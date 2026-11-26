package br.com.seridoplast.mlogbridge;
public final class AdbConnectionHealthTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        AdbConnectionHealth h=new AdbConnectionHealth();
        h.lost(0);check(!h.shouldAlert(60_000)); // no alert at first setup
        h.healthy();check(h.retryDelay()==750);check(h.discoveryTimeout()==4000);h.retryFailed();check(h.retryDelay()==750);h.retryFailed();check(h.retryDelay()==1500);check(h.discoveryTimeout()==8000);h.retryFailed();check(h.retryDelay()==3000);h.retryFailed();check(h.retryDelay()==8000);h.retryFailed();check(h.retryDelay()==8000);h.wake();check(h.retryDelay()==750);check(h.discoveryTimeout()==4000);h.healthy();check(h.retryDelay()==750);check(h.discoveryTimeout()==4000);check(!h.probeFailed());check(h.probeFailed());
        h.healthy();check(!h.probeFailed());h.healthy();check(!h.probeFailed());
        h.lost(1000);h.lost(5000);check(!h.shouldAlert(15999));check(h.shouldAlert(16000));
        h.alerted();check(!h.shouldAlert(100_000));h.healthy();check(!h.shouldAlert(200_000));
        h.lost(200_000);h.healthy();check(!h.shouldAlert(250_000));
        h.lost(300_000);check(h.shouldAlert(315_000));h.reset();check(!h.shouldAlert(400_000));
        System.out.println("AdbConnectionHealthTest OK");
    }
}
