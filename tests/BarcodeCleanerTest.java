package br.com.seridoplast.mlogbridge;
public final class BarcodeCleanerTest {
    private static int assertions;
    private static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check("7896901668837".equals(BarcodeCleaner.clean("\r\n 7896901668837 \t\n")),"strip scanner terminators");
        check("ABC-123.$/%+".equals(BarcodeCleaner.clean(" A\u200bBC-123.$/%+\ufeff")),"preserve code39 punctuation");
        check("Code39".equals(BarcodeCleaner.clean("C\u00a0od\u200be39")),"strip Unicode invisible");
        check("123345".equals(BarcodeCleaner.clean("12\u000233\u000345")),"strip control chars");
        check("".equals(BarcodeCleaner.clean("\n\t\u200b")),"reject empty result downstream");
        check(null==BarcodeCleaner.clean(null),"null remains null");
        check("abcDEF".equals(BarcodeCleaner.clean("abcDEF")),"case preserved");
        System.out.println("BarcodeCleanerTest OK: "+assertions+" checks");
    }
}
