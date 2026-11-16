package br.com.seridoplast.mlogbridge;
public final class VisibleOrderHeaderPolicyTest {
    public static void main(String[] args){
        check("136122".equals(VisibleOrderHeaderPolicy.parse("136122")),"header literal");
        check("000136122".equals(VisibleOrderHeaderPolicy.parse("Pedido: 000136122")),"leading zeros");
        check("136122".equals(VisibleOrderHeaderPolicy.parse("Conf. de Entrada 136122")),"combined header");
        check(VisibleOrderHeaderPolicy.isRelevantTitle("Conf. de Entrada"),"screen title");
        check(!VisibleOrderHeaderPolicy.isRelevantTitle("Consulta de Produtos"),"screen not mapped");
        check(VisibleOrderHeaderPolicy.isHeaderPosition(430,56,600,160,720,1440),"header coordinates");
        check(!VisibleOrderHeaderPolicy.isHeaderPosition(430,500,600,640,720,1440),"no product row");
        check(VisibleOrderHeaderPolicy.parse("7890000000105")==null,"reject EAN13");
        check(VisibleOrderHeaderPolicy.parse("10 UN")==null,"reject quantity");
        check(VisibleOrderHeaderPolicy.parse("16:22")==null,"reject time");
        check(VisibleOrderHeaderPolicy.parse("123")==null,"reject too short");
        System.out.println("VisibleOrderHeaderPolicyTest OK");
    }
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
