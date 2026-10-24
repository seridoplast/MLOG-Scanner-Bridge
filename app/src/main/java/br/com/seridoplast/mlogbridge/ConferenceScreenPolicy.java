package br.com.seridoplast.mlogbridge;

/** Tela de conferencia identificada antes de tocar na interface Flutter.
 * Coordenadas estimadas das capturas do MESMO coletor 720x1440; sempre
 * preferir no Android o no semantico do botao e a verificacao do par camera.
 * Para novos tamanhos de tela, nao fazer gesto por posicao. */
final class ConferenceScreenPolicy {
    private ConferenceScreenPolicy(){}
    static final String INITIAL="conferencia_inicio";
    static final String PRODUCT="conferencia_produto";
    static final String ENTRY_PRODUCT="conferencia_entrada_produto";
    static final float KEYBOARD_X=.681f; // venda, mantido para retrocompatibilidade
    static boolean isMapped(String screen){
        return INITIAL.equals(screen)||PRODUCT.equals(screen)||ENTRY_PRODUCT.equals(screen);
    }
    static boolean isStateVerified(String screen,boolean title,boolean prompt,boolean action){
        return title && (INITIAL.equals(screen) && prompt && !action ||
                         PRODUCT.equals(screen) && action ||
                         ENTRY_PRODUCT.equals(screen) && action);
    }
    static float keyboardX(String screen){
        if(ENTRY_PRODUCT.equals(screen))return .572f;
        if(INITIAL.equals(screen)||PRODUCT.equals(screen))return KEYBOARD_X;
        throw new IllegalArgumentException("Tela nao mapeada: "+screen);
    }
    static float keyboardY(String screen){
        if(INITIAL.equals(screen))return .469f;
        if(PRODUCT.equals(screen))return .564f;
        if(ENTRY_PRODUCT.equals(screen))return .694f;
        throw new IllegalArgumentException("Tela nao mapeada: "+screen);
    }
    static boolean rightButtonCandidate(String screen,float x,float y,float width,float height){
        if(!isMapped(screen))return false;
        float low=ENTRY_PRODUCT.equals(screen)?.49f:.56f;
        float high=ENTRY_PRODUCT.equals(screen)?.69f:.79f;
        return x>=low && x<=high && Math.abs(y-keyboardY(screen))<.047f &&
               width>=.045f && width<=.25f && height>=.020f && height<=.095f;
    }
    static boolean leftCameraCandidate(String screen,float x,float y,float width,float height){
        if(!isMapped(screen))return false;
        float low=ENTRY_PRODUCT.equals(screen)?.28f:.20f;
        float high=ENTRY_PRODUCT.equals(screen)?.48f:.43f;
        return x>=low && x<=high && Math.abs(y-keyboardY(screen))<.047f &&
               width>=.045f && width<=.25f && height>=.020f && height<=.095f;
    }
}
