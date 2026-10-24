package br.com.seridoplast.mlogbridge;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class BridgeUpdateReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context, Intent intent) {
  BridgeUpdater.schedule(context);
  final PendingResult result=goAsync();
  new Thread(() -> { try { BridgeUpdater.check(context.getApplicationContext()); } finally { result.finish(); } }, "bridge-update").start();
 }
}
