package br.com.seridoplast.mlogbridge;
public final class ZebraImeModeTest {
    private static void ok(boolean result,String label){if(!result)throw new AssertionError(label);}
    public static void main(String[] args){
        ok(ZebraImeMode.isValidCode("7896707342948","Code 39"),"valid EAN in Code39");
        ok(!ZebraImeMode.isValidCode("7896707342947","Code 39"),"bad EAN");
        ok(ZebraImeMode.isValidCode("AB-9045","Code 39"),"internal alphanumeric");
        ok(!ZebraImeMode.isValidCode("123\n","Code 39"),"control");
        ok(ZebraImeMode.isEcho("123","123",15),"broadcast echo");
        ok(!ZebraImeMode.isEcho("123","123",30),"different press after echo window");
        ok(!ZebraImeMode.isEcho("124","123",5),"different barcode");
        ok(ZebraImeMode.interval(0)==0,"zero ms");
        ok(ZebraImeMode.interval(100)==100,"100 ms");
        ScanQueue queue=new ScanQueue(ZebraImeMode.MAX_WAITING);
        for(int i=0;i<=ZebraImeMode.MAX_WAITING;i++)
            ok(queue.offer("123"),"fast repeated scan accepted "+i);
        ok(!queue.offer("123"),"bounded burst");
        int n=1;while(queue.complete())n++;
        ok(n==ZebraImeMode.MAX_WAITING+1,"FIFO retained distinct bipes");
        System.out.println("ZebraImeModeTest OK");
    }
}
