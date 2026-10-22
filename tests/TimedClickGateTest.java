package br.com.seridoplast.mlogbridge;

public final class TimedClickGateTest {
    private static int assertions;
    private static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(TimedClickGate.COOLDOWN_MS==585L,"cooldown must be exactly 585ms");
        TimedClickGate gate = new TimedClickGate();
        check(gate.admitTrigger(1000),"first physical trigger should pass");
        check(gate.reserveClick(1001),"first native click should pass");
        check(!gate.reserveClick(1002),"same-cycle second click must be blocked");
        check(gate.admitBroadcast(1110),"first hardware broadcast admitted");
        check(!gate.admitBroadcast(1175),"echo of first hardware scan suppressed");
        check(!gate.admitTrigger(1400),"second physical trigger inside cooldown blocked");
        check(!gate.admitBroadcast(1500),"broadcast of blocked trigger suppressed");
        check(!gate.reserveClick(1500),"no click within cooldown");
        check(gate.remainingMs(1500)==85,"time remaining is 85 ms");
        check(gate.remainingMs(1584)==1,"locked until millisecond 584");
        check(gate.admitTrigger(1585),"unlock exactly at t+585");
        check(gate.reserveClick(1586),"new click allowed once after cooldown");
        check(gate.admitBroadcast(1675),"second accepted cycle receives barcode");
        check(!gate.admitBroadcast(1734),"second duplicate blocked");
        check(gate.admitTrigger(2300),"third cycle should pass without Enter event");
        check(gate.reserveClick(2301),"third click allowed");
        check(!gate.admitTrigger(2600),"cooldown remains effective");
        check(gate.admitTrigger(2885),"automatic rearm without external event");
        TimedClickGate bounce = new TimedClickGate();
        check(bounce.admitTrigger(5000),"bounce baseline");
        check(!bounce.admitTrigger(5030),"bounce ignored");
        check(bounce.admitBroadcast(5080),"valid original broadcast still admitted");
        TimedClickGate broadcast = new TimedClickGate();
        check(broadcast.admitBroadcast(9000),"broadcast-only admission");
        check(broadcast.reserveClick(9002),"broadcast-only clicks once");
        check(!broadcast.admitBroadcast(9150),"broadcast-only cooldown enforced");
        check(broadcast.admitBroadcast(9585),"broadcast-only auto unlock");
        check(broadcast.reserveClick(9586),"broadcast-only second cycle click");
        System.out.println("TimedClickGateTest OK: "+assertions+" assertions");
    }
}
