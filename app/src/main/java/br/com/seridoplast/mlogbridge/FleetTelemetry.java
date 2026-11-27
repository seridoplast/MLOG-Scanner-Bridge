package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.os.SystemClock;

/** The fleet heartbeat is independent of any popup, sound or overlay. */
final class FleetTelemetry {
    private static long last;
    private FleetTelemetry(){}
    static synchronized void tick(Context c){
        long now=SystemClock.elapsedRealtime();
        if(last!=0 && now-last<5_000L)return;
        last=now;OrderNotesClient.heartbeat(c.getApplicationContext());
    }
}
