package br.com.seridoplast.mlogbridge;
public final class BarcodePreEnterGuardTest {
    private static void check(boolean cond) {if(!cond)throw new AssertionError();}
    public static void main(String[] args) {
        check(BarcodePreEnterGuard.matches("7896707342948", "7896707342948"));
        check(!BarcodePreEnterGuard.matches("7896707342948", "789670"));
        check(!BarcodePreEnterGuard.matches("7896707342948", "789670734294"));
        check(!BarcodePreEnterGuard.matches("7896707342948", "78967073429487"));
        check(!BarcodePreEnterGuard.matches("7896707342948", ""));
        check(!BarcodePreEnterGuard.matches("7896707342948", null));
        check(!BarcodePreEnterGuard.matches(null, "7896707342948"));
        check(!BarcodePreEnterGuard.matches("", ""));
        // Alguns clientes usam codigos internos curtos: nao exigir 13 digitos.
        check(BarcodePreEnterGuard.matches("1234", "1234"));
        check(!BarcodePreEnterGuard.matches("1234", "123"));
        System.out.println("BarcodePreEnterGuardTest OK");
    }
}
