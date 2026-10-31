package br.com.seridoplast.mlogbridge;
public final class BridgeUpdateIntervalsTest {
    public static void main(String[] args) {
        if(BridgeUpdateIntervals.CHECK_MINUTES!=10)throw new AssertionError("Auto check must be 10 min");
        if(BridgeUpdateIntervals.INSTALL_RETRY_MINUTES!=2)throw new AssertionError("Install retry must be 2 min");
        System.out.println("BridgeUpdateIntervalsTest OK");
    }
}
