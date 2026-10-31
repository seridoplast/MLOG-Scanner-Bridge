package br.com.seridoplast.mlogbridge;
public final class VolumeRecoveryTargetTest {
    private static void yes(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        yes(VolumeRecoveryTarget.isConference("conferencia_produto",true,true));
        yes(!VolumeRecoveryTarget.isConference("separacao",true,true));
        yes(!VolumeRecoveryTarget.isConference("conferencia_inicio",true,true));
        yes(!VolumeRecoveryTarget.isConference("conferencia_produto",false,true));
        yes(!VolumeRecoveryTarget.isConference("conferencia_produto",true,false));
        yes(VolumeRecoveryTarget.isReferenceSize(720,1440));
        yes(!VolumeRecoveryTarget.isReferenceSize(1080,1920));
        yes(VolumeRecoveryTarget.x(720)==648 && VolumeRecoveryTarget.y(1440)==175);
        yes(VolumeRecoveryTarget.nearHeaderVolume(648,175,720,1440));
        yes(!VolumeRecoveryTarget.nearHeaderVolume(490,810,720,1440));
        yes(!VolumeRecoveryTarget.nearHeaderVolume(650,620,720,1440));
        yes(VolumeRecoveryTarget.PAUSE_MS>0 && VolumeRecoveryTarget.PAUSE_MS<100);
        yes(VolumeRecoveryTarget.shellSequence(648,175,80).equals(
            "/system/bin/input tap 648 175 && /system/bin/sleep 0.080 && /system/bin/input keyevent 4"));
        boolean invalid=false;
        try{VolumeRecoveryTarget.shellSequence(-1,175,80);}catch(IllegalArgumentException e){invalid=true;}
        yes(invalid);
        System.out.println("VolumeRecoveryTargetTest OK");
    }
}
