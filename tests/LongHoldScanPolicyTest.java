package br.com.seridoplast.mlogbridge;
public class LongHoldScanPolicyTest {
    private static void ok(boolean yes){if(!yes)throw new AssertionError();}
    public static void main(String[] args){
        LongHoldScanPolicy p=new LongHoldScanPolicy();
        ok(p.down(277,1000));ok(!p.down(277,1001));
        ok(!p.shouldStart(2499));ok(p.shouldStart(2500));
        p.started();ok(p.isContinuous());ok(!p.shouldStart(3400));
        ok(!p.up(353));ok(p.up(277));ok(!p.isContinuous());
        ok(p.down(277,7000));ok(!p.up(277));ok(!p.isContinuous());
        ok(LongHoldScanPolicy.HOLD_MS==1500L);
        ok(LongHoldScanPolicy.pulseInterval(0)==250L);
        ok(LongHoldScanPolicy.pulseInterval(100)==250L);
        ok(LongHoldScanPolicy.pulseInterval(400)==400L);
        ok(LongHoldScanPolicy.canPulse(2,false));
        ok(!LongHoldScanPolicy.canPulse(3,false));
        ok(!LongHoldScanPolicy.canPulse(0,true));
        System.out.println("LongHoldScanPolicy: limiar 1500ms e pulsos OK");
    }
}
