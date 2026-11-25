package br.com.seridoplast.mlogbridge;

import android.content.SharedPreferences;

/** One-time defaults for this version. Future manual switches are respected. */
final class BridgePreferences {
    private static final String MIGRATION = "all_settings_enabled_v296";
    private BridgePreferences(){}

    static void enableOnUpgrade(SharedPreferences prefs){
        if(prefs.getBoolean(MIGRATION,false))return;
        prefs.edit()
            .putBoolean("enabled",true)
            .putBoolean("guided_taps",true)
            .putBoolean("layout_fallback",true)
            .putBoolean("experimental_bridge_ime",true)
            .putBoolean("fast_ime_mode",true)
            .putBoolean("trigger_prefocus",true)
            .putBoolean(MIGRATION,true)
            .apply();
    }
    static void adbOnly(SharedPreferences prefs){
        if(prefs.getBoolean("adb_only_v3621",false))return;
        prefs.edit().putBoolean("adb_only_v3621",true)
            .putBoolean(AdbScanMode.PREF,true).putBoolean("enabled",true)
            .putBoolean(ZebraImeMode.PREF,false).putBoolean(ZebraProfile0Mode.PREF,false)
            .putBoolean(NativeScannerController.PREF,true).putBoolean(DirectScanPolicy.PREF,true)
            .putBoolean(BridgeEnterPolicy.PREFERENCE,false).putBoolean(ZebraImeMode.AUTO_FOCUS_PREF,false)
            .putBoolean("trigger_prefocus",false).putBoolean("guided_taps",false)
            .putBoolean("layout_fallback",false).putBoolean(NativeScannerController.LONG_HOLD_PREF,false)
            .putBoolean(ScanAssistState.BATCH,false).putBoolean(ScanAssistState.ARMED,false)
            .putBoolean("floating_auto_start_v302",false).apply();
    }
}
