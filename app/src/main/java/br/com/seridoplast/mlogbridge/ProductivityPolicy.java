package br.com.seridoplast.mlogbridge;

/** Conservative classification by the MLOG screen at the physical scan, not EAN length. */
final class ProductivityPolicy {
    private ProductivityPolicy(){}
    static String kind(String screen,boolean blocked){
        if(blocked || screen==null)return "";
        switch(screen){
            case "notas": case "pedidos": case "conferencia_venda": return "note";
            case "separacao": case "conferencia_produto": case "conferencia_inicio":
            case "conferencia_entrada_produto": return "product";
            default:return "";
        }
    }
}
