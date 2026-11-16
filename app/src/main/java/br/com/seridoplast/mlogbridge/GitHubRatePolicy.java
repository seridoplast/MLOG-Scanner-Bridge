package br.com.seridoplast.mlogbridge;

/** Calculos de espera independentes do Android para os limites da API GitHub. */
final class GitHubRatePolicy {
    private GitHubRatePolicy() { }
    static boolean blocked(long now,long until){return until>now && until-now<48L*60*60*1000;}
    static boolean waitForFallback(long now,long last,long interval){
        return last>0 && now>=last && now-last<interval;
    }
    static long nextAllowedAt(long now,String epochSeconds,String retryAfterSeconds){
        long best=now+5L*60*1000;
        try {
            if(epochSeconds!=null){
                long reset=Math.multiplyExact(Long.parseLong(epochSeconds.trim()),1000L);
                if(reset>now && reset<now+24L*60*60*1000)best=Math.max(best,reset+60_000L);
            }
        } catch(Exception ignored) { }
        try {
            if(retryAfterSeconds!=null){
                long wait=Math.multiplyExact(Long.parseLong(retryAfterSeconds.trim()),1000L);
                if(wait>0 && wait<=24L*60*60*1000)best=Math.max(best,now+wait+60_000L);
            }
        } catch(Exception ignored) { }
        return best;
    }
}
