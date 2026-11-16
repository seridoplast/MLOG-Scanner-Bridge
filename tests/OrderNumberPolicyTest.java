package br.com.seridoplast.mlogbridge;
public final class OrderNumberPolicyTest {
    public static void main(String[] args){
        check("00012345".equals(OrderNumberPolicy.normalize(" 00012345\r\n")),"zeros");
        check("AB-100/1".equals(OrderNumberPolicy.normalize("AB-100/1")),"hifen");
        check(OrderNumberPolicy.normalize("1\u00002") == null,"nul");
        check(OrderNumberPolicy.normalize("123 45") == null,"espaco interno");
        check(OrderNumberPolicy.isOrderScreen("pedidos"),"pedido");
        check(!OrderNumberPolicy.isOrderScreen("separacao"),"produto");
        check(!OrderNumberPolicy.isOrderScreen("atividades"),"atividade");
        System.out.println("OrderNumberPolicyTest OK");
    }
    static void check(boolean ok,String description){if(!ok)throw new AssertionError(description);}
}
