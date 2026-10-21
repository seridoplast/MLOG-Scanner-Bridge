package br.com.seridoplast.mlogbridge;

/** Politica pura de retentativa para a tecla IME; nao armazena leituras. */
final class EnterReadinessPolicy {
    private EnterReadinessPolicy() {}
    static final long INITIAL_DELAY_MS=235L;
    static final long MAX_READY_WAIT_MS=1650L;
    static final long RETRY_INTERVAL_MS=85L;
    static boolean canRetry(long ageMs){ return ageMs < MAX_READY_WAIT_MS; }
    static long nextDelay(long ageMs){
        long left=MAX_READY_WAIT_MS-ageMs;
        return Math.max(30L, Math.min(RETRY_INTERVAL_MS,left));
    }
}
