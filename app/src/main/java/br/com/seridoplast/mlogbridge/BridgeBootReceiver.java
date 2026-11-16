package br.com.seridoplast.mlogbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/** Best-effort recovery after firmware restart or APK replacement.
 * Android can restrict starting an overlay service in background; existing
 * accessibility / overlay authorizations are never silently granted. */
public final class BridgeBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        BridgeUpdater.schedule(context);
        BridgeUpdater.checkSoon(context);
        ShizukuHealthMonitor.refresh(context);
        android.content.SharedPreferences prefs=ScanAssistState.prefs(context);
        if(!prefs.getBoolean("observacoes_auto_v3610",false)){
            prefs.edit().putBoolean("observacoes_auto_v3610",true)
                .putBoolean("floating_auto_start_v302",true)
                .putBoolean(ScanAssistState.CLEAN,true).apply();
        }
        if(intent==null || !(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())))return;
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(context))return;
        if(!ScanAssistState.prefs(context).getBoolean("floating_auto_start_v302",true))return;
        try {context.startService(new Intent(context,FloatingToolsService.class));}
        catch(RuntimeException ignored){
            // Background-start restrictions: the main app restarts this service on resume.
        }
    }
}
