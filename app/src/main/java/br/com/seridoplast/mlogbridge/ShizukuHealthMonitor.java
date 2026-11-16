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
 * Health notification only for the selected Shizuku scan mode.
 * Never starts Shizuku in the background or steals focus from MLOG.
 * Tap the notification to open Shizuku for user recovery.
 */
final class ShizukuHealthMonitor {
    // Keep the original v3.4.3 channel and notification ID to respect device preferences.
    private static final String CHANNEL="shizuku_scanner_status_v343";
    private static final int NOTIFICATION_ID=34301;
    private static final String SHIZUKU_PACKAGE="moe.shizuku.privileged.api";
    private static boolean warningPosted;

    private ShizukuHealthMonitor(){}

    static boolean ready(){
        try{return Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED &&
            (Shizuku.getUid()==2000 || Shizuku.getUid()==0);
        }catch(RuntimeException | LinkageError ex){return false;}
    }

    static boolean openShizuku(Context context){
        try{
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if(launch==null)return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);return true;
        }catch(RuntimeException ex){Log.w("MLOGBridge","Could not open Shizuku",ex);return false;}
    }

    /** Invoked by the active accessibility watchdog, Shizuku binder and app screen. */
    static synchronized void refresh(Context context){
        boolean shizukuSelected=context.getSharedPreferences(ScanCoordinator.PREFS,
            Context.MODE_PRIVATE).getBoolean(ShizukuScanMode.PREF,false);
        if(!shizukuSelected || ready()){
            cancel(context);
            warningPosted=false;
            return;
        }
        if(!warningPosted)warningPosted=notifyUnavailable(context);
    }

    private static NotificationManager manager(Context context){
        return (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static void cancel(Context context){
        try{NotificationManager nm=manager(context);if(nm!=null)nm.cancel(NOTIFICATION_ID);}
        catch(RuntimeException ex){Log.w("MLOGBridge","Could not cancel Shizuku notice",ex);}
    }

    private static boolean notifyUnavailable(Context context){
        try{
            NotificationManager nm=manager(context);
            if(nm==null)return false;
            // Android 13+ requires a runtime notification permission.
            if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(
                "android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return false;
            if(!nm.areNotificationsEnabled())return false;
            if(Build.VERSION.SDK_INT>=26){
                NotificationChannel channel=new NotificationChannel(CHANNEL,
                    "Scanner: estado do Shizuku",NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Avisa quando Shizuku nao esta pronto para ler no MLOG");
                nm.createNotificationChannel(channel);
                if(nm.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)
                    return false;
            }
            Intent open=context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if(open==null)open=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if(open==null)return false;
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent action=PendingIntent.getActivity(context,NOTIFICATION_ID,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder=Build.VERSION.SDK_INT>=26?
                new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("MLOG: Shizuku desligado")
                .setContentText("Leituras pausadas. Toque para abrir o Shizuku e iniciar.")
                .setContentIntent(action)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .addAction(android.R.drawable.ic_menu_manage,"Abrir Shizuku",action);
            nm.notify(NOTIFICATION_ID,builder.build());
            return true;
        }catch(RuntimeException ex){
            Log.w("MLOGBridge","Shizuku notification failed",ex);
            return false;
        }
    }
}
