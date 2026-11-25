package br.com.seridoplast.mlogbridge;
public final class ProductivityPolicyTest {
    private static void eq(String s,boolean blocked,String expected){if(!expected.equals(ProductivityPolicy.kind(s,blocked)))throw new AssertionError(s+" -> "+ProductivityPolicy.kind(s,blocked));}
    public static void main(String[] args){
        for(String s:new String[]{"notas","pedidos","conferencia_venda"})eq(s,false,"note");
        for(String s:new String[]{"separacao","conferencia_produto","conferencia_inicio","conferencia_entrada_produto"})eq(s,false,"product");
        for(String s:new String[]{"consulta","atividades","desconhecida",null})eq(s,false,"");
        eq("separacao",true,"");eq("notas",true,"");System.out.println("ProductivityPolicyTest OK");
    }
}
