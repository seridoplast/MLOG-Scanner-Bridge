package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import rikka.shizuku.Shizuku;

/** Silent monitor: UI status only, never posts notification or steals MLOG focus. */
final class ShizukuHealthMonitor {
    private static final String SHIZUKU_PACKAGE="moe.shizuku.privileged.api";
    private static boolean clearedOldNotice;
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
    static synchronized void refresh(Context context){
        if(clearedOldNotice)return;
        clearedOldNotice=true;
        // Cancel any stale alert created by the previous v3.4.3 installation.
        try{
            android.app.NotificationManager manager=(android.app.NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
            if(manager!=null)manager.cancel(34301);
        }catch(RuntimeException ignored){}
    }
}
