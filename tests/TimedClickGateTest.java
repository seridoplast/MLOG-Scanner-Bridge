package br.com.seridoplast.mlogbridge;

public final class TimedClickGateTest {
    private static int assertions;
    private static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(TimedClickGate.COOLDOWN_MS==150L,"cooldown must be exactly 150ms");
        TimedClickGate gate = new TimedClickGate();
        check(gate.admitTrigger(1000),"first physical trigger should pass");
        check(gate.reserveClick(1001),"first native click should pass");
        check(!gate.reserveClick(1002),"same-cycle second click must be blocked");
        check(gate.admitBroadcast(1020),"first hardware broadcast admitted");
        check(!gate.admitBroadcast(1040),"echo of first hardware scan suppressed");
        check(!gate.admitTrigger(1080),"second physical trigger inside cooldown blocked");
        check(!gate.admitBroadcast(1100),"broadcast of blocked trigger suppressed");
        check(!gate.reserveClick(1100),"no click within cooldown");
        check(gate.remainingMs(1140)==10,"10ms remain");
        check(gate.remainingMs(1149)==1,"locked through t+149");
        check(gate.admitTrigger(1150),"unlock exactly at t+150");
        check(gate.reserveClick(1151),"new click allowed once after cooldown");
        check(gate.admitBroadcast(1210),"second accepted cycle receives barcode");
        check(!gate.admitBroadcast(1230),"second duplicate blocked");
        check(gate.admitTrigger(2300),"third cycle should pass without Enter event");
        check(gate.reserveClick(2301),"third click allowed");
        check(!gate.admitTrigger(2400),"cooldown remains effective");
        check(gate.admitTrigger(2500),"automatic rearm without external event");
        TimedClickGate bounce = new TimedClickGate();
        check(bounce.admitTrigger(5000),"bounce baseline");
        check(!bounce.admitTrigger(5030),"bounce ignored");
        check(bounce.admitBroadcast(5080),"valid original broadcast still admitted");
        TimedClickGate broadcast = new TimedClickGate();
        check(broadcast.admitBroadcast(9000),"broadcast-only admission");
        check(broadcast.reserveClick(9002),"broadcast-only clicks once");
        check(!broadcast.admitBroadcast(9120),"broadcast-only cooldown enforced");
        check(broadcast.admitBroadcast(9150),"broadcast-only auto unlock");
        check(broadcast.reserveClick(9151),"broadcast-only second cycle click");
        System.out.println("TimedClickGateTest OK: "+assertions+" assertions");
    }
}
