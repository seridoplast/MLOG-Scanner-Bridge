package br.com.seridoplast.mlogbridge;

/** Politica opt-in de Enter local: so com tela e campo confirmados. */
final class BridgeEnterPolicy {
    static final String PREFERENCE="enter_owned_by_bridge";
    static final long DELAY_MS=180;
    private BridgeEnterPolicy(){}
    static boolean active(boolean setting){return setting;}
    // O modo nativo dispensa a espera do sufixo CMX: testa o Enter apos a
    // estabilidade inicial do campo. Modo legado conserva atraso original.
    static final long NATIVE_DELAY_MS=60;
    static boolean shouldAttempt(boolean ownMode,String screen,long elapsed,
        boolean cmxEnterObserved,boolean editorFocused,boolean verified){
        return shouldAttempt(ownMode,screen,elapsed,cmxEnterObserved,
                editorFocused,verified,DELAY_MS);
    }
    static boolean shouldAttempt(boolean ownMode,String screen,long elapsed,
        boolean cmxEnterObserved,boolean editorFocused,boolean verified,long delayMs){
        return ownMode && (ConferenceScreenPolicy.isMapped(screen) || "separacao".equals(screen)) && verified &&
            elapsed>=delayMs && !cmxEnterObserved && editorFocused;
    }
}
