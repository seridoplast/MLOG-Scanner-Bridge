package br.com.seridoplast.mlogbridge;
public final class VolumeRecoveryTargetTest {
    private static void yes(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        yes(VolumeRecoveryTarget.hasVerifiedVolumeHeader(true,true));
        yes(!VolumeRecoveryTarget.hasVerifiedVolumeHeader(false,true));
        yes(!VolumeRecoveryTarget.hasVerifiedVolumeHeader(true,false));
        yes(VolumeRecoveryTarget.isConference("conferencia_produto",true,true));
        yes(!VolumeRecoveryTarget.isConference("separacao",true,true));
        // Diagnostico real: tela Separacao, rotulo Volume, botao (616,140)-(664,188).
        yes(VolumeRecoveryTarget.isVerifiedHeaderButton(616,140,664,188));
        yes(VolumeRecoveryTarget.isVerifiedHeaderLabel(513,170,600,198));
        yes(!VolumeRecoveryTarget.isVerifiedHeaderLabel(30,540,600,620));
        yes(!VolumeRecoveryTarget.isVerifiedHeaderLabel(20,155,160,198));
        yes(VolumeRecoveryTarget.isSeparation("separacao",true,true,true,true));
        yes(!VolumeRecoveryTarget.isSeparation("separacao",false,true,true,true));
        yes(!VolumeRecoveryTarget.isSeparation("separacao",true,false,true,true));
        yes(!VolumeRecoveryTarget.isSeparation("separacao",true,true,false,true));
        yes(!VolumeRecoveryTarget.isSeparation("separacao",true,true,true,false));
        yes(!VolumeRecoveryTarget.isSeparation("atividades",true,true,true,true));
        yes(!VolumeRecoveryTarget.isVerifiedHeaderButton(558,268,654,364));
        yes(!VolumeRecoveryTarget.isVerifiedHeaderButton(616,550,664,598));
        yes(!VolumeRecoveryTarget.isConference("conferencia_inicio",true,true));
        yes(!VolumeRecoveryTarget.isConference("conferencia_produto",false,true));
        yes(!VolumeRecoveryTarget.isConference("conferencia_produto",true,false));
        yes(VolumeRecoveryTarget.isReferenceSize(720,1440));
        yes(!VolumeRecoveryTarget.isReferenceSize(1080,1920));
        yes(VolumeRecoveryTarget.x(720)==648 && VolumeRecoveryTarget.y(1440)==175);
        yes(VolumeRecoveryTarget.nearHeaderVolume(648,175,720,1440));
        yes(!VolumeRecoveryTarget.nearHeaderVolume(490,810,720,1440));
        yes(!VolumeRecoveryTarget.nearHeaderVolume(650,620,720,1440));
        yes(VolumeRecoveryTarget.PAUSE_MS==10);
        yes(VolumeRecoveryTarget.BACK_X==55 && VolumeRecoveryTarget.BACK_Y==103);
        yes(VolumeRecoveryTarget.shellSequence(648,175,10).equals(
            "/system/bin/input tap 648 175 && /system/bin/sleep 0.010 && /system/bin/input tap 55 103"));
        boolean invalid=false;
        try{VolumeRecoveryTarget.shellSequence(-1,175,80);}catch(IllegalArgumentException e){invalid=true;}
        yes(invalid);
        invalid=false;
        try{VolumeRecoveryTarget.shellSequence(648,175,0);}catch(IllegalArgumentException e){invalid=true;}
        yes(invalid);
        System.out.println("VolumeRecoveryTargetTest OK");
    }
}
