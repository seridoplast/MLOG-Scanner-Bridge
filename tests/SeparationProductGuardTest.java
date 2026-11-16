package br.com.seridoplast.mlogbridge;
public class SeparationProductGuardTest {
    private static void ok(boolean yes){if(!yes)throw new AssertionError();}
    public static void main(String[] args){
        SeparationProductGuard guard=new SeparationProductGuard();
        ok(!guard.rejects("19649","7896707342948"));
        guard.sent("19649","7896707342948");
        ok(!guard.rejects("19649","7896707342948"));
        ok(guard.rejects("4235","7896707342948"));
        ok(!guard.rejects("4235","7890000000000"));
        guard.sent("4235","7890000000000");
        ok(!guard.rejects("4235","7890000000000"));
    }
}
