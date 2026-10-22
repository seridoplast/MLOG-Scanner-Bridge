package br.com.seridoplast.mlogbridge;

/** Timing and submission accounting for an explicitly armed continuous batch. */
final class AutoRepeatPolicy {
    static final long MIN_INTERVAL_MS = 155L;
    private AutoRepeatPolicy() {}
    static int virtualRepeats(int totalBipes) {return Math.max(0,totalBipes-1);}
    static long delayMs(long lastSubmissionMs,long nowMs) {
        return Math.max(0L, MIN_INTERVAL_MS-Math.max(0L,nowMs-lastSubmissionMs));
    }
    // Count only a successfully dispatched Enter, never a visual-only UI change.
    static int afterEnterDispatched(int previouslySent,int target){
        if(previouslySent<1 || target<1 || previouslySent>=target)
            throw new IllegalArgumentException("invalid automatic repeat progress");
        return previouslySent+1;
    }
    static boolean refreshSameCode(CharSequence shown,String barcode,int alreadySent){
        return alreadySent>1 && barcode!=null && shown!=null &&
            barcode.equals(shown.toString().trim());
    }
    static boolean firstConfirmed(String reason){
        return "campo_saiu".equals(reason) || "campo_mudou".equals(reason);
    }
}
