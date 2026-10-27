package br.com.seridoplast.mlogbridge;
public final class ZebraModeSwitch330Test {
  private static void check(boolean v){if(!v)throw new AssertionError();}
  public static void main(String[]args){
    check(ZebraProfile0Mode.isSelected(true,true));
    check(!ZebraProfile0Mode.isSelected(true,false));
    check(ZebraProfile0Mode.usesBridgeIme(true,false));
    check(!ZebraProfile0Mode.usesBridgeIme(true,true));
    check(!ZebraProfile0Mode.usesBridgeIme(false,false));
    check("keyboard".equals(ZebraProfile0Mode.OUTPUT));
    System.out.println("ZebraModeSwitch330Test OK");
  }
}