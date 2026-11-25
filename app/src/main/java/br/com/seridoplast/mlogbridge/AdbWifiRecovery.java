package br.com.seridoplast.mlogbridge;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.HashSet;
import java.util.Set;

/** Opt-in USB-admin grant; never grants permissions, changes network trust, or accepts LAN commands. */
final class AdbWifiRecovery {
    private static final String SETTING="adb_wifi_enabled";
    private static final String AP_LIST="adb_recovery_approved_aps_v3621";
    private static long lastAttempt;
    private static volatile String outcome="";
    private AdbWifiRecovery(){}
    private static SharedPreferences prefs(Context c){return ScanAssistState.prefs(c);}
    static boolean hasGrant(Context c){return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
    static boolean wifiDebugOn(Context c){return Settings.Global.getInt(c.getContentResolver(),SETTING,0)==1;}
    private static Set<String> allowed(Context c){return new HashSet<>(prefs(c).getStringSet(AP_LIST,java.util.Collections.emptySet()));}
    private static String currentAp(Context c){
        try{
            ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);
            if(cm==null)return "";
            boolean wifiConnected=false;
            for(Network n:cm.getAllNetworks()){
                NetworkCapabilities cap=cm.getNetworkCapabilities(n);
                if(cap!=null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){wifiConnected=true;break;}
            }
            if(!wifiConnected)return "";
            WifiManager wm=(WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info=wm==null?null:wm.getConnectionInfo();
            if(info==null || info.getNetworkId()<0)return "";
            return AdbRecoveryPolicy.normalize(info.getBSSID());
        }catch(RuntimeException e){return "";}
    }
    static synchronized String enroll(Context c){
        if(!LocalAdbBridge.enabled(c))return "Pareie o ADB primeiro.";
        if(!hasGrant(c))return "Falta autorização USB.";
        String ap=currentAp(c);
        if(ap.isEmpty())return "Conecte ao Wi-Fi corporativo e autorize localização precisa.";
        Set<String> list=allowed(c);
        if(list.size()>=32 && !list.contains(ap))return "Limite de pontos Wi-Fi atingido.";
        list.add(ap);prefs(c).edit().putStringSet(AP_LIST,list).apply();lastAttempt=0;outcome="";
        LocalAdbBridge.retry(c);
        return "Ponto Wi-Fi cadastrado.";
    }
    static synchronized void clear(Context c){prefs(c).edit().remove(AP_LIST).apply();outcome="";lastAttempt=0;}
    static String status(Context c){
        Set<String> list=allowed(c);
        if(Build.VERSION.SDK_INT<30)return "Android 11 ou superior necessário.";
        if(!hasGrant(c))return "Autorização USB pendente.";
        if(list.isEmpty())return "Recuperação desativada · cadastre o Wi-Fi.";
        if(Build.VERSION.SDK_INT>=29 && c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            return "Wi-Fi cadastrado · autorize localização o tempo todo para recuperar em segundo plano.";
        String ap=currentAp(c);
        if(!list.contains(ap))return "Aguardando ponto Wi-Fi cadastrado ("+list.size()+").";
        if(wifiDebugOn(c))return "Depuração sem fio ligada · "+list.size()+" ponto(s) cadastrado(s).";
        return outcome.isEmpty()?"Depuração desligada · aguardando recuperação.":outcome;
    }
    /** Called on bounded worker, after opt-in. A settings write is a request, not confirmation. */
    static synchronized boolean recoverIfNeeded(Context c){
        if(Build.VERSION.SDK_INT<30)return false;
        if(wifiDebugOn(c)){outcome="";return true;}
        Set<String> list=allowed(c);String ap=currentAp(c);
        if(!AdbRecoveryPolicy.mayEnable(LocalAdbBridge.enabled(c),hasGrant(c),!ap.isEmpty(),ap,list))return false;
        long now=SystemClock.elapsedRealtime();
        if(lastAttempt!=0 && now-lastAttempt<60_000)return false;
        lastAttempt=now;
        try{
            boolean requested=Settings.Global.putInt(c.getContentResolver(),SETTING,1);
            outcome=requested?"Reativação solicitada · aguardando Android.":"Firmware recusou a reativação.";
            return requested;
        }catch(SecurityException e){outcome="Android bloqueou a reativação · confira a autorização USB.";return false;}
        catch(RuntimeException e){outcome="Não foi possível reativar a depuração.";return false;}
    }
}
