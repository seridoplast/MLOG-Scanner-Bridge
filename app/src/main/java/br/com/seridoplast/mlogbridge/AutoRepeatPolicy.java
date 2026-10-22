package br.com.seridoplast.mlogbridge;

/** Pure timing and completion decisions for a confirmation-gated batch. */
final class AutoRepeatPolicy {
    static final long MIN_INTERVAL_MS = 155L;
    private AutoRepeatPolicy() {}
    static long delayMs(long lastSubmissionMs,long nowMs) {
        return Math.max(0L, MIN_INTERVAL_MS-Math.max(0L,nowMs-lastSubmissionMs));
    }
    static boolean firstConfirmed(String reason){
        return "campo_saiu".equals(reason) || "campo_mudou".equals(reason);
    }
}
