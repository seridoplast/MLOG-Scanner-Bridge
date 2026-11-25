package br.com.seridoplast.mlogbridge;

/** Experimental, mutually exclusive native-broadcast -> privileged-keyboard mode. */
final class AdbScanMode {
    static final String PREF = "adb_scan_mode_v340";
    static final int MAX_PENDING = 8;
    private AdbScanMode() {}

    // Do not silently strip or modify barcode payload. Shell receives tokens as argv,
    // never interpolated into a shell command. Explicitly reject unsupported bytes.
    static boolean supported(String code) {
        if (code == null || code.isEmpty() || code.length() > 80) return false;
        for (int i = 0; i < code.length(); i++) {
            char c=code.charAt(i);
            // `input text` recebe argv diretamente, sem interpretacao por shell.
            // Suporte a pontuacao simples de etiquetas internas/Code 39/128.
            // Excluir espaco e % para nao acionar a sintaxe especial %s do input.
            if (!((c >= '0' && c <= '9') ||
                  (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                  c=='-' || c=='.' || c=='_' || c=='/' || c=='+')) return false;
        }
        return true;
    }
}
