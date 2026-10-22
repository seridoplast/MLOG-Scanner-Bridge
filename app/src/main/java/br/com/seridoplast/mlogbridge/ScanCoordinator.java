package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

/** Mode focus-only: CMX owns decoding, text injection and Enter. */
final class ScanCoordinator {
    static final String TARGET="com.targetsistemas.target_mlog2";
    static final String PREFS="mlog_bridge_v3";
    private ScanCoordinator() { }
    static boolean isActive(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("enabled",false);
    }
    static void status(Context c,String value){
        Log.i("MLOGBridge","elapsed="+SystemClock.elapsedRealtime()+" status="+value);
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("last_status",value).apply();
    }
}
