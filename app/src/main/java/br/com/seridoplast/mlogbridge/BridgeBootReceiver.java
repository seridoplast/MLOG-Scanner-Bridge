package br.com.seridoplast.mlogbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recovery is best effort and never bypasses Android's pairing/network approval. */
public final class BridgeBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null || !(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())))return;
        BridgePreferences.enableOnUpgrade(ScanAssistState.prefs(context));
        BridgePreferences.adbOnly(ScanAssistState.prefs(context));
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())){
            android.app.NotificationManager nm=context.getSystemService(android.app.NotificationManager.class);
            if(nm!=null)nm.cancelAll();
        }
        AdbRecoveryWorker.schedule(context,true);
        // Start now as well as scheduling persistent WorkManager recovery.
        LocalAdbBridge.wakeIfUnavailable(context);
        BridgeUpdater.schedule(context);BridgeUpdater.checkSoon(context);
    }
}
