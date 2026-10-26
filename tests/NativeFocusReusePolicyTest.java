package br.com.seridoplast.mlogbridge;

public final class NativeFocusReusePolicyTest {
    private static int checked;
    static void expect(boolean value,boolean expected){
        if(value!=expected)throw new AssertionError("Expected "+expected+" but got "+value);
        checked++;
    }
    public static void main(String[] args){
        expect(NativeFocusReusePolicy.withinWindow(1030,1000),true);
        expect(NativeFocusReusePolicy.withinWindow(1020,1000),true);
        expect(NativeFocusReusePolicy.withinWindow(1019,1000),false);
        expect(NativeFocusReusePolicy.withinWindow(3001,1000),false);
        expect(NativeFocusReusePolicy.withinWindow(999,1000),false);
        expect(NativeFocusReusePolicy.withinWindow(2000,0),false);
        expect(NativeFocusReusePolicy.editorReusable(true,true,true,true,true,false,false,true),true);
        for(int i=0;i<8;i++){
            boolean[] x={true,true,true,true,true,false,false,true};
            x[i]=!x[i];
            expect(NativeFocusReusePolicy.editorReusable(x[0],x[1],x[2],x[3],x[4],x[5],x[6],x[7]),false);
        }
        System.out.println("NativeFocusReusePolicyTest OK: "+checked+" checks");
    }
}
