package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** HTTPS + hash + signing certificate validation; no installation while downloading. */
public final class BridgeAutoUpdateWorker extends Worker {
    public BridgeAutoUpdateWorker(@NonNull Context context,@NonNull WorkerParameters params) {super(context,params);}
    @NonNull @Override public Result doWork() {
        Context ctx=getApplicationContext();
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        if(!sp.getBoolean(BridgeUpdater.PREF_ENABLED,true))return Result.success();
        try {
            GitHubReleaseUpdater.Update update=GitHubReleaseUpdater.downloadLatest(ctx);
            if(update==null){BridgeUpdater.status(ctx,"Nenhuma nova versao aprovada.");return Result.success();}
            if(update.debug){BridgeUpdater.status(ctx,"DEBUG ignorada pelo atualizador automatico.");return Result.success();}
            BridgeUpdater.status(ctx,"APK "+update.tag+" verificado e pronto (rollout "+
                sp.getInt(GitHubReleaseUpdater.PREF_ROLLOUT,0)+"%).");
            BridgeIdleInstallWorker.maybeInstall(ctx);
            return Result.success();
        } catch(Exception ex) {
            BridgeUpdater.status(ctx,"Falha ao consultar atualizacao: "+ex.getMessage());
            return Result.success(); // Aguarda proximo periodo, evita flood na API.
        }
    }
}
