package br.com.seridoplast.mlogbridge;

final class EnterReadinessPolicyTest {
    static void check(boolean ok, String msg){ if(!ok)throw new AssertionError(msg); }
    public static void main(String[] args){
        check(EnterReadinessPolicy.INITIAL_DELAY_MS>=200,"stable initial delay");
        check(EnterReadinessPolicy.FAST_INITIAL_DELAY_MS>=40 &&
              EnterReadinessPolicy.FAST_INITIAL_DELAY_MS<EnterReadinessPolicy.INITIAL_DELAY_MS,
              "fast mode has a guarded shorter delay");
        check(EnterReadinessPolicy.canRetry(200),"early false-negative is retried");
        check(EnterReadinessPolicy.canRetry(1000),"slow keyboard still retried");
        check(!EnterReadinessPolicy.canRetry(1650),"bounded deadline");
        long elapsed=EnterReadinessPolicy.INITIAL_DELAY_MS, passes=0;
        while(EnterReadinessPolicy.canRetry(elapsed)) {
            long next=EnterReadinessPolicy.nextDelay(elapsed);
            check(next>0 && next<=85,"bounded retry interval");
            elapsed+=next; passes++;
            check(passes<100,"no infinite loop");
        }
        check(elapsed>=1650 && elapsed<1700,"deadline reached");
        System.out.println("EnterReadinessPolicyTest OK, checks="+passes);
    }
}
