package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Daily, explicitly opted-in Shizuku manager update discovery and verified download. */
public final class ShizukuUpdateWorker extends Worker {
    public ShizukuUpdateWorker(@NonNull Context ctx,@NonNull WorkerParameters params){super(ctx,params);}
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        if(!sp.getBoolean(ShizukuReleaseUpdater.PREF_CHECK,false))return Result.success();
        try { ShizukuReleaseUpdater.fetch(ctx);return Result.success(); }
        catch(Exception ex){ShizukuReleaseUpdater.status(ctx,"Falha na consulta do Shizuku: "+ex.getMessage());return Result.retry();}
    }
}
