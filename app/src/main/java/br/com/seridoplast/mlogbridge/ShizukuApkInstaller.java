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
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

/**
 * Installs only a locally revalidated corporate APK using Shizuku's shell identity.
 * A PackageManager session separates transfer from commit, making errors observable.
 * If the OEM closes the stdin pipe, the UI can offer the standard Android installer.
 */
final class ShizukuApkInstaller {
    private static final AtomicBoolean INSTALLING = new AtomicBoolean(false);
    private static final String PM = "/system/bin/pm";
    private ShizukuApkInstaller() { }

    static boolean ready() {
        try {
            return Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                && (Shizuku.getUid() == 2000 || Shizuku.getUid() == 0);
        } catch (RuntimeException ex) { return false; }
    }

    interface Callback { void done(boolean ok, String message); }

    static void installAsync(Context context, Callback callback) {
        installAsync(context,false,callback);
    }

    static void installAsync(Context context, boolean manualPrereleaseAuthorized, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean success = false;
            String status;
            try {
                GitHubReleaseUpdater.Update update = GitHubReleaseUpdater.cachedVerified(app);
                if (update == null) throw new IllegalStateException("Nenhuma atualizacao verificada foi baixada.");
                if(update.debug && (!manualPrereleaseAuthorized || !DebugUpdateAccess.allowed()))
                    throw new SecurityException("Pre-release exige senha.");
                status = installVerified(app, update, manualPrereleaseAuthorized);
                success = true;
            } catch (Exception ex) {
                status = "Falha na instalacao Shizuku: " + safeMessage(ex);
                BridgeUpdater.status(app, status);
                Log.e("MLOGBridge", "UPDATE_SHIZUKU_FAILURE", ex);
            }
            final boolean ok = success;
            final String message = status;
            if (callback != null) new Handler(Looper.getMainLooper()).post(() -> callback.done(ok, message));
        }, "bridge-shizuku-install").start();
    }

    /** Automatic caller must first verify rollout, app idleness and Shizuku readiness. */
    static String installVerified(Context context, GitHubReleaseUpdater.Update update) throws Exception {
        return installVerified(context,update,false);
    }

    private static String installVerified(Context context, GitHubReleaseUpdater.Update update,
                                          boolean manualPrereleaseAuthorized) throws Exception {
        if (!INSTALLING.compareAndSet(false, true)) throw new IllegalStateException("Outra instalacao em andamento.");
        String session = null;
        boolean committed = false;
        try {
            if (!ready()) throw new IllegalStateException("Shizuku desligado ou nao autorizado.");
            // This also validates the package, versionName/versionCode and pinned certificate.
            GitHubReleaseUpdater.Update checked = GitHubReleaseUpdater.cachedVerified(context);
            if (checked == null || !checked.tag.equals(update.tag))
                throw new IllegalStateException("APK nao passou na verificacao de assinatura/versao.");
            if(checked.debug && (!manualPrereleaseAuthorized || !DebugUpdateAccess.allowed()))
                throw new SecurityException("Pre-release sem autorizacao manual por senha.");
            File apk = checked.apk;
            long size = apk.length();
            if (size < 50L * 1024L || size > 120L * 1024L * 1024L)
                throw new IllegalStateException("Tamanho de APK inesperado.");

            BridgeUpdater.status(context, "Criando sessao de instalacao " + update.tag + "...");
            ProcessResult created = exec(30, PM, "install-create", "-r", "-S", Long.toString(size));
            if (!created.ok()) throw new IOException("pm install-create: " + created.description());
            session = PmInstallOutput.createdSession(created.stdout);
            if (session == null) throw new IOException("Sessao invalida: " + created.description());

            BridgeUpdater.status(context, "Transferindo APK " + update.tag + " ao Android...");
            // Explicit session ID avoids the single-shot pm install command that closed stdin early.
            ProcessResult written = streamApk(apk, size, session);
            if (!written.ok()) throw new IOException("pm install-write: " + written.description());

            BridgeUpdater.status(context, "Concluindo instalacao " + update.tag + "...");
            ProcessResult result = exec(120, PM, "install-commit", session);
            if (!result.ok()) throw new IOException("pm install-commit: " + result.description());
            committed = true;
            BridgeUpdater.status(context, "Instalacao " + update.tag + " concluida.");
            return "Atualizacao " + update.tag + " instalada.";
        } finally {
            // No abandoned sessions if the transfer or package validation fails.
            if (session != null && !committed) {
                try { exec(10, PM, "install-abandon", session); }
                catch (Exception ex) { Log.w("MLOGBridge", "UPDATE_ABANDON_FAILED", ex); }
            }
            INSTALLING.set(false);
        }
    }

    private static ProcessResult streamApk(File apk, long length, String session) throws Exception {
        ShizukuRemoteProcess p = start(PM, "install-write", "-S", Long.toString(length), session, "base.apk", "-");
        IOException pipeFailure = null;
        long transferred = 0L;
        try {
            try (InputStream in = new FileInputStream(apk); OutputStream out = p.getOutputStream()) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    try { out.write(buffer, 0, n); }
                    catch (IOException ex) { pipeFailure = ex; break; }
                    transferred += n;
                }
                if (pipeFailure == null) {
                    try { out.flush(); }
                    catch (IOException ex) { pipeFailure = ex; }
                }
            } catch (IOException ex) {
                if (pipeFailure == null) pipeFailure = ex;
            }
            if (pipeFailure != null) {
                boolean exited = p.waitForTimeout(10, TimeUnit.SECONDS);
                if (!exited) {
                    p.destroy();
                    exited = p.waitForTimeout(3, TimeUnit.SECONDS);
                }
                ProcessResult response = exited ? collectAfterExit(p, true)
                    : new ProcessResult(-1, "", "Instalador nao encerrou apos falha do canal");
                throw new IOException("Canal de transferencia encerrado apos " + transferred + "/" + length
                    + " bytes (EPIPE/pipe). Resposta Android: " + response.description(), pipeFailure);
            }
            if (transferred != length) throw new IOException("Quantidade transferida diferente do APK esperado.");
            if (!p.waitForTimeout(90, TimeUnit.SECONDS)) {
                p.destroy();
                throw new IOException("Timeout durante pm install-write.");
            }
            return collectAfterExit(p, true);
        } finally { closeStreams(p); }
    }

    private static ProcessResult exec(int timeoutSeconds, String... command) throws Exception {
        ShizukuRemoteProcess p = start(command);
        try {
            // A command without streamed input must see EOF immediately.
            try { p.getOutputStream().close(); } catch (IOException ignored) { }
            if (!p.waitForTimeout(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroy();
                throw new IOException("Tempo limite no comando do instalador.");
            }
            return collectAfterExit(p, true);
        } finally { closeStreams(p); }
    }

    private static ShizukuRemoteProcess start(String... args) throws Exception {
        if (!ready()) throw new IllegalStateException("Shizuku nao esta ativo/autorizado.");
        Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        method.setAccessible(true);
        Object response;
        try { response = method.invoke(null, (Object) args, null, null); }
        catch (InvocationTargetException ex) { throw new IOException("Shizuku nao iniciou o comando.", ex.getCause()); }
        if (!(response instanceof ShizukuRemoteProcess))
            throw new IOException("Processo remoto indisponivel no Shizuku.");
        return (ShizukuRemoteProcess) response;
    }

    private static ProcessResult collectAfterExit(ShizukuRemoteProcess process, boolean exited) {
        String stdout = readShortQuietly(process.getInputStream());
        String stderr = readShortQuietly(process.getErrorStream());
        int exit = -1;
        if (exited) {
            try { exit = process.exitValue(); } catch (RuntimeException ignored) { }
        }
        if (exit != 0 || !PmInstallOutput.success(stdout))
            Log.e("MLOGBridge", "UPDATE_PM exit=" + exit + " stdout=" + stdout + " stderr=" + stderr);
        return new ProcessResult(exit, stdout, stderr);
    }

    private static final class ProcessResult {
        final int exit;
        final String stdout;
        final String stderr;
        ProcessResult(int exit, String stdout, String stderr) {
            this.exit=exit; this.stdout=stdout; this.stderr=stderr;
        }
        boolean ok() { return exit == 0 && PmInstallOutput.success(stdout); }
        String description() {
            return "exit=" + exit + " " + PmInstallOutput.brief(stderr.isEmpty() ? stdout : stderr);
        }
    }

    private static String readShortQuietly(InputStream stream) {
        if (stream == null) return "";
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                if (bytes.size() < 4096) bytes.write(buffer, 0, Math.min(n, 4096 - bytes.size()));
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        } catch (IOException ex) { return "Falha ao ler retorno: " + safeMessage(ex); }
    }

    private static void closeStreams(ShizukuRemoteProcess p) {
        if (p == null) return;
        try { p.getOutputStream().close(); } catch (Exception ignored) { }
        try { p.getInputStream().close(); } catch (Exception ignored) { }
        try { p.getErrorStream().close(); } catch (Exception ignored) { }
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isEmpty()) message = ex.getClass().getSimpleName();
        return PmInstallOutput.brief(message);
    }
}
