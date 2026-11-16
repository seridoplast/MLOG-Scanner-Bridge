package br.com.seridoplast.mlogbridge;
public final class ZebraProfile0ModeTest {
    private static void check(boolean ok,String what){
        if(!ok)throw new AssertionError(what);
    }
    public static void main(String[] args){
        check("keyboard".equals(ZebraProfile0Mode.OUTPUT),"native keyboard output");
        check("newline".equals(ZebraProfile0Mode.TERMINATOR),"Enter suffix");
        check("trigger".equals(ZebraProfile0Mode.SCAN_MODE),"one scan per trigger");
        check(ZebraProfile0Mode.isSelected(true,true),"new profile selected");
        check(!ZebraProfile0Mode.isSelected(true,false),"old IME not Profile0");
        check(!ZebraProfile0Mode.usesBridgeIme(true,true),"no second keyboard writer");
        check(ZebraProfile0Mode.usesBridgeIme(true,false),"legacy IME migration");
        check(!ZebraProfile0Mode.usesBridgeIme(false,false),"old mode unaffected");
        System.out.println("ZebraProfile0ModeTest OK");
    }
}
