package br.com.seridoplast.mlogbridge;
public final class BarcodeArtifactReportTest {
    private static int assertions;
    private static void check(boolean ok,String msg){assertions++;if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        check(BarcodeArtifactReport.describe("ABC123").startsWith("Nenhum espaço"),"clean code");
        String mixed=BarcodeArtifactReport.describe(" ABC 123\r\n\t\u200b");
        check(mixed.contains("2 espaços"),"normal and trailing space count");
        check(mixed.contains("1 Enter/CR"),"CR");
        check(mixed.contains("1 quebra de linha/LF"),"LF");
        check(mixed.contains("1 Tab"),"TAB");
        check(mixed.contains("1 caractere invisível"),"zero width");
        check("ABC123".equals(BarcodeCleaner.clean(" A\tBC\r123\n\u200b")),"clean preserves significant chars");
        check(BarcodeCleaner.clean("\r\n \t").isEmpty(),"only delimiters");
        check(BarcodeArtifactReport.describe("A-B/5").contains("Nenhum"),"punctuation is significant");
        System.out.println("BarcodeArtifactReportTest OK: "+assertions+" checks");
    }
}
