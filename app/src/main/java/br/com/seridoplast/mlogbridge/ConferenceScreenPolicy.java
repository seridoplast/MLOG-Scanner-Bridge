package br.com.seridoplast.mlogbridge;

/** Reconhecimento conservador de dois layouts da Conferência de Venda MLOG.
 * As coordenadas vêm de dois prints reais 720x1440 do mesmo coletor.
 * Sem título e marcador explícito, não autorizar toque por posição. */
final class ConferenceScreenPolicy {
    private ConferenceScreenPolicy(){}
    static final String INITIAL="conferencia_inicio";
    static final String PRODUCT="conferencia_produto";
    static final float KEYBOARD_X=0.681f;
    static boolean isMapped(String screen){return INITIAL.equals(screen)||PRODUCT.equals(screen);}
    static boolean isStateVerified(String screen,boolean title,boolean prompt,boolean action){
        return title && (INITIAL.equals(screen) && prompt && !action ||
                         PRODUCT.equals(screen) && action);
    }
    static float keyboardY(String screen){
        if(INITIAL.equals(screen))return 0.469f;
        if(PRODUCT.equals(screen))return 0.564f;
        throw new IllegalArgumentException("Layout de conferência não homologado: "+screen);
    }
    static boolean rightButtonCandidate(String screen,float x,float y,float width,float height){
        if(!isMapped(screen))return false;
        return x>=0.56f && x<=0.79f && Math.abs(y-keyboardY(screen))<.045f &&
               width>=.045f && width<=.25f && height>=.020f && height<=.095f;
    }
    static boolean leftCameraCandidate(String screen,float x,float y,float width,float height){
        if(!isMapped(screen))return false;
        return x>=.20f && x<=.43f && Math.abs(y-keyboardY(screen))<.045f &&
               width>=.045f && width<=.25f && height>=.020f && height<=.095f;
    }
}
