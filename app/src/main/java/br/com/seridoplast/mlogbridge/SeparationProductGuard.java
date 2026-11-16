package br.com.seridoplast.mlogbridge;

/** Nao enviar codigo da etiqueta anterior apos o MLOG mudar de produto.
 * Observa somente o cartao VISIVEL; nao guarda historico de produtos.
 */
final class SeparationProductGuard {
    private String lastProduct;
    private String lastCode;
    boolean rejects(String product, String code) {
        return product!=null && lastProduct!=null && !product.equals(lastProduct)
            && code!=null && code.equals(lastCode);
    }
    void sent(String product, String code) {
        if(product==null || code==null)return;
        lastProduct=product;
        lastCode=code;
    }
    void clear(){lastProduct=null;lastCode=null;}
}
