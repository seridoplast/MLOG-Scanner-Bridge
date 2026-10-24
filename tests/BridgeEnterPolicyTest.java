package br.com.seridoplast.mlogbridge;
public final class BridgeEnterPolicyTest {
    private static int tests=0;
    private static void ok(boolean result,String msg){tests++;if(!result)throw new AssertionError(msg);}
    public static void main(String[] args){
        String c=ConferenceScreenPolicy.ENTRY_PRODUCT;
        ok(!BridgeEnterPolicy.shouldAttempt(false,c,250,false,true,true),"opt-in");
        ok(!BridgeEnterPolicy.shouldAttempt(true,c,40,false,true,true),"wait for IME");
        ok(BridgeEnterPolicy.shouldAttempt(true,c,180,false,true,true),"entry supported");
        ok(!BridgeEnterPolicy.shouldAttempt(true,c,200,true,true,true),"CMX observed");
        ok(!BridgeEnterPolicy.shouldAttempt(true,c,200,false,false,true),"focus required");
        ok(!BridgeEnterPolicy.shouldAttempt(true,c,200,false,true,false),"screen verified");
        ok(BridgeEnterPolicy.shouldAttempt(true,"separacao",200,false,true,true),"separation verified direct");
        ok(!BridgeEnterPolicy.shouldAttempt(true,"separacao",200,false,true,false),"separation not verified");
        ok(ConferenceScreenPolicy.isMapped(c),"entry recognized");
        ok(ConferenceScreenPolicy.isStateVerified(c,true,false,true),"entry title + bipar");
        ok(!ConferenceScreenPolicy.isStateVerified(c,false,false,true),"entry no title");
        ok(Math.abs(ConferenceScreenPolicy.keyboardX(c)*720-411.84)<1,"entry keyboard x");
        ok(Math.abs(ConferenceScreenPolicy.keyboardY(c)*1440-999.36)<1,"entry keyboard y");
        ok(ConferenceScreenPolicy.rightButtonCandidate(c,.572f,.694f,.13f,.05f),"keyboard candidate");
        ok(ConferenceScreenPolicy.leftCameraCandidate(c,.40f,.694f,.13f,.05f),"camera candidate");
        ok(!ConferenceScreenPolicy.rightButtonCandidate(c,.85f,.694f,.13f,.05f),"not quantity");
        System.out.println("OK BridgeEnterPolicyTest: "+tests+" checks");
    }
}
