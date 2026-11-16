package br.com.seridoplast.mlogbridge;

/**
 * Normaliza apenas enquadramento de transporte emitido pelo decodificador CMX.
 * Preserva EXATAMENTE os caracteres do codigo, incluindo zeros a esquerda.
 * Nao corrige GTIN/check digit; esse julgamento pertence ao MLOG.
 */
final class ShizukuBarcodePayload {
    private ShizukuBarcodePayload() { }

    static String prepare(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        int left=0, right=raw.length();
        while (left<right && transport(raw.charAt(left))) left++;
        while (right>left && transport(raw.charAt(right-1))) right--;
        if (left==right) return null;
        // Controles no meio podem ser separadores GS1/FNC1 significativos.
        // Não os remover silenciosamente, pois isso mudaria o código.
        for (int i=left;i<right;i++) {
            char c=raw.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c) ||
                Character.isSpaceChar(c) || Character.getType(c)==Character.FORMAT)
                return null;
        }
        return raw.substring(left,right);
    }

    private static boolean transport(char c) {
        return Character.isISOControl(c) || Character.isWhitespace(c) ||
            Character.isSpaceChar(c) || Character.getType(c)==Character.FORMAT ||
            c=='\uFEFF' || c=='\u200B';
    }
}
