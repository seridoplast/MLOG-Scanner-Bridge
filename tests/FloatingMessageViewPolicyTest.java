package br.com.seridoplast.mlogbridge;
public final class FloatingMessageViewPolicyTest {
    public static void main(String[] args){
        int O=FloatingMessageViewPolicy.OBSERVATION;
        int A=FloatingMessageViewPolicy.GENERAL_NOTICE;
        int C=FloatingMessageViewPolicy.COMPLETION;
        check(FloatingMessageViewPolicy.select(O,2,0,0)==O,"observation");
        check(FloatingMessageViewPolicy.select(A,2,1,0)==A,"new general notice");
        check(FloatingMessageViewPolicy.select(O,0,1,0)==A,"notice fallback");
        check(FloatingMessageViewPolicy.select(A,1,0,1)==O,"note fallback");
        check(FloatingMessageViewPolicy.select(C,1,1,1)==C,"explicit completion");
        check(FloatingMessageViewPolicy.select(O,0,0,1)==C,"pending after order changes");
        check(FloatingMessageViewPolicy.select(O,0,0,0)==FloatingMessageViewPolicy.EMPTY,"empty state");
        System.out.println("FloatingMessageViewPolicyTest OK");
    }
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
