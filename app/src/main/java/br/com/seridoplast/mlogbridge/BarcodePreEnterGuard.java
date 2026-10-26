package br.com.seridoplast.mlogbridge;

/** Confere SOMENTE se o campo recebeu o codigo completo antes do Enter.
 * Nao valida contagem visual, servidor ou tempo de confirmacao do MLOG.
 */
final class BarcodePreEnterGuard {
    private BarcodePreEnterGuard() {}

    static boolean matches(String expected, CharSequence fieldText) {
        return expected!=null && !expected.isEmpty() && fieldText!=null &&
            expected.contentEquals(fieldText);
    }
}
