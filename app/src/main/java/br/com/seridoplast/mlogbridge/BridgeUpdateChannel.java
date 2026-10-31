package br.com.seridoplast.mlogbridge;

/** GitHub prerelease is a testing channel, never an unattended OTA update. */
final class BridgeUpdateChannel {
    private BridgeUpdateChannel() { }
    static boolean isManualTest(boolean draft, boolean prerelease) {
        return !draft && prerelease;
    }
    static boolean isAutoChannel(boolean draft, boolean prerelease) {
        return !draft && !prerelease;
    }
    static boolean mayAutoInstall(boolean draft, boolean prerelease, int rollout) {
        return isAutoChannel(draft, prerelease) && rollout > 0 && rollout <= 100;
    }
}
