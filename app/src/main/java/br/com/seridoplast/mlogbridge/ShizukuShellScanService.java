package br.com.seridoplast.mlogbridge;

import android.os.Process;
import android.util.Log;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService. Runs as the adb shell UID (2000), not the app UID. */
public final class ShizukuShellScanService extends IShizukuScanService.Stub {
    public ShizukuShellScanService() {}

    @Override public synchronized boolean injectScan(String code) {
        // An app-level callback must never be able to execute arbitrary shell commands.
        if (!ShizukuScanMode.supported(code) || Process.myUid() != 2000) {
            Log.w("MLOGBridge", "SHIZUKU_REJECTED_PAYLOAD_OR_UID");
            return false;
        }
        // Two commands, matching the tested adb shell input text + keyevent 66.
        // Using argv avoids sh -c and interpolation of untrusted barcode data.
        if (!run("/system/bin/input", "text", code)) return false;
        return run("/system/bin/input", "keyevent", "66");
    }

    private static boolean run(String... command) {
        java.lang.Process p=null;
        try {
            p=new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!p.waitFor(2500,TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                Log.w("MLOGBridge","SHIZUKU_INPUT_TIMEOUT");
                return false;
            }
            boolean ok=p.exitValue()==0;
            if (!ok)Log.w("MLOGBridge","SHIZUKU_INPUT_NONZERO");
            return ok;
        } catch (Exception ex) {
            if (ex instanceof InterruptedException)Thread.currentThread().interrupt();
            Log.w("MLOGBridge","SHIZUKU_INPUT_EXCEPTION",ex);
            return false;
        } finally {
            if (p!=null) {try {p.getInputStream().close();} catch(Exception ignored) {}}
        }
    }
}
