package br.com.seridoplast.mlogbridge;

import android.content.Context;
import androidx.work.WorkManager;

/** Since 3.6.25, the GitHub catalog is exclusively operator-initiated. */
public final class BridgeUpdater {
    static final String LAST_STATUS="updater_status";
    static final String PREF_ENABLED="updater_enabled_v350";
    static final String PREF_INSTALL="updater_install_idle_v350";
    private BridgeUpdater(){}
    public static void schedule(Context ctx){
        android.content.SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        if(sp.getBoolean("manual_only_migration_v3625",false))return;
        WorkManager m=WorkManager.getInstance(ctx.getApplicationContext());
        for(String name:new String[]{"bridge_auto_check_10m_a_v362","bridge_auto_check_10m_b_v362","bridge_check_watchdog_v3619","bridge_check_soon_v362","bridge_auto_install_idle_v359","bridge_idle_retry_2m_a_v359","bridge_idle_retry_2m_b_v359","bridge_auto_update_check_v350","bridge_check_soon_v350","bridge_auto_install_idle_v350","bridge_panel_stable_check_v3616"})m.cancelUniqueWork(name);
        sp.edit().putBoolean(PREF_ENABLED,false).putBoolean(PREF_INSTALL,false).remove("updater_next_check_due_v362").remove(GitHubReleaseUpdater.PREF_APPROVED_AT).remove(GitHubReleaseUpdater.PREF_APPROVED_TAG).putBoolean("manual_only_migration_v3625",true).apply();
    }
    public static void checkSoon(Context ctx){} // no network
    static boolean checkRequestedByPanel(Context ctx){return false;}
    public static void status(Context ctx,String message){ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).edit().putString(LAST_STATUS,message).apply();android.util.Log.i("MLOGBridge","UPDATE "+message);}
}
