package br.com.seridoplast.mlogbridge;
public final class BarcodeErrorDialogPolicyTest {
    private static void ok(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        ok(BarcodeErrorDialogPolicy.isKnownBarcodeError("Código de barras não encontrado"));
        ok(BarcodeErrorDialogPolicy.isKnownBarcodeError("Produto não localizado"));
        ok(BarcodeErrorDialogPolicy.isKnownBarcodeError("Código inválido"));
        ok(!BarcodeErrorDialogPolicy.isKnownBarcodeError("Confirmar cancelamento"));
        ok(!BarcodeErrorDialogPolicy.isKnownBarcodeError("Senha incorreta"));
        ok(!BarcodeErrorDialogPolicy.isKnownBarcodeError("Encerrar item"));
        ok(BarcodeErrorDialogPolicy.isOk("OK"));
        ok(!BarcodeErrorDialogPolicy.isOk("Cancelar"));
        ok(!BarcodeErrorDialogPolicy.isOk("Confirmar"));
        System.out.println("BarcodeErrorDialogPolicy OK");
    }
}
