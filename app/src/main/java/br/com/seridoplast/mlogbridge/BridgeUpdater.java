package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Background checks are inexact; Android schedules them when network/battery permit. */
public final class BridgeUpdater {
    static final String AUTO_CHECK="bridge_auto_update_check_v360";
    static final String AUTO_INSTALL="bridge_auto_install_idle_v359";
    private static final String RETRY_A="bridge_idle_retry_2m_a_v359";
    private static final String RETRY_B="bridge_idle_retry_2m_b_v359";
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
        // Migra o agendador periodico anterior para 1 hora.
        if(!sp.getBoolean("migrated_install_periodic_v359",false)) {
            manager.cancelUniqueWork("bridge_auto_install_idle_v350");
            sp.edit().putBoolean("migrated_install_periodic_v359",true).apply();
        }
        if(!sp.getBoolean("migrated_update_check_v360",false)) {
            manager.cancelUniqueWork("bridge_auto_update_check_v350");
            manager.cancelUniqueWork("bridge_check_soon_v350");
            sp.edit().putBoolean("migrated_update_check_v360",true).apply();
        }
        if(!sp.getBoolean(PREF_ENABLED,true)) {
            manager.cancelUniqueWork(AUTO_CHECK);
            manager.cancelUniqueWork(AUTO_INSTALL);
            manager.cancelUniqueWork(RETRY_A);
            manager.cancelUniqueWork(RETRY_B);
            return;
        }
        PeriodicWorkRequest periodic=new PeriodicWorkRequest.Builder(BridgeAutoUpdateWorker.class,1,TimeUnit.HOURS)
            .setConstraints(online()).build();
        manager.enqueueUniquePeriodicWork(AUTO_CHECK,ExistingPeriodicWorkPolicy.UPDATE,periodic);
        // Installer rechecks staged APK even when no new release is available.
        if(!sp.getBoolean(PREF_INSTALL,true)){
            manager.cancelUniqueWork(AUTO_INSTALL);
            manager.cancelUniqueWork(RETRY_A);
            manager.cancelUniqueWork(RETRY_B);
            return;
        }
        // Fallback do Android (minimo de 15 min para WorkManager periodico).
        PeriodicWorkRequest install=new PeriodicWorkRequest.Builder(BridgeIdleInstallWorker.class,15,TimeUnit.MINUTES).build();
        manager.enqueueUniquePeriodicWork(AUTO_INSTALL,ExistingPeriodicWorkPolicy.KEEP,install);
        // Retry de 2 min somente quando ha APK estavel validado e pendente.
        queueInstallRetry(ctx,null);
    }
    /** WorkManager's two-minute one-time retries are inexact and may be deferred by Doze. */
    static void queueInstallRetry(Context ctx, String justRan) {
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        if(!sp.getBoolean(PREF_ENABLED,true) || !sp.getBoolean(PREF_INSTALL,true) ||
            !sp.getBoolean(GitHubReleaseUpdater.PREF_READY,false) ||
            sp.getBoolean(GitHubReleaseUpdater.PREF_DEBUG,false) ||
            sp.getInt(GitHubReleaseUpdater.PREF_ROLLOUT,0)<=0)return;
        String slot=RETRY_A.equals(justRan)?RETRY_B:RETRY_A;
        OneTimeWorkRequest next=new OneTimeWorkRequest.Builder(BridgeIdleInstallWorker.class)
            .setInputData(new Data.Builder().putString("retry_slot",slot).build())
            .setInitialDelay(2,TimeUnit.MINUTES).build();
        WorkManager.getInstance(ctx.getApplicationContext())
            .enqueueUniqueWork(slot,ExistingWorkPolicy.KEEP,next);
    }

    public static void checkSoon(Context ctx) {
        if(!ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).getBoolean(PREF_ENABLED,true))return;
        long now=System.currentTimeMillis();
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        long last=sp.getLong("updater_last_check_requested",0L);
        if(now-last<60*60*1000L && now>=last)return;
        sp.edit().putLong("updater_last_check_requested",now).apply();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(BridgeAutoUpdateWorker.class)
            .setConstraints(online()).build();
        WorkManager.getInstance(ctx.getApplicationContext())
            .enqueueUniqueWork("bridge_check_soon_v360",ExistingWorkPolicy.KEEP,request);
    }
    public static void status(Context ctx,String message) {
        ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE)
            .edit().putString(LAST_STATUS,message).apply();
        android.util.Log.i("MLOGBridge","UPDATE "+message);
    }
}
