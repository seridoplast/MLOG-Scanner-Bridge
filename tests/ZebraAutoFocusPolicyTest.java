package br.com.seridoplast.mlogbridge;
public final class ZebraAutoFocusPolicyTest {
    private static void ok(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args){
        ok(ZebraAutoFocusPolicy.isEligible("separacao",false,false,false),"separacao");
        ok(ZebraAutoFocusPolicy.isEligible("conferencia_venda",false,false,false),"conferencia");
        ok(!ZebraAutoFocusPolicy.isEligible("desconhecida",false,false,false),"unknown blocked");
        ok(!ZebraAutoFocusPolicy.isEligible("separacao",true,false,false),"login blocked");
        ok(!ZebraAutoFocusPolicy.isEligible("separacao",false,true,false),"dialog blocked");
        ok(!ZebraAutoFocusPolicy.isEligible("separacao",false,false,true),"overlay blocked");
        ZebraAutoFocusPolicy p=new ZebraAutoFocusPolicy();
        ok(p.shouldAttempt("separacao",1000),"first attempt");
        ok(!p.shouldAttempt("separacao",1100),"throttle");
        ok(p.shouldAttempt("separacao",1650),"second");
        ok(p.shouldAttempt("separacao",2300),"third");
        ok(p.shouldAttempt("separacao",2950),"fourth");
        ok(!p.shouldAttempt("separacao",3650),"bounded attempts");
        ok(p.shouldAttempt("separacao",12000),"rearm after timeout");
        ok(p.shouldAttempt("conferencia_venda",12001),"new screen reset");
        p.markReady();
        ok(p.shouldAttempt("conferencia_venda",12005),"editor re-armed");
        System.out.println("ZebraAutoFocusPolicyTest OK");
    }
}
