package br.com.seridoplast.mlogbridge;

final class ScanQueueTest {
    private static void ok(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        ScanQueue q=new ScanQueue(2);
        ok(q.isIdle(),"initial empty");
        ok(q.offer("123"),"first accepted");
        ok("123".equals(q.active()),"active first");
        ok(q.offer("456"),"second queued");
        ok(q.offer("789"),"third queued");
        ok(!q.offer("999"),"queue overflow rejected explicitly");
        ok(q.waitingCount()==2,"two waiting");
        ok(q.complete(),"next active");
        ok("456".equals(q.active()),"FIFO second");
        ok(q.complete(),"next active again");
        ok("789".equals(q.active()),"FIFO third");
        ok(!q.complete(),"empty after third");
        ok(q.isIdle(),"idle after complete");
        ok(q.offer("repeat") && q.offer("repeat"),"repeated scans are distinct");
        ok(q.complete() && "repeat".equals(q.active()),"repeat preserved");
        q.clear(); ok(q.isIdle() && q.waitingCount()==0,"clear");
        // Limite simultaneo nao e limite total de leituras por sessao.
        ScanQueue session=new ScanQueue(128);
        for(int i=0;i<10000;i++){
            ok(session.offer("codigo"+i),"continuous offer "+i);
            ok(!session.complete(),"continuous complete "+i);
        }
        // Rajada de 129 leituras: uma ativa, 128 aguardando.
        ok(session.offer("start"),"burst active");
        for(int i=0;i<128;i++)ok(session.offer("wait"+i),"burst wait");
        ok(!session.offer("overflow"),"backpressure explicit");
        for(int i=0;i<128;i++)ok(session.complete(),"burst drain");
        ok(!session.complete() && session.isIdle(),"burst drained");
        System.out.println("ScanQueueTest OK");
    }
}
