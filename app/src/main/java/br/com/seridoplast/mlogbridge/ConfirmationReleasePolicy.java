package br.com.seridoplast.mlogbridge;

/** Nao libera por timeout: somente por evidencia de mudanca do Flutter.
 *  Funcao pura para manter a regra auditavel e testavel fora do Android. */
final class ConfirmationReleasePolicy {
    private ConfirmationReleasePolicy(){}
    static boolean shouldRelease(long ageMs, boolean screenChanged,
                                 boolean editorPresent, boolean otherEditorsPresent,
                                 boolean sameCode){
        if(ageMs<70)return false;
        if(screenChanged)return true;
        if(editorPresent)return !sameCode;
        return !otherEditorsPresent;
    }
}
