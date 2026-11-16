package br.com.seridoplast.mlogbridge;

import java.util.ArrayDeque;

/** Fila limitada em memoria. Nunca conserva codigos no disco. */
final class ScanQueue {
    private final ArrayDeque<String> waiting=new ArrayDeque<>();
    private final int capacity;
    private String current;
    ScanQueue(int capacity){if(capacity<1)throw new IllegalArgumentException();this.capacity=capacity;}
    boolean isIdle(){return current==null;}
    String active(){return current;}
    int waitingCount(){return waiting.size();}
    boolean offer(String value){
        if(value==null || value.isEmpty())return false;
        if(current==null){current=value;return true;}
        if(waiting.size()>=capacity)return false;
        waiting.addLast(value);
        return true;
    }
    /** Retorna true quando outra leitura esta pronta para iniciar. */
    boolean complete(){current=waiting.pollFirst();return current!=null;}
    void clear(){current=null;waiting.clear();}
}
