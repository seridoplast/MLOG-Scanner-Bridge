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
}
