package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * Canal temporario entre scanner, acessibilidade e metodo de entrada (mesmo processo).
 * Nenhum codigo vai para armazenamento, logs, rede ou broadcasts novos.
 */
final class ScanCoordinator {
    static final String TARGET = "com.targetsistemas.target_mlog2";
    static final String PREFS = "mlog_bridge_v3";
    private static int lastHash, lastLength;
    private static long lastAt;
    private static String waitingCode;
    private static long waitingUntil;
    private static long expectUntil;
    private static boolean armed;

    private ScanCoordinator() {}

    static void status(Context c, String s) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("last_status",s).apply();
    }

    static boolean isActive(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false);
    }

    private static boolean safeBarcode(String c) {
        if(c == null || c.length() < 1 || c.length() > 128) return false;
        for(int x=0;x<c.length();x++) {
            char ch = c.charAt(x);
            if(ch < 32 || ch > 126) return false;
        }
        return true;
    }

    static synchronized void onScan(Context context, String c) {
        if(!isActive(context)) return;
        if(!safeBarcode(c)) {
            status(context,"Leitura recusada: formato de dados invalido.");
            return;
        }
        long now=SystemClock.elapsedRealtime();
        int h=c.hashCode();
        if(h==lastHash && c.length()==lastLength && now-lastAt < 380) return;
        lastHash=h;lastLength=c.length();
        lastAt = now;
        MlogFocusAccessibilityService a = MlogFocusAccessibilityService.current();
        if(a != null) {
            a.handleBarcode(c);
            return;
        }
        // Modo de compatibilidade: somente com editor do MLOG ja focado.
        ScannerInputMethodService i = ScannerInputMethodService.current();
        boolean compatible=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getBoolean("compat_fallback",false);
        if(!compatible || i == null || !i.tryInsert(c, false))
            status(context,"Acessibilidade inativa ou campo de codigo nao selecionado.");
    }

    static synchronized void expectCodeEditor() {
        expectUntil=SystemClock.elapsedRealtime()+2500;
        armed=false;
    }
    static synchronized void cancelExpect() { expectUntil=0; armed=false; waitingCode=null; waitingUntil=0; }
    static synchronized void queueAfterClick(String c) {
        waitingCode=c;
        waitingUntil=SystemClock.elapsedRealtime()+1900;
    }
    static synchronized void expirePending() {
        if(SystemClock.elapsedRealtime()>=waitingUntil)waitingCode=null;
    }
    static synchronized void onInputStarted(ScannerInputMethodService ime) {
        long now = SystemClock.elapsedRealtime();
        armed = now < expectUntil && ime.hasSafeMlogEditor(true);
        expectUntil = 0;
        if(!armed) { waitingCode=null; waitingUntil=0; return; }
        if(waitingCode != null && now < waitingUntil) {
            String code=waitingCode;
            waitingCode=null; waitingUntil=0;
            ime.tryInsert(code,true);
        } else { waitingCode=null; waitingUntil=0; }
    }
    static synchronized void onInputFinished() {
        armed=false;
        expectUntil=0;
        waitingCode=null;
        waitingUntil=0;
    }
    static synchronized boolean isArmed() { return armed; }

    static synchronized boolean insertIfArmed(String code) {
        ScannerInputMethodService ime=ScannerInputMethodService.current();
        return armed && ime!=null && ime.tryInsert(code,true);
    }

    static synchronized boolean hasAnyMlogEditor() {
        ScannerInputMethodService ime=ScannerInputMethodService.current();
        return ime!=null && ime.hasAnyMlogEditor();
    }

    static synchronized void flushIfEditorOpened() {
        ScannerInputMethodService ime=ScannerInputMethodService.current();
        if(ime!=null && ime.hasSafeMlogEditor(true) &&
                SystemClock.elapsedRealtime()<expectUntil)onInputStarted(ime);
    }

    static synchronized boolean pending() {
        return waitingCode!=null && SystemClock.elapsedRealtime()<waitingUntil;
    }
}
