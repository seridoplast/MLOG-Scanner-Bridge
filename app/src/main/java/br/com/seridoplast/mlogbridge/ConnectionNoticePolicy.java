package br.com.seridoplast.mlogbridge;

/** A rolling 24-hour window, persisted by the caller using wall-clock time. */
final class ConnectionNoticePolicy {
    static final long INTERVAL_MS=24L*60L*60L*1000L;
    private ConnectionNoticePolicy(){}
    static boolean mayNotify(long last,long now){
        if(now<=0L)return false;
        if(last<=0L)return true;
        // Clock rollback must not unlock the cooldown or cause repeated alerts.
        return now>=last && now-last>=INTERVAL_MS;
    }
}
