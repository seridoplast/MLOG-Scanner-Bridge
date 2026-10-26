package br.com.seridoplast.mlogbridge;
public class BarcodeIntegrityPolicyTest {
    private static void ok(boolean yes){if(!yes)throw new AssertionError();}
    public static void main(String[] args){
        ok(BarcodeIntegrityPolicy.isValid("7896707342948","Code 39"));
        ok(!BarcodeIntegrityPolicy.isValid("7896707342947","Code 39"));
        ok(BarcodeIntegrityPolicy.isValid("401234","Code 39"));
        ok(BarcodeIntegrityPolicy.isValid("AB-4093","Code 39"));
        ok(!BarcodeIntegrityPolicy.isValid("1234567890123","EAN-13"));
        ok(!BarcodeIntegrityPolicy.isValid("","EAN-13"));
        ok(!BarcodeIntegrityPolicy.isValid("123\n","Code 39"));
        ok(BarcodeIntegrityPolicy.gtinValid("7896707342948"));
    }
}
