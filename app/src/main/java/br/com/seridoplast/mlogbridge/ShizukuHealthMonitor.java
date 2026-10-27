package br.com.seridoplast.mlogbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import rikka.shizuku.Shizuku;

/**
 * v3.4.3: non-invasive health monitor. Never attempts to grant Shizuku permissions,
 * press buttons in another app, or inject a fallback scanner event.
 */
final class ShizukuHealthMonitor {
    private static final String CHANNEL="shizuku_scanner_status_v343";
    private static final int NOTIFICATION_ID=34301;
    private static final String SHIZUKU_PACKAGE="moe.shizuku.privileged.api";
    private static Boolean wasReady;
    private ShizukuHealthMonitor() {}

    static boolean ready() {
        try {
            return Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED &&
                (Shizuku.getUid()==2000 || Shizuku.getUid()==0);
        } catch(RuntimeException | LinkageError ignored) { return false; }
    }

    static boolean openShizuku(Context context) {
        try {
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if(launch==null)return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            return true;
        }catch(RuntimeException ex){
            Log.w("MLOGBridge","Could not open Shizuku",ex);
            return false;
        }
    }

    static void refresh(Context context) {
        boolean chosen=context.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE)
            .getBoolean(ShizukuScanMode.PREF,false);
        if(!chosen){cancel(context);wasReady=null;return;}
        boolean ok=ready();
        if(!ok && Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(
            "android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){
            wasReady=null;return;
        }
        // Check often, but only change notifications on actual transitions.
        if(wasReady!=null && wasReady.booleanValue()==ok)return;
        wasReady=ok;
        if(ok)cancel(context);
        else notifyUnavailable(context);
    }

    private static NotificationManager manager(Context context){
        return (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
    private static void cancel(Context context){
        try{NotificationManager nm=manager(context);if(nm!=null)nm.cancel(NOTIFICATION_ID);}
        catch(RuntimeException ignored){}
    }
    private static void notifyUnavailable(Context context){
        try {
            NotificationManager nm=manager(context);
            if(nm==null)return;
            if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(
                "android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
            if(Build.VERSION.SDK_INT>=26){
                NotificationChannel c=new NotificationChannel(CHANNEL,"Scanner: estado do Shizuku",
                    NotificationManager.IMPORTANCE_DEFAULT);
                c.setDescription("Avisa quando o serviço necessário para as leituras está desligado");
                nm.createNotificationChannel(c);
            }
            Intent open=context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if(open==null)open=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if(open==null)return;
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent action=PendingIntent.getActivity(context,NOTIFICATION_ID,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder=Build.VERSION.SDK_INT>=26?
                new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("MLOG: Shizuku desligado")
                .setContentText("Leituras pausadas. Toque para abrir o Shizuku e iniciar.")
                .setContentIntent(action).setAutoCancel(false)
                .setOngoing(false).setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_manage,"Abrir Shizuku",action);
            nm.notify(NOTIFICATION_ID,builder.build());
        }catch(RuntimeException ex){Log.w("MLOGBridge","Shizuku notification failed",ex);}
    }
}
