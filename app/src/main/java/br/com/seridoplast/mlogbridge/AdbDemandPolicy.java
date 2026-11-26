package br.com.seridoplast.mlogbridge;

/** Wi-Fi changes do not constitute demand for privileged transport. */
final class AdbDemandPolicy {
    private AdbDemandPolicy(){}
    static boolean needsTransport(boolean localHealthy,boolean provisioning,boolean install){
        return !localHealthy || provisioning || install;
    }
    static boolean earlyLoss(long parkedAt,long now){
        return parkedAt>=0 && now>=parkedAt && now-parkedAt<=15_000L;
    }
}
