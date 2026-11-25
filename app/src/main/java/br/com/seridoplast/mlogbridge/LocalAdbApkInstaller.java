package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Installs only a locally revalidated corporate APK using Adb's shell identity.
 * A PackageManager session separates transfer from commit, making errors observable.
 * If the OEM closes the stdin pipe, the UI can offer the standard Android installer.
 */
final class LocalAdbApkInstaller {
    private static final AtomicBoolean INSTALLING = new AtomicBoolean(false);
    private static final String PM = "/system/bin/pm";
    private LocalAdbApkInstaller() { }

    static boolean ready(){return LocalAdbBridge.ready();}

    interface Callback { void done(boolean ok, String message); }

    static void installAsync(Context context, Callback callback) {
        installAsync(context,false,callback);
    }

    /** Manual-only installation of password-authorized prereleases. */
    static void installAsync(Context context, boolean allowPrerelease, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean success = false;
            String status;
            try {
                GitHubReleaseUpdater.Update update = GitHubReleaseUpdater.cachedVerified(app);
                if (update == null) throw new IllegalStateException("Nenhuma atualizacao verificada foi baixada.");
                if(update.debug && (!allowPrerelease || !DebugUpdateAccess.allowed()))
                    throw new SecurityException("Senha necessaria para instalar pre-release.");
                status = installVerified(app, update, allowPrerelease);
                success = true;
            } catch (Exception ex) {
                status = "Falha na instalacao Adb: " + safeMessage(ex);
                BridgeUpdater.status(app, status);
                Log.e("MLOGBridge", "UPDATE_ADB_FAILURE", ex);
            }
            final boolean ok = success;
            final String message = status;
            if (callback != null) new Handler(Looper.getMainLooper()).post(() -> callback.done(ok, message));
        }, "bridge-adb-install").start();
    }

    /** Automatic caller must first verify rollout, app idleness and Adb readiness. */
    static String installVerified(Context context, GitHubReleaseUpdater.Update update) throws Exception {
        return installVerified(context,update,false);
    }

    private static String installVerified(Context context, GitHubReleaseUpdater.Update update,
                                          boolean allowPrerelease) throws Exception {
        if (!INSTALLING.compareAndSet(false, true)) throw new IllegalStateException("Outra instalacao em andamento.");
        try {
            if (!ready()) throw new IllegalStateException("ADB desligado ou nao autorizado.");
            // This also validates the package, versionName/versionCode and pinned certificate.
            GitHubReleaseUpdater.Update checked = GitHubReleaseUpdater.cachedVerified(context);
            if (checked == null || !checked.tag.equals(update.tag))
                throw new IllegalStateException("APK nao passou na verificacao de assinatura/versao.");
            if(checked.debug && (!allowPrerelease || !DebugUpdateAccess.allowed()))
                throw new SecurityException("Pre-release sem autorizacao manual por senha.");
            File apk = checked.apk;
            long size = apk.length();
            if (size < 50L * 1024L || size > 120L * 1024L * 1024L)
                throw new IllegalStateException("Tamanho de APK inesperado.");

            BridgeUpdater.status(context,"Instalando "+update.tag+" via ADB local...");
            if(!LocalAdbBridge.installBridge(apk))throw new IOException("ADB sem confirmação da instalação; confira a versão instalada.");
            BridgeUpdater.status(context,"Instalação ADB confirmada: "+update.tag);
            return "Atualização instalada: "+update.tag;
        } finally {INSTALLING.set(false);}
    }

    private static String safeMessage(Throwable ex){
        String message=ex.getMessage();return PmInstallOutput.brief(message==null?ex.getClass().getSimpleName():message);
    }
}
