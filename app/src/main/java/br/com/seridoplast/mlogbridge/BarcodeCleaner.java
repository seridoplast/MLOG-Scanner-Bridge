package br.com.seridoplast.mlogbridge;

/** Removes transport/paste artefacts without changing the significant ASCII barcode. */
final class BarcodeCleaner {
    private BarcodeCleaner() {}
    static String clean(String input) {
        if (input == null) return null;
        StringBuilder result = new StringBuilder(input.length());
        for (int index = 0; index < input.length();) {
            int cp = input.codePointAt(index);
            index += Character.charCount(cp);
            int type = Character.getType(cp);
            // Scanner suffixes: CR, LF, TAB, STX/ETX, Unicode zero-width markers,
            // NBSP and other separator characters. Do NOT remove punctuation,
            // uppercase/lowercase or change EAN check digits.
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) ||
                Character.isISOControl(cp) || type == Character.FORMAT ||
                cp == 0xFEFF || cp == 0x200B || cp == 0x2060) continue;
            result.appendCodePoint(cp);
        }
        return result.toString();
    }
}
