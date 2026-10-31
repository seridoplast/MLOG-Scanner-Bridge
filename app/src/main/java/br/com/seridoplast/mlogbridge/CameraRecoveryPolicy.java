package br.com.seridoplast.mlogbridge;

/**
 * Protege o ciclo de recuperacao de tela contra teclados que piscam,
 * reentradas e multiplas aberturas consecutivas. Sem dependencias Android.
 * Nao confirma leitura nem reenvia codigo algum.
 */
final class CameraRecoveryPolicy {
    // Uma abertura curta real tambem deve ser detectada; flashes mais rapidos
    // durante a micro-navegacao sao suprimidos pelo servico.
    static final long MIN_KEYBOARD_OPEN_MS=150L;
    // Evita ciclos, mas permite recuperar uma nova interacao poucos segundos depois.
    static final long COOLDOWN_MS=2500L;
    private boolean keyboardVisible;
    private long keyboardOpenedAt;
    private boolean recovering;
    private long lastStartedAt=-COOLDOWN_MS;

    /** true apenas quando um teclado REAL ficou visivel e depois fechou. */
    boolean observeKeyboard(boolean visible,long now){
        if(visible==keyboardVisible)return false;
        if(visible){
            keyboardVisible=true;
            keyboardOpenedAt=now;
            return false;
        }
        keyboardVisible=false;
        return !recovering && now-keyboardOpenedAt>=MIN_KEYBOARD_OPEN_MS;
    }

    boolean begin(long now,boolean manual){
        if(recovering || keyboardVisible)return false;
        if(!manual && now-lastStartedAt<COOLDOWN_MS)return false;
        recovering=true;
        lastStartedAt=now;
        return true;
    }

    boolean isRecovering(){return recovering;}
    boolean keyboardVisible(){return keyboardVisible;}
    void finish(){recovering=false;}
    void resetKeyboard(){keyboardVisible=false;keyboardOpenedAt=0;}
}
