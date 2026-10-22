package br.com.seridoplast.mlogbridge;

/**
 * Um gatilho -> no maximo um acionamento de campo -> uma leitura ->
 * uma confirmacao verificavel. Sem fila para executar toques atrasados.
 * Acessado na thread principal da acessibilidade; nenhum dado do codigo armazenado.
 */
final class ScanCycleGate {
    enum Phase { IDLE, TRIGGERED, RECEIVED, WAIT_CONFIRMATION, NEEDS_ATTENTION }
    private Phase phase=Phase.IDLE;
    private boolean clickClaimed;
    private long lastReleasedAt=-100000;

    Phase phase(){return phase;}
    boolean isIdle(){return phase==Phase.IDLE;}
    boolean isWaitingForDecode(){return phase==Phase.TRIGGERED;}
    boolean hasClicked(){return clickClaimed;}
    boolean beginTrigger(long now){
        if(phase!=Phase.IDLE || now-lastReleasedAt<65)return false;
        phase=Phase.TRIGGERED;clickClaimed=false;return true;
    }
    boolean beginBarcode(long now){
        if(phase==Phase.IDLE){
            if(now-lastReleasedAt<65)return false;
            clickClaimed=false;
            phase=Phase.RECEIVED;
            return true;
        }
        if(phase==Phase.TRIGGERED){phase=Phase.RECEIVED;return true;}
        return false;
    }
    boolean claimClick(){
        if(phase==Phase.IDLE || phase==Phase.NEEDS_ATTENTION || clickClaimed)return false;
        clickClaimed=true;return true;
    }
    void waitingConfirmation(){
        if(phase==Phase.RECEIVED)phase=Phase.WAIT_CONFIRMATION;
    }
    void completed(long now){release(now);}
    void aborted(long now){release(now);}
    void needsAttention(){if(phase!=Phase.IDLE)phase=Phase.NEEDS_ATTENTION;}
    private void release(long now){phase=Phase.IDLE;clickClaimed=false;lastReleasedAt=now;}
}
