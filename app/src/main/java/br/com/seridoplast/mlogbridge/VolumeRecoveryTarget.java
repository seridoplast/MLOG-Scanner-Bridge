package br.com.seridoplast.mlogbridge;

/**
 * Alvo experimental do botao Volume na Conferencia de Venda (print 720x1440).
 * Nao presumir o mesmo icone em outras telas do MLOG.
 * Sem dependencias Android para permitir testes no CI.
 */
final class VolumeRecoveryTarget {
    static final int REFERENCE_WIDTH=720;
    static final int REFERENCE_HEIGHT=1440;
    static final float X_RATIO=648f/REFERENCE_WIDTH;
    static final float Y_RATIO=175f/REFERENCE_HEIGHT;
    // Sequencia testada no coletor: Volume -> 10 ms -> seta de voltar do MLOG.
    // Coordenadas homologadas somente em 720x1440.
    static final int BACK_X=55;
    static final int BACK_Y=103;
    static final int PAUSE_MS=10;

    private VolumeRecoveryTarget(){}

    static boolean isConference(String screen,boolean hasPageTitle,boolean hasProductAction){
        return "conferencia_produto".equals(screen) && hasPageTitle && hasProductAction;
    }

    static boolean isReferenceSize(int width,int height){
        return width==REFERENCE_WIDTH && height==REFERENCE_HEIGHT;
    }

    static boolean nearHeaderVolume(int centerX,int centerY,int width,int height){
        if(width<=0 || height<=0)return false;
        float x=centerX/(float)width, y=centerY/(float)height;
        return x>=.83f && x<=.98f && y>=.075f && y<=.18f;
    }

    static int x(int width){return Math.round(width*X_RATIO);}
    static int y(int height){return Math.round(height*Y_RATIO);}

    /** Comando fechado, construido somente com inteiros e faixas validados. */
    static String shellSequence(int x,int y,int pauseMs){
        if(x<1 || y<1 || x>4096 || y>4096 || pauseMs<1 || pauseMs>99)
            throw new IllegalArgumentException("Alvo/pausa fora da faixa do piloto");
        // O KEYEVENT 4 eh consumido pelo teclado aberto na tela de destino.
        // Um toque direto na seta do MLOG nao depende do estado do IME.
        return "/system/bin/input tap "+x+" "+y+
            " && /system/bin/sleep 0."+
            String.format(java.util.Locale.ROOT,"%03d",pauseMs)+
            " && /system/bin/input tap "+BACK_X+" "+BACK_Y;
    }
}
