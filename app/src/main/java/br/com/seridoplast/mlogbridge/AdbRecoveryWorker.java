package br.com.seridoplast.mlogbridge;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Boot and periodic safety net. Accessibility performs the faster 30s polling. */
public final class AdbRecoveryWorker extends Worker {
    public AdbRecoveryWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        if(LocalAdbBridge.enabled(c)){
            AdbWifiRecovery.recoverIfNeeded(c);
            LocalAdbBridge.tick(c);
        }
        return Result.success();
    }
    static void schedule(Context c,boolean afterBoot){
        WorkManager wm=WorkManager.getInstance(c);
        // Cancel work scheduled by old versions; no third-party updater remains.
        wm.cancelUniqueWork("shizuku_update_daily_v364");
        wm.cancelUniqueWork("shizuku_update_once_v364");
        wm.cancelUniqueWork("shizuku_install_idle_v364");
        wm.enqueueUniquePeriodicWork("adb_recovery_v3621",ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(AdbRecoveryWorker.class,15,TimeUnit.MINUTES).build());
        if(afterBoot)wm.enqueueUniqueWork("adb_recovery_boot_v3621",ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(AdbRecoveryWorker.class).setInitialDelay(20,TimeUnit.SECONDS).build());
    }
}
