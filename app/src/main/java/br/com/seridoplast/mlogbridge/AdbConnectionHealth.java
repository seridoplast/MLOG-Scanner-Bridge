package br.com.seridoplast.mlogbridge;

/** In-process outage episode. No alerts at initial setup or for routine probes. */
final class AdbConnectionHealth {
    static final long ALERT_AFTER_MS=15_000L;
    private boolean everConnected, alerted;
    private long lostAt=-1;
    private int failures, retries;
    synchronized void healthy(){everConnected=true;lostAt=-1;alerted=false;failures=0;retries=0;}
    synchronized void reset(){everConnected=false;lostAt=-1;alerted=false;failures=0;retries=0;}
    synchronized void retryFailed(){if(retries<4)retries++;}
    synchronized long retryDelay(){return retries<=1?5000L:retries==2?10000L:retries==3?20000L:30000L;}
    synchronized boolean probeFailed(){return ++failures>=2;}
    synchronized void lost(long now){if(everConnected && lostAt<0){lostAt=now;alerted=false;}failures=0;}
    synchronized boolean shouldAlert(long now){return everConnected && lostAt>=0 && !alerted && now-lostAt>=ALERT_AFTER_MS;}
    synchronized void alerted(){alerted=true;}
}
