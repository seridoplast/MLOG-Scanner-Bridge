package br.com.seridoplast.mlogbridge;

/** As duas coordenadas sao relativas aos screenshots 720x1440 enviados. */
public final class ConferenceScreenPolicyTest {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        ok(ConferenceScreenPolicy.isMapped("conferencia_inicio"),"initial screen recognized");
        ok(ConferenceScreenPolicy.isMapped("conferencia_produto"),"product screen recognized");
        ok(!ConferenceScreenPolicy.isMapped("conferencia_venda"),"search screen must remain separate");
        ok(!ConferenceScreenPolicy.isMapped("separacao"),"other screens unaffected");
        ok(ConferenceScreenPolicy.isStateVerified("conferencia_inicio",true,true,false),"initial requires prompt");
        ok(!ConferenceScreenPolicy.isStateVerified("conferencia_inicio",true,false,false),"no fallback without prompt");
        ok(!ConferenceScreenPolicy.isStateVerified("conferencia_inicio",false,true,false),"no fallback without title");
        ok(!ConferenceScreenPolicy.isStateVerified("conferencia_inicio",true,true,true),"product state must not use initial fallback");
        ok(ConferenceScreenPolicy.isStateVerified("conferencia_produto",true,false,true),"product requires action");
        ok(!ConferenceScreenPolicy.isStateVerified("conferencia_produto",true,false,false),"no product action means no fallback");
        ok(Math.abs(ConferenceScreenPolicy.KEYBOARD_X*720-490.32f)<1,"keyboard x from screenshot");
        ok(Math.abs(ConferenceScreenPolicy.keyboardY("conferencia_inicio")*1440-675.36f)<1,"first keyboard y");
        ok(Math.abs(ConferenceScreenPolicy.keyboardY("conferencia_produto")*1440-812.16f)<1,"second keyboard y");
        ok(ConferenceScreenPolicy.rightButtonCandidate("conferencia_inicio",.68f,.469f,.17f,.052f),"right input candidate");
        ok(ConferenceScreenPolicy.leftCameraCandidate("conferencia_inicio",.32f,.469f,.17f,.052f),"left camera candidate");
        ok(!ConferenceScreenPolicy.rightButtonCandidate("conferencia_produto",.68f,.469f,.17f,.052f),"wrong screen y blocked");
        ok(!ConferenceScreenPolicy.rightButtonCandidate("conferencia_inicio",.45f,.469f,.17f,.052f),"center is not input button");
        ok(!ConferenceScreenPolicy.rightButtonCandidate("conferencia_inicio",.68f,.469f,.80f,.052f),"full panel not a button");
        System.out.println("OK ConferenceScreenPolicyTest: "+checks+" checks");
    }
}
