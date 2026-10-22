package br.com.seridoplast.mlogbridge;

/**
 * Janela fixa entre tentativas de acionamento do botao de leitura do MLOG.
 * Nao envia Enter, nao guarda codigos ou toques e nao usa tarefas agendadas.
 * Todos os horarios sao SystemClock.elapsedRealtime() no Android.
 */
final class TimedClickGate {
    static final long COOLDOWN_MS = 200L;
    private static final long TRIGGER_BOUNCE_MS = 70L;
    private static final long DECODE_ASSOCIATION_MS = 500L;
    private long unlockAt;
    private long physicalTriggerAt = -1L;
    private boolean physicalTriggerAllowed = true;
    private boolean broadcastConsumed;
    private boolean clicked;
    private long cycleSerial;

    boolean admitTrigger(long now){
        if(physicalTriggerAt>=0 && now-physicalTriggerAt<TRIGGER_BOUNCE_MS){
            return false; // mesmo pressionamento, sem alterar o estado do broadcast
        }
        physicalTriggerAt=now;
        if(now<unlockAt){
            physicalTriggerAllowed=false;
            return false;
        }
        physicalTriggerAllowed=true;
        startCycle(now);
        return true;
    }

    /** Permitir somente o primeiro broadcast do gatilho admitido. */
    boolean admitBroadcast(long now){
        if(physicalTriggerAt>=0 && now>=physicalTriggerAt &&
           now-physicalTriggerAt<DECODE_ASSOCIATION_MS){
            if(!physicalTriggerAllowed || broadcastConsumed)return false;
            broadcastConsumed=true;
            return true;
        }
        // Fallback para coletores que nao entregam gatilho ao Android.
        // Continua sendo uma leitura a cada intervalo fixo, sem fila.
        if(now<unlockAt)return false;
        startCycle(now);
        broadcastConsumed=true;
        return true;
    }

    /** Apenas uma tentativa nativa de clique por ciclo. */
    boolean reserveClick(long now){
        if(clicked || cycleSerial==0 || now>=unlockAt)return false;
        // Acao pode acontecer apos o fim do prazo se houver atraso do Flutter.
        // Nao executar gesto velho depois da janela em que foi solicitado.
        if(now>=unlockAt)return false;
        clicked=true;
        return true;
    }

    boolean hasClicked(){return clicked;}
    long remainingMs(long now){return Math.max(0L,unlockAt-now);}
    long generation(){return cycleSerial;}

    private void startCycle(long now){
        unlockAt=now+COOLDOWN_MS;
        clicked=false;
        broadcastConsumed=false;
        cycleSerial++;
    }
}
