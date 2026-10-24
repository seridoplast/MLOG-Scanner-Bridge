package br.com.seridoplast.mlogbridge;

/**
 * Janela fixa entre tentativas de acionamento do botao de leitura do MLOG.
 * Nao envia Enter, nao guarda codigos ou toques e nao usa tarefas agendadas.
 * Todos os horarios sao SystemClock.elapsedRealtime() no Android.
 */
final class TimedClickGate {
    static final long COOLDOWN_MS = 100L; // valor inicial quando nao ha ajuste salvo
    // O ajuste aceita qualquer inteiro nao negativo representavel pelo Android.
    // Ajuste em memoria; alteracoes nao modificam o prazo de um ciclo ja iniciado.
    private long cooldownMs = COOLDOWN_MS;
    static long validatedCooldown(long ms){
        return (ms>=0L && ms<=Integer.MAX_VALUE)?ms:-1L;
    }
    boolean setCooldownMs(long ms){
        if(validatedCooldown(ms)<0L)return false;
        cooldownMs=ms;
        return true;
    }
    long getCooldownMs(){return cooldownMs;}
    private static final long TRIGGER_BOUNCE_MS = 70L;
    private static final long DECODE_ASSOCIATION_MS = 500L;
    private long unlockAt;
    private long clickExpiresAt;
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
        if(clicked || cycleSerial==0)return false;
        // O prazo para realizar o unico toque por leitura e independente
        // do intervalo entre bipes. Isso permite 0/1/10 ms sem bloquear
        // o primeiro toque, mas evita executa-lo indefinidamente depois.
        if(now>=clickExpiresAt)return false;
        clicked=true;
        return true;
    }

    boolean hasClicked(){return clicked;}
    long remainingMs(long now){return Math.max(0L,unlockAt-now);}
    long generation(){return cycleSerial;}

    private void startCycle(long now){
        unlockAt=now+cooldownMs;
        clickExpiresAt=now+Math.max(COOLDOWN_MS,cooldownMs);
        clicked=false;
        broadcastConsumed=false;
        cycleSerial++;
    }
}
