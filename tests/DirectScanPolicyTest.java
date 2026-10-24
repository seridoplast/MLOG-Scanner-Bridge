package br.com.seridoplast.mlogbridge;
public final class DirectScanPolicyTest {
    static int n;
    static void ok(boolean yes){n++;if(!yes)throw new AssertionError("Check "+n);}
    public static void main(String[] args){
        ok(DirectScanPolicy.accept(DirectScanPolicy.ACTION,true));
        ok(!DirectScanPolicy.accept("com.cmx.scanner.DATA",true));
        ok(!DirectScanPolicy.accept("com.hikvision.ACTION_INPUT",true));
        ok(!DirectScanPolicy.accept(DirectScanPolicy.ACTION,false));
        ok(DirectScanPolicy.accept("com.cmx.scanner.DATA",false));
        ok(DirectScanPolicy.barcodeRepeatedInField("789","789789"));
        ok(!DirectScanPolicy.barcodeRepeatedInField("789","789"));
        ok(!DirectScanPolicy.barcodeRepeatedInField("789","789123"));
        System.out.println("OK DirectScanPolicy: "+n+" testes");
    }
}
