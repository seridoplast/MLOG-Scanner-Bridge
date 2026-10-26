package br.com.seridoplast.mlogbridge;

/** Um intervalo em ms vale para o ciclo de leitura e entre Enters sucessivos.
 * Nao depende do estado visual do Flutter.
 */
final class FixedEnterCadence {
    private FixedEnterCadence(){}
    private static long addSaturated(long at,long interval){
        if(at<=0 || interval<=0)return Math.max(0L,at);
        return at>Long.MAX_VALUE-interval?Long.MAX_VALUE:at+interval;
    }
    static long delayMs(long scanStartedAt,long lastEnterAt,long intervalMs,long now){
        if(intervalMs<0)throw new IllegalArgumentException("intervalo negativo");
        long scanReady=addSaturated(scanStartedAt,intervalMs);
        long enterReady=lastEnterAt>0?addSaturated(lastEnterAt,intervalMs):0L;
        long target=Math.max(scanReady,enterReady);
        return target>now?target-now:0L;
    }
}
