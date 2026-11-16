package br.com.seridoplast.mlogbridge;
public final class GitHubRatePolicyTest {
    static void check(boolean ok){if(!ok)throw new AssertionError("rate policy");}
    public static void main(String[] args){
        long now=1_700_000_000_000L;
        check(GitHubRatePolicy.blocked(now,now+60_000));
        check(!GitHubRatePolicy.blocked(now,now-1));
        check(!GitHubRatePolicy.waitForFallback(now,0,1_000));
        check(GitHubRatePolicy.waitForFallback(now,now-500,1_000));
        check(!GitHubRatePolicy.waitForFallback(now,now-2000,1_000));
        check(GitHubRatePolicy.nextAllowedAt(now,String.valueOf((now+3_600_000)/1000),null)>=now+3_600_000);
        check(GitHubRatePolicy.nextAllowedAt(now,null,"600")>=now+600_000);
        System.out.println("GitHubRatePolicy OK");
    }
}
