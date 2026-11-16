package br.com.seridoplast.mlogbridge;

/** Verificacao rapida de codigo antes de preencher o MLOG.
 * Codigos internos/Code39 nao devem receber um checksum presumido.
 */
final class BarcodeIntegrityPolicy {
    private BarcodeIntegrityPolicy() {}
    static boolean isValid(String code, String declaredType) {
        if (code==null || code.isEmpty() || code.length()>64)return false;
        for (int i=0;i<code.length();i++) {
            char c=code.charAt(i);
            if(c<33 || c>126)return false; // Apos limpar transporte: ASCII imprimivel.
        }
        String t=declaredType==null?"":declaredType.toUpperCase(java.util.Locale.ROOT);
        boolean numeric=code.matches("[0-9]+");
        // EAN/UPC declarados como tais tem digito de verificacao obrigatorio.
        if (t.contains("EAN-13")) return numeric && code.length()==13 && gtinValid(code);
        if (t.contains("EAN-8")) return numeric && code.length()==8 && gtinValid(code);
        if (t.contains("UPC-A")) return numeric && code.length()==12 && gtinValid(code);
        // Alguns produtos brasileiros sao lidos como Code39 neste firmware.
        // O prefixo GS1 brasileiro permite detectar leituras 789/790 quebradas,
        // sem rejeitar codigos internos com outro formato e comprimento.
        if(numeric && code.length()==13 && (code.startsWith("789") || code.startsWith("790")))
            return gtinValid(code);
        return true;
    }
    static boolean gtinValid(String code) {
        if(code==null || !code.matches("[0-9]+") ||
           !(code.length()==8 || code.length()==12 || code.length()==13 || code.length()==14))
            return false;
        int sum=0,weight=3;
        for(int i=code.length()-2;i>=0;i--){sum+=(code.charAt(i)-'0')*weight;weight=weight==3?1:3;}
        return (10-sum%10)%10==code.charAt(code.length()-1)-'0';
    }
}
