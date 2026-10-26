package br.com.seridoplast.mlogbridge;

/** Estado deterministico do gatilho fisico para leitura continua opt-in. */
final class LongHoldScanPolicy {
    static final long HOLD_MS=1500L;
    // Ritmo minimo para repeticao programada; nao promete decodificacao
    // em 250 ms (o firmware e o MLOG podem precisar de mais tempo).
    static long pulseInterval(long configuredMs){
        return Math.max(250L,configuredMs);
    }
    static boolean canPulse(int pendingCount,boolean awaitingEditor){
        return pendingCount<3 && !awaitingEditor;
    }
    private int heldKey=-1;
    private long downAt=-1L;
    private boolean continuous=false;
    boolean down(int key,long at) {
        if(key<0 || heldKey!=-1)return false;
        heldKey=key;downAt=at;continuous=false;return true;
    }
    boolean shouldStart(long at){return heldKey!=-1 && !continuous && at-downAt>=HOLD_MS;}
    void started(){if(heldKey!=-1)continuous=true;}
    boolean up(int key){
        if(key!=heldKey)return false;
        boolean wasContinuous=continuous;
        reset();return wasContinuous;
    }
    boolean holdingKey(int key){return heldKey==key;}
    boolean isContinuous(){return continuous;}
    void reset(){heldKey=-1;downAt=-1L;continuous=false;}
}
