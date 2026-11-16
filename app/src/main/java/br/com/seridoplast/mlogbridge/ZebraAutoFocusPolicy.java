package br.com.seridoplast.mlogbridge;

/** Limita abertura automatica do editor: somente telas de leitura,
 * poucas tentativas espacadas, nunca tecla/Enter. Sem APIs Android. */
final class ZebraAutoFocusPolicy {
    static final long MIN_ATTEMPT_GAP_MS=650L;
    static final long REARM_AFTER_MS=8000L;
    static final int MAX_ATTEMPTS=4;
    private String lastScreen="";
    private long lastAttemptAt=-1L;
    private int attempts;

    static boolean isEligible(String screen,boolean login,boolean dialog,boolean overlay){
        if(login || dialog || overlay || screen==null)return false;
        return "separacao".equals(screen) || "conferencia_venda".equals(screen) ||
               "conferencia_inicio".equals(screen) || "conferencia_produto".equals(screen) ||
               ConferenceScreenPolicy.ENTRY_PRODUCT.equals(screen) ||
               "pedidos".equals(screen) || "notas".equals(screen) ||
               "consulta".equals(screen) || "atividades".equals(screen);
    }
    boolean canSchedule(long now){
        return lastAttemptAt<0 || now-lastAttemptAt>=MIN_ATTEMPT_GAP_MS;
    }
    boolean shouldAttempt(String screen,long now){
        if(!screen.equals(lastScreen)){
            lastScreen=screen;lastAttemptAt=-1;attempts=0;
        }
        if(!canSchedule(now))return false;
        if(attempts>=MAX_ATTEMPTS){
            if(now-lastAttemptAt<REARM_AFTER_MS)return false;
            attempts=0;
        }
        attempts++; lastAttemptAt=now;
        return true;
    }
    boolean hasAttemptsRemaining(){return attempts<MAX_ATTEMPTS;}
    void markReady(){attempts=0;lastAttemptAt=-1;}
}
