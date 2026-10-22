package br.com.seridoplast.mlogbridge;
public final class ScanCycleGateTest {
    static void must(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    public static void main(String[] args){
        ScanCycleGate g=new ScanCycleGate();
        must(g.beginTrigger(1000),"primeiro gatilho");
        must(!g.beginTrigger(1080),"nao aceitar segundo toque durante abertura");
        must(g.claimClick(),"primeiro clique");
        must(!g.claimClick(),"bloquear segundo clique por ciclo");
        must(g.beginBarcode(1120),"associar primeiro codigo ao gatilho");
        must(!g.beginBarcode(1200),"nao aceitar outro codigo durante ciclo");
        g.waitingConfirmation();
        must(!g.beginTrigger(1400),"sem clique antes da confirmacao");
        g.completed(1530);
        must(g.isIdle(),"liberar apos confirmacao");
        must(g.beginTrigger(1600),"permitir proxima leitura");
        must(g.claimClick(),"novo ciclo pode clicar");
        must(g.beginBarcode(1680),"receber segundo ciclo");
        g.needsAttention();
        must(!g.beginTrigger(4000),"falha nunca enfileira toque atrasado");
        g.aborted(4100);
        must(g.beginBarcode(4200),"sem evento fisico pode iniciar via broadcast");
        must(g.claimClick(),"primeiro clique apenas no broadcast");
        must(!g.claimClick(),"nada duplicado");
        System.out.println("ScanCycleGateTest OK");
    }
}
