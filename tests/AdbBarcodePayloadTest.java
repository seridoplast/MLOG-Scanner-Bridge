package br.com.seridoplast.mlogbridge;

public final class AdbBarcodePayloadTest {
    private static void eq(String expect, String actual) {
        if (expect==null ? actual!=null : !expect.equals(actual))
            throw new AssertionError("Expected " + expect + " / got " + actual);
    }
    private static void ok(boolean value) { if(!value)throw new AssertionError(); }
    public static void main(String[] args) {
        eq("7890000000105",AdbBarcodePayload.prepare("7890000000105"));
        eq("7890000000105",AdbBarcodePayload.prepare("\u00027890000000105\u0003\r\n\u0000"));
        eq("00000000000162",AdbBarcodePayload.prepare("\ufeff00000000000162\r"));
        eq("AB-23/4",AdbBarcodePayload.prepare("\tAB-23/4\n"));
        eq(null,AdbBarcodePayload.prepare("\r\n"));
        eq(null,AdbBarcodePayload.prepare("123\u001D45")); // GS1: not safe to erase an inner separator
        eq(null,AdbBarcodePayload.prepare("123 45")); // never collapse spaces inside payload
        ok(AdbScanMode.supported("7890000000105"));
        ok(AdbScanMode.supported("AB-23/4"));
        ok(!AdbScanMode.supported("123%45"));
        ok(!AdbScanMode.supported("123 45"));
        System.out.println("AdbBarcodePayloadTest OK");
    }
}
