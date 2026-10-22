package br.com.seridoplast.mlogbridge;

public final class TimedClickGateTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        TimedClickGate gate = new TimedClickGate();
        check(gate.admitTrigger(1000),"first physical trigger should pass");
        check(gate.reserveClick(1001),"first native click should pass");
        check(!gate.reserveClick(1002),"same-cycle second click must be blocked");
        check(gate.admitBroadcast(1110),"first hardware broadcast admitted");
        check(!gate.admitBroadcast(1175),"echo of first hardware scan suppressed");
        check(!gate.admitTrigger(1400),"second physical trigger inside cooldown blocked");
        check(!gate.admitBroadcast(1500),"broadcast of blocked trigger suppressed");
        check(!gate.reserveClick(1500),"no click within cooldown");
        check(gate.remainingMs(1650)==100,"time remaining");
        check(gate.admitTrigger(1750),"unlock exactly at t+750");
        check(gate.reserveClick(1751),"new click allowed once after cooldown");
        check(gate.admitBroadcast(1840),"second accepted cycle receives barcode");
        check(!gate.admitBroadcast(1899),"second duplicate blocked");
        // nao ficar travado em ausencia de Enter nem ficar guardando eventos
        check(gate.admitTrigger(2500),"third cycle should pass without Enter event");
        check(gate.reserveClick(2501),"third click allowed");
        check(!gate.admitTrigger(3000),"cooldown remains effective");
        check(gate.admitTrigger(3250),"automatic rearm without external event");
        // bounce is ignored without interfering with the allowed barcode
        TimedClickGate bounce = new TimedClickGate();
        check(bounce.admitTrigger(5000),"bounce baseline");
        check(!bounce.admitTrigger(5030),"bounce ignored");
        check(bounce.admitBroadcast(5080),"valid original broadcast still admitted");
        // fallback in environments without physical key events
        TimedClickGate broadcast = new TimedClickGate();
        check(broadcast.admitBroadcast(9000),"broadcast-only admission");
        check(broadcast.reserveClick(9002),"broadcast-only clicks once");
        check(!broadcast.admitBroadcast(9150),"broadcast-only cooldown enforced");
        check(broadcast.admitBroadcast(9750),"broadcast-only auto unlock");
        check(broadcast.reserveClick(9751),"broadcast-only second cycle click");
        System.out.println("TimedClickGateTest OK: 26 assertions");
    }
}
