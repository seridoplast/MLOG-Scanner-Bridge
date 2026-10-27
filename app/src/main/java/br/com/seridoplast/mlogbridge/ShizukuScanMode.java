package br.com.seridoplast.mlogbridge;

/** Experimental, mutually exclusive native-broadcast -> privileged-keyboard mode. */
final class ShizukuScanMode {
    static final String PREF = "shizuku_scan_mode_v340";
    static final int MAX_PENDING = 8;
    private ShizukuScanMode() {}

    // Do not silently strip or modify barcode payload. Shell receives tokens as argv,
    // never interpolated into a shell command. Explicitly reject unsupported bytes.
    static boolean supported(String code) {
        if (code == null || code.isEmpty() || code.length() > 80) return false;
        for (int i = 0; i < code.length(); i++) {
            char c=code.charAt(i);
            if (!((c >= '0' && c <= '9') ||
                  (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
        }
        return true;
    }
}
