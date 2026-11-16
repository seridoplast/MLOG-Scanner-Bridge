package br.com.seridoplast.mlogbridge;

/** Confere SOMENTE se o campo recebeu o codigo completo antes do Enter.
 * Nao valida contagem visual, servidor ou tempo de confirmacao do MLOG.
 */
final class BarcodePreEnterGuard {
    private BarcodePreEnterGuard() {}

    /** Somente um prefixo real do codigo atual pode receber reparo.
     * Um valor diferente pode ser uma leitura antiga ou uma troca de produto;
     * sobrescreve-lo silenciosamente pode criar uma conferencia incorreta.
     */
    static boolean canRepairPrefix(String expected, CharSequence fieldText) {
        if(expected==null || expected.isEmpty() || fieldText==null)return false;
        String found=fieldText.toString();
        return !found.isEmpty() && found.length()<expected.length() &&
            expected.startsWith(found);
    }

    static boolean matches(String expected, CharSequence fieldText) {
        return expected!=null && !expected.isEmpty() && fieldText!=null &&
            expected.contentEquals(fieldText);
    }
}
