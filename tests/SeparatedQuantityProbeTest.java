package br.com.seridoplast.mlogbridge;

public final class SeparatedQuantityProbeTest {
    public static void main(String[] args){
        String before="258 - IANE KI QUEIJO 80G - PC C/10\nV: -\nSEPARAR\n10 PC\n4 PC\nSEPARADO";
        String after="258 - IANE KI QUEIJO 80G - PC C/10\nV: -\nSEPARAR\n10 PC\n5 PC\nSEPARADO";
        SeparatedQuantityProbe.Result a=SeparatedQuantityProbe.parse(before);
        SeparatedQuantityProbe.Result b=SeparatedQuantityProbe.parse(after);
        if(a==null||b==null||a.count!=4||b.count!=5||!a.sameProduct(b))throw new AssertionError("cannot identify increment");
        if(SeparatedQuantityProbe.parse("um produto aleatório")==null){}else throw new AssertionError("false match");
        String other="423 - BANDEJA PC\nSEPARAR 10 PC\n0 PC\nSEPARADO";
        if(a.sameProduct(SeparatedQuantityProbe.parse(other)))throw new AssertionError("product swap not recognized");
        if(SeparatedQuantityProbe.parse("6342 - KG BOBINA\nSEPARAR 5 KG\n0 KG SEPARADO")!=null)throw new AssertionError("kg must not be interpreted as a piece");
        System.out.println("OK SeparatedQuantityProbeTest");
    }
}
