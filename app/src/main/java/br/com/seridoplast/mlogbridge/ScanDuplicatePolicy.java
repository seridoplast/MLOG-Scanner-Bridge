package br.com.seridoplast.mlogbridge;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Evita que dois broadcasts do MESMO decodificador contem como dois bipes.
 *
 * O diagnostico de 21/10 encontrou broadcasts duplicados 69-84ms apos
 * o primeiro. Na referencia Key Mapper o menor intervalo entre duas
 * decodificacoes fisicas distintas foi 192ms. 140ms preserva essa amostra.
 *
 * Nao compara com o ultimo codigo da fila: usa apenas o horario de entrada,
 * para nao deixar o tempo de processamento do Flutter afetar a deduplicacao.
 * Nao persiste codigo nem registra o conteudo em logs.
 */
final class ScanDuplicatePolicy {
    static final long DUPLICATE_WINDOW_MS=140L;
    private final Map<String,Long> recent=new HashMap<>();

    synchronized boolean shouldAccept(String code,long nowMs){
        return shouldAccept(code,nowMs,DUPLICATE_WINDOW_MS);
    }
    synchronized boolean shouldAccept(String code,long nowMs,long configuredWindowMs){
        if(code==null || code.isEmpty()) return false;
        // No modo nativo a janela acompanha o bloqueio definido pelo usuario.
        // Conserva pelo menos 35 ms para ecos identicos do mesmo broadcast.
        long windowMs=Math.max(35L,configuredWindowMs);
        Iterator<Map.Entry<String,Long>> it=recent.entrySet().iterator();
        while(it.hasNext()){
            long age=nowMs-it.next().getValue();
            if(age<0 || age>=windowMs) it.remove();
        }
        if(recent.containsKey(code)) return false;
        recent.put(code,nowMs);
        return true;
    }
}
