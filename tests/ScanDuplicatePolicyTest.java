package br.com.seridoplast.mlogbridge;

public final class ScanDuplicatePolicyTest {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        ScanDuplicatePolicy p=new ScanDuplicatePolicy();
        check(p.shouldAccept("A",1000),"Primeira leitura");
        for(int ms:new int[]{69,70,72,78,84,139})
            check(!p.shouldAccept("A",1000+ms),"Broadcast repetido "+ms);
        check(p.shouldAccept("A",1140),"Limite 140 ms");
        check(p.shouldAccept("A",1332),"Repeticao legitima 192ms");
        check(p.shouldAccept("B",1340),"Codigo distinto apos 8ms");
        check(!p.shouldAccept("B",1415),"Broadcast B repetido apos 75ms");
        check(p.shouldAccept("B",1532),"Nova leitura B apos 192ms");
        // 1000 leituras fisicas a 192ms: nenhuma deve desaparecer.
        ScanDuplicatePolicy fast=new ScanDuplicatePolicy();
        for(int i=0;i<1000;i++){
            long t=5000L+i*192L;
            check(fast.shouldAccept("MESMO_CODIGO",t),"Leitura fisica "+i);
            check(!fast.shouldAccept("MESMO_CODIGO",t+75),"Eco "+i);
        }
        System.out.println("OK: 1000 leituras, sem ecos duplicados; dois codigos distintos preservados.");
    }
}
