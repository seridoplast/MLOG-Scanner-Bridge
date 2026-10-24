package br.com.seridoplast.mlogbridge;

/** Roteamento exclusivo do scanner nativo. Sem SDK proprietario/privilegios. */
final class DirectScanPolicy {
    static final String PREF = "direct_scanner_broadcast";
    static final String ACTION = "br.com.seridoplast.mlogbridge.SCAN";
    static final String EXTRA = "CmxCode";
    private DirectScanPolicy() {}
    static boolean accept(String action, boolean exclusive) {
        if (exclusive) return ACTION.equals(action);
        return !ACTION.equals(action); // canal de testes nao entra no modo legado
    }
    static boolean barcodeRepeatedInField(String code, String field) {
        return code != null && !code.isEmpty() && field != null &&
                field.equals(code + code);
    }
}
