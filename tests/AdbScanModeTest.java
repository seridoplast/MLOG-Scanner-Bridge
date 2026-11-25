package br.com.seridoplast.mlogbridge;

public final class AdbScanModeTest {
    private static void expect(boolean condition){if(!condition)throw new AssertionError("Adb policy");}
    public static void main(String[] args){
        expect(AdbScanMode.supported("7896707342948"));
        expect(AdbScanMode.supported("ABC123abc"));
        expect(!AdbScanMode.supported(null));
        expect(!AdbScanMode.supported(""));
        expect(!AdbScanMode.supported("7896707\n342948"));
        expect(!AdbScanMode.supported("123 456"));
        expect(!AdbScanMode.supported("ab;echo"));
        expect(!AdbScanMode.supported("123\t"));
        expect(!AdbScanMode.supported("1".repeat(81)));
        System.out.println("AdbScanModeTest OK");
    }
}
