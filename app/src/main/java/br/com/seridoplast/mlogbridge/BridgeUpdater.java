package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Background checks are inexact; Android schedules them when network/battery permit. */
public final class BridgeUpdater {
    static final String AUTO_CHECK="bridge_auto_update_check_v350";
    static final String AUTO_INSTALL="bridge_auto_install_idle_v350";
    static final String LAST_STATUS="updater_status";
    static final String PREF_ENABLED="updater_enabled_v350";
    static final String PREF_INSTALL="updater_install_idle_v350";
    private BridgeUpdater() { }

    private static Constraints online() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }
    public static void schedule(Context ctx) {
        WorkManager manager=WorkManager.getInstance(ctx.getApplicationContext());
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        if(!sp.getBoolean(PREF_ENABLED,true)) {
            manager.cancelUniqueWork(AUTO_CHECK);
            manager.cancelUniqueWork(AUTO_INSTALL);
            return;
        }
        PeriodicWorkRequest periodic=new PeriodicWorkRequest.Builder(BridgeAutoUpdateWorker.class,2,TimeUnit.HOURS)
            .setConstraints(online()).build();
        manager.enqueueUniquePeriodicWork(AUTO_CHECK,ExistingPeriodicWorkPolicy.KEEP,periodic);
        // Installer rechecks staged APK even when no new release is available.
        PeriodicWorkRequest install=new PeriodicWorkRequest.Builder(BridgeIdleInstallWorker.class,30,TimeUnit.MINUTES).build();
        manager.enqueueUniquePeriodicWork(AUTO_INSTALL,ExistingPeriodicWorkPolicy.KEEP,install);
    }
    public static void checkSoon(Context ctx) {
        if(!ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).getBoolean(PREF_ENABLED,true))return;
        long now=System.currentTimeMillis();
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        long last=sp.getLong("updater_last_check_requested",0L);
        if(now-last<30*60*1000L && now>=last)return;
        sp.edit().putLong("updater_last_check_requested",now).apply();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(BridgeAutoUpdateWorker.class)
            .setConstraints(online()).build();
        WorkManager.getInstance(ctx.getApplicationContext())
            .enqueueUniqueWork("bridge_check_soon_v350",ExistingWorkPolicy.KEEP,request);
    }
    public static void status(Context ctx,String message) {
        ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE)
            .edit().putString(LAST_STATUS,message).apply();
        android.util.Log.i("MLOGBridge","UPDATE "+message);
    }
}
