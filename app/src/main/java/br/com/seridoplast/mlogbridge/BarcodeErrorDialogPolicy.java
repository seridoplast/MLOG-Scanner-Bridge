package br.com.seridoplast.mlogbridge;

import java.text.Normalizer;
import java.util.Locale;

/** Somente avisos de codigo de barras/produto nao localizado sao dispensaveis por bip. */
final class BarcodeErrorDialogPolicy {
    private BarcodeErrorDialogPolicy() {}
    static String normalized(CharSequence text){
        if(text==null)return "";
        String value=Normalizer.normalize(text.toString(),Normalizer.Form.NFD);
        return value.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }
    static boolean isKnownBarcodeError(CharSequence text){
        return isKnownNormalized(normalized(text));
    }
    static boolean isKnownNormalized(String s){
        boolean item=s.contains("codigo") || s.contains("cod. barras") ||
                s.contains("produto") || s.contains("barcode");
        boolean error=s.contains("nao encontrado") || s.contains("nao localizado") ||
                s.contains("nao identificado") || s.contains("codigo invalido") ||
                s.contains("codigo incorreto") || s.contains("produto invalido");
        return item && error;
    }
    static boolean isOk(CharSequence text){
        String s=normalized(text);
        return "ok".equals(s) || "entendi".equals(s);
    }
}
