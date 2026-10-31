package br.com.seridoplast.mlogbridge;

public final class CameraRecoveryPolicyTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        CameraRecoveryPolicy p=new CameraRecoveryPolicy();
        check(!p.observeKeyboard(false,1000),"initial hidden");
        check(!p.observeKeyboard(true,2000),"opening does not recover");
        check(!p.observeKeyboard(false,2200),"keyboard blink ignored");
        check(!p.observeKeyboard(true,3000),"opening");
        check(p.observeKeyboard(false,3400),"real close recognized");
        check(p.begin(3900,false),"first automatic recovery");
        check(p.isRecovering(),"running");
        check(!p.begin(4000,true),"manual cannot overlap");
        p.finish();
        check(!p.begin(4500,false),"cooldown blocks loop");
        check(p.begin(4500,true),"manual override cooldown");
        p.finish();
        check(!p.observeKeyboard(true,4800),"open during normal operation");
        check(p.keyboardVisible(),"visible");
        check(!p.begin(5000,true),"do not open camera over keyboard");
        p.resetKeyboard();
        check(!p.keyboardVisible(),"reset");
        check(p.begin(12000,false),"cooldown expired");
        p.finish();
        System.out.println("CameraRecoveryPolicyTest OK");
    }
}
