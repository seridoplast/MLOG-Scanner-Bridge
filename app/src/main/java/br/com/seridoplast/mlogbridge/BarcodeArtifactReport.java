package br.com.seridoplast.mlogbridge;

/** Diagnostica sufixos e caracteres ocultos que o scanner realmente transmitiu.
 * Nunca infere um Enter que tenha sido emitido como tecla separada do broadcast. */
final class BarcodeArtifactReport {
    private BarcodeArtifactReport() {}

    static String describe(String input) {
        if (input == null) return "Aguardando leitura para verificar caracteres invisiveis.";
        int spaces=0, tabs=0, cr=0, lf=0, controls=0, invisible=0;
        for(int i=0;i<input.length();) {
            int cp=input.codePointAt(i);
            i+=Character.charCount(cp);
            if (cp==' ') {spaces++;continue;}
            if (cp=='\t') {tabs++;continue;}
            if (cp=='\r') {cr++;continue;}
            if (cp=='\n') {lf++;continue;}
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) ||
                Character.getType(cp)==Character.FORMAT || cp==0xFEFF ||
                cp==0x200B || cp==0x2060) {invisible++;continue;}
            if (Character.isISOControl(cp)) controls++;
        }
        StringBuilder result = new StringBuilder();
        append(result, spaces, "espaço", "espaços");
        append(result, tabs, "Tab", "Tabs");
        append(result, cr, "Enter/CR", "Enter/CR");
        append(result, lf, "quebra de linha/LF", "quebras de linha/LF");
        append(result, invisible, "caractere invisível", "caracteres invisíveis");
        append(result, controls, "caractere de controle", "caracteres de controle");
        return result.length()==0 ? "Nenhum espaço ou caractere invisível detectado." :
            "Atenção: " + result.toString() + " no código recebido.";
    }
    private static void append(StringBuilder out,int number,String singular,String plural){
        if (number==0)return;
        if (out.length()>0)out.append(", ");
        out.append(number).append(" ").append(number==1?singular:plural);
    }
}
