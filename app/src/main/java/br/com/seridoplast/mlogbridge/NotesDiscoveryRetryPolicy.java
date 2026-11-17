package br.com.seridoplast.mlogbridge;

/** Five minute retry policy for LAN discovery; independent of Android APIs. */
final class NotesDiscoveryRetryPolicy {
    static final long RETRY_MS=300_000L;
    private NotesDiscoveryRetryPolicy(){}
    static boolean ready(long nowElapsed,long previousAttemptElapsed){
        return previousAttemptElapsed<=0L || nowElapsed<previousAttemptElapsed
            || nowElapsed-previousAttemptElapsed>=RETRY_MS;
    }
}
