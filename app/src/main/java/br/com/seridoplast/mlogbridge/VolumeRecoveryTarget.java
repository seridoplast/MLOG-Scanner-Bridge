package br.com.seridoplast.mlogbridge;

/**
 * Alvo experimental do botao Volume de telas operacionais do MLOG.
 * O diagnostico XML 31/10 confirmou o botao [616,140][664,188]
 * no cabecalho da Separacao 720x1440. Nada de coordenadas em outras telas.
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

    /** Reconhecer cabecalho por elementos reais, nao por nome da rota Flutter. */
    static boolean hasVerifiedVolumeHeader(boolean volumeLabel,boolean headerButton){
        return volumeLabel && headerButton;
    }

    static boolean isConference(String screen,boolean hasPageTitle,boolean hasProductAction){
        return "conferencia_produto".equals(screen) && hasPageTitle && hasProductAction;
    }

    /** A tela Separacao so e permitida com 4 sinais independentes.
     * Nao aceitar telas de atividade, dialogos ou outras janelas. */
    static boolean isSeparation(String screen,boolean hasHeader,
        boolean hasControls,boolean hasVolumeLabel,boolean hasHeaderButton){
        return "separacao".equals(screen) && hasHeader && hasControls &&
            hasVolumeLabel && hasHeaderButton;
    }

    /** A palavra Volume deve estar junto ao botao do cabecalho, e nao
     * numa descricao de produto ou num card/modal no restante da pagina. */
    static boolean isVerifiedHeaderLabel(int left,int top,int right,int bottom){
        return left>=440 && left<630 && top>=100 && top<=220 &&
            right>left && right<=675 && bottom>top && bottom<=235 &&
            right-left<=220 && bottom-top<=85;
    }

    /** O toque homologado precisa cair num botao pequeno do cabecalho.
     * Valores observados no dump XML: [616,140][664,188]. */
    static boolean isVerifiedHeaderButton(int left,int top,int right,int bottom){
        int width=right-left, height=bottom-top;
        return width>=24 && width<=110 && height>=24 && height<=110 &&
            left<=x(REFERENCE_WIDTH) && right>x(REFERENCE_WIDTH) &&
            top<=y(REFERENCE_HEIGHT) && bottom>y(REFERENCE_HEIGHT) &&
            nearHeaderVolume((left+right)/2,(top+bottom)/2,
                REFERENCE_WIDTH,REFERENCE_HEIGHT);
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
