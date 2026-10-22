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
        eq("Nao informado pelo CMX",BarcodeFormatLabel.normalize(null));
        eq("Nao informado pelo CMX",BarcodeFormatLabel.normalize(" "));
        eq("Aztec",BarcodeFormatLabel.normalize("Aztec"));
        System.out.println("BarcodeFormatLabelTest OK: "+count+" assertions");
    }
}
