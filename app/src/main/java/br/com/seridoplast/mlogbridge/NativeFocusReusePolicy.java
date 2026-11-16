package br.com.seridoplast.mlogbridge;

/**
 * Reuso defensivo de foco: jamais aproveitar um campo de outro ciclo/tela,
 * nem texto ainda presente. A validação de node.refresh() e do pacote
 * Android permanece obrigatória no serviço de acessibilidade.
 */
final class NativeFocusReusePolicy {
    private NativeFocusReusePolicy() {}
    static boolean withinWindow(long now,long capturedAt) {
        if(capturedAt<=0 || now<capturedAt)return false;
        long age=now-capturedAt;
        return age>=20L && age<=2000L;
    }
    static boolean editorReusable(boolean sameWindow,boolean focused,
                                   boolean visible,boolean editable,
                                   boolean enabled,boolean password,
                                   boolean forbiddenLabel,boolean empty) {
        return sameWindow && focused && visible && editable && enabled &&
            !password && !forbiddenLabel && empty;
    }
}
