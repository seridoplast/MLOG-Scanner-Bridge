package br.com.seridoplast.mlogbridge;
final class SeparationVisualReceiptTest {
    static void eq(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        String a="6342 - BOBINA DE SACO\nSEPARAR\n10 KG\n0 KG\nSEPARADO";
        String b="6342 - BOBINA DE SACO\nSEPARAR\n10 KG\n1 KG\nSEPARADO";
        SeparationVisualReceipt old=SeparationVisualReceipt.parse(a), now=SeparationVisualReceipt.parse(b);
        eq(old!=null && old.separated==0,"parse kg");
        eq(old.increased(now),"visual kg increase");
        eq(!now.increased(old),"no count regression");
        String c="19649 - PIRULITO\nSEPARAR\n30 PC\n0 CX\nSEPARADO\n0 PC\nFRACIONADO";
        String d="19649 - PIRULITO\nSEPARAR\n30 PC\n0 CX\nSEPARADO\n1 PC\nFRACIONADO";
        eq(SeparationVisualReceipt.parse(c).increased(SeparationVisualReceipt.parse(d)),"fractionated PC increment");
        String other="99999 - PIRULITO\nSEPARAR\n30 PC\n1 CX\nSEPARADO";
        eq(!SeparationVisualReceipt.parse(c).increased(SeparationVisualReceipt.parse(other)),"different product must not confirm");
        eq(SeparationVisualReceipt.parse("198 - OTHER, 10KG") == null,"irrelevant text");
        System.out.println("SeparationVisualReceiptTest OK");
    }
}
