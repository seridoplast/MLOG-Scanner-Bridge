package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Install only when screen off or an unrelated foreground activity is visible and scans are idle. */
public final class BridgeIdleInstallWorker extends Worker {
    public BridgeIdleInstallWorker(@NonNull Context context,@NonNull WorkerParameters params) {super(context,params);}
    @NonNull @Override public Result doWork() {
        Context ctx=getApplicationContext();
        // Agenda a tentativa seguinte ANTES de tentar instalar (o processo pode reiniciar).
        String current=getInputData().getString("retry_slot");
        BridgeUpdater.queueInstallRetry(ctx,current);
        maybeInstall(ctx);
        return Result.success();
    }
    static void maybeInstall(Context ctx) {
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        if(!sp.getBoolean(BridgeUpdater.PREF_ENABLED,true) ||
            !sp.getBoolean(BridgeUpdater.PREF_INSTALL,true))return;
        GitHubReleaseUpdater.Update update;
        try {update=GitHubReleaseUpdater.cachedVerified(ctx);}
        catch(Exception ex) {BridgeUpdater.status(ctx,"APK em cache invalido: "+ex.getMessage());return;}
        if(update==null)return;
        if(update.debug) {
            BridgeUpdater.status(ctx,"APK de teste nao entra no canal automatico.");
            return;
        }
        int rollout=sp.getInt(GitHubReleaseUpdater.PREF_ROLLOUT,0);
        String androidId=Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if(!BridgeRolloutPolicy.eligible(androidId,rollout)) {
            BridgeUpdater.status(ctx,"Versao "+update.tag+" aguardando liberacao gradual ("+rollout+"%).");
            return;
        }
        if(!ShizukuApkInstaller.ready()) {
            BridgeUpdater.status(ctx,"Atualizacao pronta; aguardando Shizuku ativo/autorizado.");
            return;
        }
        // Avoid any hot update while a barcode is currently being injected.
        long lastScan=sp.getLong("bridge_last_shizuku_scan_wall",0L);
        long now=System.currentTimeMillis();
        if(lastScan>0 && (now<lastScan || now-lastScan<2*60*1000L))return;
        PowerManager pm=(PowerManager)ctx.getSystemService(Context.POWER_SERVICE);
        boolean interactive=pm!=null && pm.isInteractive();
        if(interactive) {
            // Without an active accessibility service we cannot prove MLOG is out of focus.
            MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
            if(svc==null)return;
            AccessibilityNodeInfo root=svc.getRootInActiveWindow();
            if(root==null || root.getPackageName()==null ||
                ScanCoordinator.TARGET.contentEquals(root.getPackageName()))return;
            // System overlays can temporarily take focus while MLOG still separates.
            String foreground=root.getPackageName().toString();
            if(foreground.equals("com.android.systemui") || foreground.contains("inputmethod"))return;
        }
        try {
            ShizukuApkInstaller.installVerified(ctx,update);
        } catch(Exception ex) {
            BridgeUpdater.status(ctx,"Instalacao adiada/falhou: "+ex.getMessage());
        }
    }
}
