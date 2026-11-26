package br.com.seridoplast.mlogbridge;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Boot and periodic safety net. Accessibility performs the faster 5s polling. */
public final class AdbRecoveryWorker extends Worker {
    public AdbRecoveryWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        if(LocalAdbBridge.enabled(c)){
            // Keep the worker alive while the asynchronous connection is being established.
            // In a live accessibility process its normal watchdog takes over afterwards.
            LocalAdbBridge.wakeIfUnavailable(c);
            long deadline=android.os.SystemClock.elapsedRealtime()+75_000L;
            do{
                if(isStopped() || Thread.currentThread().isInterrupted())break;
                LocalAdbBridge.tick(c);
                if(LocalAdbBridge.ready() && !LocalAdbBridge.checking())break;
                try{Thread.sleep(1000L);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            }while(android.os.SystemClock.elapsedRealtime()<deadline);
        }
        // The network may not be available when BOOT_COMPLETED is delivered.
        // Retry boundedly instead of waiting until the next 15-minute periodic run.
        if(!isStopped() && LocalAdbBridge.enabled(c) && !LocalAdbBridge.ready()
            && getRunAttemptCount()<4)return Result.retry();
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
            new OneTimeWorkRequest.Builder(AdbRecoveryWorker.class).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS).build());
    }
}
