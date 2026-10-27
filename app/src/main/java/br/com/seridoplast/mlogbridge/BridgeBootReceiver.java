package br.com.seridoplast.mlogbridge;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class BridgeBootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context, Intent intent) { BridgeUpdater.schedule(context); ShizukuHealthMonitor.refresh(context); }
}
