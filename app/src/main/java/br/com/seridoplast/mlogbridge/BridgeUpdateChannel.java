package br.com.seridoplast.mlogbridge;

/** Only published stable GitHub releases may be installed unattended. */
final class BridgeUpdateChannel {
    private BridgeUpdateChannel() { }
    static boolean isAutoChannel(boolean draft, boolean prerelease) {
        return !draft && !prerelease;
    }
    static boolean mayAutoInstall(boolean draft, boolean prerelease, int rollout) {
        return isAutoChannel(draft, prerelease) && rollout > 0 && rollout <= 100;
    }
}
