package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Additional high-risk opt-in. Never runs without verified APK and idle collector. */
public final class ShizukuIdleInstallWorker extends Worker {
    public ShizukuIdleInstallWorker(@NonNull Context ctx,@NonNull WorkerParameters params){super(ctx,params);}
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        if(!sp.getBoolean(ShizukuReleaseUpdater.PREF_CHECK,false) ||
           !sp.getBoolean(ShizukuReleaseUpdater.PREF_INSTALL,false))return Result.success();
        if(!ShizukuApkInstaller.ready() || !BridgeIdleInstallWorker.isCollectorIdle(ctx))return Result.success();
        try {
            File apk=ShizukuReleaseUpdater.cachedVerified(ctx);
            if(apk!=null)ShizukuApkInstaller.installVerifiedShizuku(ctx,apk);
        }catch(Exception ex){
            ShizukuReleaseUpdater.status(ctx,
                ShizukuReleaseUpdater.stagedVersionInstalled(ctx)?
                    "Shizuku instalado. Confira se o serviço está ativo.":
                    "Instalação do Shizuku pendente: "+ex.getMessage());
        }
        return Result.success();
    }
}
