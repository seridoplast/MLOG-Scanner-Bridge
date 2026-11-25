package br.com.seridoplast.mlogbridge;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
/** Tombstone for persisted work from previous versions. Never contacts GitHub. */
public final class BridgeAutoUpdateWorker extends Worker {
    public BridgeAutoUpdateWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){return Result.success();}
}
