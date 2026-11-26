package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/** Process-lifetime listener; coalesces Wi-Fi/ADB changes, does not keep an extra service alive. */
final class AdbNetworkWatch {
    private static boolean started;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private AdbNetworkWatch(){}
    static synchronized void start(Context context){
        if(started)return;
        Context c=context.getApplicationContext();
        ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);
        if(cm==null)return;
        final Runnable retry=()->LocalAdbBridge.networkChanged(c);
        final Runnable signal=()->{MAIN.removeCallbacks(retry);MAIN.postDelayed(retry,1200L);};
        try{
            cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                new ConnectivityManager.NetworkCallback(){
                    @Override public void onAvailable(Network n){signal.run();ProductivityLog.networkAvailable(c);}
                    @Override public void onLost(Network n){signal.run();}
                });
            started=true;
        }catch(RuntimeException ignored){}
        if(started)try{
            c.getContentResolver().registerContentObserver(Settings.Global.getUriFor("adb_wifi_enabled"),false,
                new ContentObserver(MAIN){@Override public void onChange(boolean selfChange){signal.run();}});
        }catch(RuntimeException ignored){}
    }
}
