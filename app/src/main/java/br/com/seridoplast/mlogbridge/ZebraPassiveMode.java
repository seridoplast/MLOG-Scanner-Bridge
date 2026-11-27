package br.com.seridoplast.mlogbridge;
import android.content.Context;
/** Native DataWedge keystrokes remain owned by Zebra. Bridge only observes the explicit intent. */
final class ZebraPassiveMode {
 static final String PREF="zebra_passive_v403";
 static final String ACTION="br.com.seridoplast.mlogbridge.ZEBRA_SCAN";
 static boolean enabled(Context c){return c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).getBoolean(PREF,false);}
 private ZebraPassiveMode(){}
}
