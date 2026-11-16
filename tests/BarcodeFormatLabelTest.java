package br.com.seridoplast.mlogbridge;

public final class BarcodeFormatLabelTest {
    private static int count;
    private static void eq(String expected,String actual){
        count++;
        if(!expected.equals(actual))throw new AssertionError("Expected ["+expected+"] but got ["+actual+"]");
    }
    public static void main(String[] args){
        eq("Code 39",BarcodeFormatLabel.normalize("Code39"));
        eq("Code 39",BarcodeFormatLabel.normalize("CODE_39"));
        eq("Code 39",BarcodeFormatLabel.normalize("MVID_CODE_CODE39"));
        eq("Code 128",BarcodeFormatLabel.normalize("CODE-128"));
        eq("EAN-13",BarcodeFormatLabel.normalize("ean13"));
        eq("EAN-8",BarcodeFormatLabel.normalize("EAN_8"));
        eq("QR Code",BarcodeFormatLabel.normalize("QR_CODE"));
        eq("Data Matrix",BarcodeFormatLabel.normalize("DataMatrix"));
        eq("PDF417",BarcodeFormatLabel.normalize("PDF417"));
        eq("Não informado pelo CMX",BarcodeFormatLabel.normalize(null));
        eq("Não informado pelo CMX",BarcodeFormatLabel.normalize(" "));
        eq("Aztec",BarcodeFormatLabel.normalize("Aztec"));
        eq("GS1-128",BarcodeFormatLabel.normalize("GS1_128"));
        eq("Micro QR",BarcodeFormatLabel.normalize("MICRO_QR"));
        eq("Code 39 Full ASCII",BarcodeFormatLabel.normalize("CODE39_EXTENDED"));
        eq("ID 19 (tipo sem tabela CMX)",BarcodeFormatLabel.normalize("19"));
        eq("GS1 DataBar",BarcodeFormatLabel.normalize("GS1_DATABAR"));
        System.out.println("BarcodeFormatLabelTest OK: "+count+" assertions");
    }
}
