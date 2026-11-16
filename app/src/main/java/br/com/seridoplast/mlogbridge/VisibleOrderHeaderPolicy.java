package br.com.seridoplast.mlogbridge;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Interprets only an order number printed in the top app header.
 * No OCR, no guessing from product codes, and no write access to MLOG. */
final class VisibleOrderHeaderPolicy {
    private static final Pattern NUMBER = Pattern.compile("^#?\\s*([0-9]{4,12})$");
    private static final Pattern NAMED = Pattern.compile("^(?:pedido|ordem|n[úu]mero|n[º°.]|os)\\s*[:#-]?\\s*([0-9]{4,12})$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern COMBINED = Pattern.compile("^(?:conf\\.?\\s*de\\s*entrada|confer[êe]ncia\\s*de\\s*entrada|separa[çc][ãa]o|conf\\.?\\s*de\\s*venda)\\s*[:#-]?\\s*([0-9]{4,12})$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private VisibleOrderHeaderPolicy(){}
    static boolean isRelevantTitle(String text){
        if(text==null)return false;
        String t=text.toLowerCase(Locale.ROOT);
        return t.contains("conf. de entrada") || t.contains("conf de entrada") ||
            t.contains("conferência de entrada") || t.contains("conferencia de entrada") ||
            t.contains("separação") || t.contains("separacao") ||
            t.contains("conf. de venda") || t.contains("conferência de venda");
    }
    static String parse(String text){
        if(text==null)return null;
        String clean=text.trim();
        Matcher m=NUMBER.matcher(clean);
        if(!m.matches())m=NAMED.matcher(clean);
        if(!m.matches())m=COMBINED.matcher(clean);
        if(!m.matches())return null;
        return OrderNumberPolicy.normalize(m.group(1));
    }
    static boolean isHeaderPosition(int left,int top,int right,int bottom,int width,int height){
        if(width<=0||height<=0||right<=left||bottom<=top)return false;
        float centerX=(left+right)*.5f/width;
        float centerY=(top+bottom)*.5f/height;
        // Toolbar: prevent confusing quantities, EANs, timestamps and pallet IDs.
        return centerY>=.045f && centerY<=.185f && centerX>=.54f && centerX<=.94f;
    }
    static boolean isCombinedHeaderPosition(int top,int bottom,int height){
        if(height<=0||bottom<=top)return false;
        float y=(top+bottom)*.5f/height;
        return y>=.045f && y<=.185f;
    }
    static boolean hasExplicitHeaderNumber(String text){
        if(text==null)return false;
        return COMBINED.matcher(text.trim()).matches();
    }
}
