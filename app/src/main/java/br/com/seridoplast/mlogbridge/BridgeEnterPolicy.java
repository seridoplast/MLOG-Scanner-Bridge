package br.com.seridoplast.mlogbridge;

/** Politica opt-in de Enter local: so com tela e campo confirmados. */
final class BridgeEnterPolicy {
    static final String PREFERENCE="enter_owned_by_bridge";
    static final long DELAY_MS=180;
    private BridgeEnterPolicy(){}
    static boolean active(boolean setting){return setting;}
    static boolean shouldAttempt(boolean ownMode,String screen,long elapsed,
        boolean cmxEnterObserved,boolean editorFocused,boolean verified){
        return ownMode && ConferenceScreenPolicy.isMapped(screen) && verified &&
            elapsed>=DELAY_MS && !cmxEnterObserved && editorFocused;
    }
}
