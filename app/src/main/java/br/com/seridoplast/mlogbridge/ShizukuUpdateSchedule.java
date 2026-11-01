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

/** A daily check; optional idle installation retry follows Android's 15-minute minimum. */
final class ShizukuUpdateSchedule {
    private static final String CHECK="shizuku_update_daily_v364";
    private static final String CHECK_NOW="shizuku_update_once_v364";
    private static final String INSTALL="shizuku_install_idle_v364";
    private ShizukuUpdateSchedule(){}
    static void schedule(Context ctx){
        Context app=ctx.getApplicationContext();
        WorkManager wm=WorkManager.getInstance(app);
        SharedPreferences sp=app.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        if(!sp.getBoolean(ShizukuReleaseUpdater.PREF_CHECK,false)){
            wm.cancelUniqueWork(CHECK);
            wm.cancelUniqueWork(CHECK_NOW);
            wm.cancelUniqueWork(INSTALL);
            return;
        }
        Constraints internet=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest daily=new PeriodicWorkRequest.Builder(ShizukuUpdateWorker.class,24,TimeUnit.HOURS)
            .setConstraints(internet).build();
        wm.enqueueUniquePeriodicWork(CHECK,ExistingPeriodicWorkPolicy.KEEP,daily);
        if(sp.getBoolean(ShizukuReleaseUpdater.PREF_INSTALL,false)){
            PeriodicWorkRequest install=new PeriodicWorkRequest.Builder(ShizukuIdleInstallWorker.class,15,TimeUnit.MINUTES).build();
            wm.enqueueUniquePeriodicWork(INSTALL,ExistingPeriodicWorkPolicy.KEEP,install);
        } else wm.cancelUniqueWork(INSTALL);
    }
    static void checkSoon(Context ctx){
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        if(!sp.getBoolean(ShizukuReleaseUpdater.PREF_CHECK,false))return;
        WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniqueWork(CHECK_NOW,ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(ShizukuUpdateWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }
}
