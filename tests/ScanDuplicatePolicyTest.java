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
        ScanDuplicatePolicy nativePolicy=new ScanDuplicatePolicy();
        check(nativePolicy.shouldAccept("1234567890123",1000,100),"primeiro bip modo nativo");
        check(!nativePolicy.shouldAccept("1234567890123",1060,100),"eco abaixo de 100ms");
        check(nativePolicy.shouldAccept("1234567890123",1110,100),"bipe valido apos 100ms");
        ScanDuplicatePolicy fastNative=new ScanDuplicatePolicy();
        check(fastNative.shouldAccept("ABC",2000,0),"primeiro bipe zero");
        check(!fastNative.shouldAccept("ABC",2020,0),"eco min 35ms");
        check(fastNative.shouldAccept("ABC",2040,0),"repeticao com zero apos piso");
        System.out.println("OK: 1000 leituras, sem ecos duplicados; dois codigos distintos preservados.");
    }
}
