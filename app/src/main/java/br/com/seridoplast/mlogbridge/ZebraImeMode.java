package br.com.seridoplast.mlogbridge;

/** Saida experimental tipo Zebra DataWedge: broadcast do decodificador -> IME Android. */
final class ZebraImeMode {
    static final String PREF = "zebra_ime_mode_v326";
    static final int MAX_WAITING = 48;
    static final long DUPLICATE_ECHO_MS = 30L; // nao confundir eco com bipes legitimos
    static final long EDITOR_RETRY_MS = 35L;
    static final long MAX_EDITOR_WAIT_MS = 750L;
    private ZebraImeMode() {}
    static boolean isValidCode(String code, String symbology) {
        return BarcodeIntegrityPolicy.isValid(code, symbology);
    }
    static boolean isEcho(String code, String previous, long sinceMs) {
        return code != null && code.equals(previous) && sinceMs >= 0 && sinceMs < DUPLICATE_ECHO_MS;
    }
    static long interval(int configured) { return Math.max(0L, configured); }
}
