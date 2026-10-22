package br.com.seridoplast.mlogbridge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ler apenas contagem exibida no cartão do produto da separação.
 * Nao e prova de gravação no servidor; apenas confirmacao visual do Flutter. */
final class SeparatedQuantityProbe {
    static final class Result {
        final String product;
        final int count;
        final String unit;
        Result(String product,int count,String unit){this.product=product;this.count=count;this.unit=unit;}
        boolean sameProduct(Result other){return other!=null && product.equals(other.product) && unit.equals(other.unit);}
    }
    private static final Pattern PRODUCT=Pattern.compile("(?s)^\\s*([0-9]{2,})\\s*[-–]");
    private static final Pattern SEPARATED=Pattern.compile(
        "(?is)([0-9]+)\\s*(PC|UN|UND|UNID|PÇ|PÇS)\\s*SEPARADO(?:S|AS)?");
    private SeparatedQuantityProbe(){}
    static Result parse(CharSequence text){
        if(text==null)return null;
        String s=text.toString();
        if(!s.toUpperCase(java.util.Locale.ROOT).contains("SEPARAR"))return null;
        Matcher p=PRODUCT.matcher(s),q=SEPARATED.matcher(s);
        if(!p.find() || !q.find())return null;
        try{return new Result(p.group(1),Integer.parseInt(q.group(1)),q.group(2).toUpperCase(java.util.Locale.ROOT));}
        catch(NumberFormatException ex){return null;}
    }
}
