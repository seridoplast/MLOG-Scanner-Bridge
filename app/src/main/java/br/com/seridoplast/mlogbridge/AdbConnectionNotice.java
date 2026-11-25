package br.com.seridoplast.mlogbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

/** One notice per sustained loss of an established ADB session. Never requests pairing. */
final class AdbConnectionNotice {
    private static final String CHANNEL="bridge_adb_connection_v3624";
    private static final int ID=36240;
    private AdbConnectionNotice(){}
    static void clear(Context c){try{NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm!=null)nm.cancel(ID);}catch(RuntimeException ignored){}}
    static void update(Context c,AdbConnectionHealth health,boolean connected){
        if(connected){clear(c);return;}
        if(!health.shouldAlert(SystemClock.elapsedRealtime()))return;
        try{
            NotificationManager nm=c.getSystemService(NotificationManager.class);
            if(nm==null || !nm.areNotificationsEnabled())return;
            if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Conexão ADB",NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open=PendingIntent.getActivity(c,ID,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            String text="Conexão local interrompida. O Bridge está tentando recuperar o ADB automaticamente.";
            nm.notify(ID,new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ui_terminal).setContentTitle("MLOG Bridge · ADB desconectado")
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true).build());
            health.alerted();
        }catch(RuntimeException ignored){}
    }
}
