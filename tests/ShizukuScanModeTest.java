package br.com.seridoplast.mlogbridge;

public final class ShizukuScanModeTest {
    private static void expect(boolean condition){if(!condition)throw new AssertionError("Shizuku policy");}
    public static void main(String[] args){
        expect(ShizukuScanMode.supported("7896707342948"));
        expect(ShizukuScanMode.supported("ABC123abc"));
        expect(!ShizukuScanMode.supported(null));
        expect(!ShizukuScanMode.supported(""));
        expect(!ShizukuScanMode.supported("7896707\n342948"));
        expect(!ShizukuScanMode.supported("123 456"));
        expect(!ShizukuScanMode.supported("ab;echo"));
        expect(!ShizukuScanMode.supported("123\t"));
        expect(!ShizukuScanMode.supported("1".repeat(81)));
        System.out.println("ShizukuScanModeTest OK");
    }
}
