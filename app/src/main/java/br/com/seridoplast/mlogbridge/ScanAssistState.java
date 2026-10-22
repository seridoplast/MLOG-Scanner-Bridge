package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;

/** Only settings and counters; never persist captured barcode strings. */
final class ScanAssistState {
    static final String CLEAN = "scan_assist_clean";
    static final String BATCH = "scan_assist_batch";
    static final String ARMED = "scan_assist_armed";
    static final String COUNT = "scan_assist_count";
    static final int MAX_COUNT = 100;
    private ScanAssistState(){}
    static SharedPreferences prefs(Context context){
        return context.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
    }
    static int clamp(int n){return Math.max(1,Math.min(MAX_COUNT,n));}
    static boolean isClean(Context context){return prefs(context).getBoolean(CLEAN,false);}
    static boolean isBatch(Context context){return prefs(context).getBoolean(BATCH,false);}
    static boolean isArmed(Context context){return isBatch(context) && prefs(context).getBoolean(ARMED,false);}
    static void report(String message){FloatingToolsService.report(message);}
}
