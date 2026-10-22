package br.com.seridoplast.mlogbridge;
public final class AutoRepeatPolicyTest {
    private static int assertions;
    private static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(AutoRepeatPolicy.MIN_INTERVAL_MS==900L,"900 ms cadence");
        check(AutoRepeatPolicy.virtualRepeats(1)==0,"1 total is 0 virtual");
        check(AutoRepeatPolicy.virtualRepeats(3)==2,"3 total is 2 virtual");
        check(AutoRepeatPolicy.virtualRepeats(100)==99,"100 total is 99 virtual");
        check(AutoRepeatPolicy.delayMs(1000,1000)==900,"back-to-back wait");
        check(AutoRepeatPolicy.delayMs(1000,1899)==1,"1ms still waiting");
        check(AutoRepeatPolicy.delayMs(1000,1900)==0,"unlocked at 900");
        check(AutoRepeatPolicy.delayMs(1000,2200)==0,"wait for Flutter beyond min");
        check(AutoRepeatPolicy.delayMs(1000,900)==900,"clock can't go backwards");
        int sent=1;
        for(int n=2;n<=10;n++){
            sent=AutoRepeatPolicy.afterEnterDispatched(sent,10);
            check(sent==n,"continuous submission advances without UI signal #"+n);
        }
        check(sent==10,"1 physical + 9 virtual = 10 sent");
        check(!AutoRepeatPolicy.refreshSameCode("ABC123","ABC123",1),"first virtual, no stale refresh");
        check(AutoRepeatPolicy.refreshSameCode("ABC123","ABC123",2),"retained barcode cleared after first virtual");
        check(!AutoRepeatPolicy.refreshSameCode("ABC123","XYZ123",2),"different barcode never cleared");
        check(!AutoRepeatPolicy.refreshSameCode(null,"ABC123",2),"null text never cleared");
        boolean rejected=false;
        try{AutoRepeatPolicy.afterEnterDispatched(10,10);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"cannot submit beyond armed target");
        check(AutoRepeatPolicy.firstConfirmed("campo_saiu"),"field disappeared");
        check(AutoRepeatPolicy.firstConfirmed("campo_mudou"),"field changed");
        check(!AutoRepeatPolicy.firstConfirmed("timeout"),"timeout not success");
        check(!AutoRepeatPolicy.firstConfirmed("sem_confirmacao_cmx"),"unconfirmed not success");
        System.out.println("AutoRepeatPolicyTest OK: "+assertions+" checks");
    }
}
