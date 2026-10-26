package br.com.seridoplast.mlogbridge;
public final class FixedEnterCadenceTest {
  static void eq(long want,long got){if(want!=got)throw new AssertionError(want+" != "+got);}
  public static void main(String[] args){
    eq(100,FixedEnterCadence.delayMs(1000,0,100,1000));
    eq(0,FixedEnterCadence.delayMs(1000,0,0,1000));
    eq(0,FixedEnterCadence.delayMs(1000,0,100,1300));
    eq(120,FixedEnterCadence.delayMs(1000,1150,150,1180));
    eq(15,FixedEnterCadence.delayMs(1000,0,15,1000));
    eq(2147483647L,FixedEnterCadence.delayMs(1000,0,2147483647L,1000));
    eq(0,FixedEnterCadence.delayMs(1000,0,100,Long.MAX_VALUE));
    boolean threw=false;try{FixedEnterCadence.delayMs(0,0,-1,0);}catch(IllegalArgumentException e){threw=true;}
    if(!threw)throw new AssertionError("negative delay accepted");
    System.out.println("FixedEnterCadenceTest OK");
  }
}
