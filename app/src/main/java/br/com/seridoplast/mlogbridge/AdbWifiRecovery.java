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

/** Explicit local enrollment; Android still controls Wi-Fi trust and ADB pairing. */
final class AdbWifiRecovery {
    private static final String SETTING="adb_wifi_enabled";
    private static final String AP_LIST="adb_recovery_approved_aps_v3621";
    private static final String NETWORKS="adb_recovery_networks_v3622";
    private static final String PROVISION="adb_recovery_permission_consent_v3622";
    private static long lastAttempt,lastProvision;
    private static volatile String outcome="";
    private static final AdbRecoveryPolicy.StaleTransport STALE=new AdbRecoveryPolicy.StaleTransport();
    private AdbWifiRecovery(){}
    private static SharedPreferences prefs(Context c){return ScanAssistState.prefs(c);}
    static boolean hasGrant(Context c){return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
    static boolean hasBackground(Context c){return Build.VERSION.SDK_INT<29 || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    static boolean wifiDebugOn(Context c){return Settings.Global.getInt(c.getContentResolver(),SETTING,0)==1;}
    private static Set<String> list(Context c,String key){return new HashSet<>(prefs(c).getStringSet(key,java.util.Collections.emptySet()));}
    private static final class Wifi {
        final String ap,network;
        Wifi(String ap,String network){this.ap=ap;this.network=network;}
    }
    private static Wifi current(Context c){
        Wifi empty=new Wifi("","");
        try{
            ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);
            if(cm==null)return empty;
            boolean connected=false;
            for(Network n:cm.getAllNetworks()){
                NetworkCapabilities cap=cm.getNetworkCapabilities(n);
                if(cap!=null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){connected=true;break;}
            }
            if(!connected)return empty;
            WifiManager wm=(WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info=wm==null?null:wm.getConnectionInfo();
            if(info==null || info.getNetworkId()<0)return empty;
            String ap=AdbRecoveryPolicy.normalize(info.getBSSID());
            String network=Build.VERSION.SDK_INT>=31?AdbRecoveryPolicy.networkKey(info.getNetworkId(),info.getSSID(),info.getCurrentSecurityType()):"";
            return new Wifi(ap,network);
        }catch(RuntimeException e){return empty;}
    }
    private static boolean approved(Context c,Wifi w){return AdbRecoveryPolicy.approved(w.ap,w.network,list(c,AP_LIST),list(c,NETWORKS));}
    static synchronized String enroll(Context c){
        if(!LocalAdbBridge.enabled(c))return "Pareie o ADB primeiro.";
        Wifi w=current(c);
        if(w.ap.isEmpty())return "Conecte ao Wi-Fi corporativo e autorize localização precisa.";
        // Android 12+: authorize the authenticated saved network, not an arbitrary SSID.
        if(Build.VERSION.SDK_INT>=31 && w.network.isEmpty())return "Use uma rede corporativa protegida (WPA2/WPA3).";
        Set<String> aps=list(c,AP_LIST), networks=list(c,NETWORKS);
        if(w.network.isEmpty()){
            if(aps.size()>=32 && !aps.contains(w.ap))return "Limite de pontos Wi-Fi atingido.";
            aps.add(w.ap);
        }else{
            if(networks.size()>=32 && !networks.contains(w.network))return "Limite de redes atingido.";
            networks.add(w.network);
            // Do not retain a weaker legacy match for the newly approved point.
            aps.remove(w.ap);
        }
        if(!prefs(c).edit().putStringSet(AP_LIST,aps).putStringSet(NETWORKS,networks).putBoolean(PROVISION,true).commit())return "Não foi possível salvar a autorização.";
        lastAttempt=0;lastProvision=0;outcome="";
        LocalAdbBridge.retry(c);
        return "Rede salva. Aguarde a confirmação das permissões pelo ADB local.";
    }
    static synchronized void clear(Context c){prefs(c).edit().remove(AP_LIST).remove(NETWORKS).remove(PROVISION).commit();outcome="";lastAttempt=0;lastProvision=0;}
    // Only after explicit enrollment, on an approved network, through the already paired local shell.
    static synchronized boolean provisionConsent(Context c){
        return LocalAdbBridge.enabled(c) && prefs(c).getBoolean(PROVISION,false) && approved(c,current(c));
    }
    static synchronized boolean pendingProvision(Context c){
        if(!provisionConsent(c))return false;
        if(hasGrant(c) && hasBackground(c)){prefs(c).edit().remove(PROVISION).apply();return false;}
        return true;
    }
    static synchronized boolean shouldProvision(Context c){
        if(!provisionConsent(c))return false;
        if(hasGrant(c) && hasBackground(c)){prefs(c).edit().remove(PROVISION).apply();return false;}
        long now=SystemClock.elapsedRealtime();
        if(lastProvision!=0 && now-lastProvision<60_000L)return false;
        lastProvision=now;return true;
    }
    static synchronized void provisionResult(Context c){
        // Consent is consumed by this attempt, never silently re-grant a revoked permission.
        prefs(c).edit().remove(PROVISION).apply();
        if(hasGrant(c) && hasBackground(c))outcome="";
        else outcome="Permissão não confirmada · confira as permissões do Android.";
    }
    static String status(Context c){
        Set<String> aps=list(c,AP_LIST), networks=list(c,NETWORKS);
        if(Build.VERSION.SDK_INT<30)return "Android 11 ou superior necessário.";
        if(aps.isEmpty() && networks.isEmpty())return "Recuperação desativada · autorize o Wi-Fi uma vez.";
        if(!hasGrant(c))return prefs(c).getBoolean(PROVISION,false)?"Aguardando ADB conectado para conceder autorização.":"Autorização pendente · autorize a recuperação.";
        if(!hasBackground(c))return "Autorize localização o tempo todo nas permissões do Android.";
        Wifi w=current(c);
        if(w.ap.isEmpty())return "Aguardando Wi-Fi e identificação da rede.";
        if(!approved(c,w))return "Aguardando rede autorizada · não é necessário recadastrar a mesma rede salva.";
        if(OfflineShell.ready())return "Serviço local ativo · recuperação ADB sob demanda.";
        if(wifiDebugOn(c))return "Recuperação ativa · depuração sem fio ligada.";
        return outcome.isEmpty()?"Depuração desligada · aguardando recuperação.":outcome;
    }
    // Upgrade a previously approved AP only when it is actually observed on a
    // protected saved network; never infer trust from SSID alone.
    static synchronized void migrateLegacy(Context c){
        if(!LocalAdbBridge.enabled(c))return;
        Set<String> aps=list(c,AP_LIST);
        if(aps.isEmpty())return;
        Wifi w=current(c);
        if(w.network.isEmpty() || !aps.contains(w.ap))return;
        Set<String> networks=list(c,NETWORKS);
        if(networks.size()>=32 && !networks.contains(w.network))return;
        networks.add(w.network);aps.remove(w.ap);
        prefs(c).edit().putStringSet(AP_LIST,aps).putStringSet(NETWORKS,networks).apply();
    }
    static void transportHealthy(){STALE.healthy();}
    static void transportUnavailable(){STALE.unavailable();}
    private static boolean mayRestart(Context c){
        return Build.VERSION.SDK_INT>=30 && LocalAdbBridge.enabled(c) && !LocalAdbBridge.pairing()
            && !LocalAdbBridge.transportReady() && !OfflineShell.ready() && hasGrant(c) && approved(c,current(c));
    }
    /** IO executor only. Restart Android debugging once after actual failed connections,
     * never simply because Wi-Fi changed or because the local agent parked ADB. */
    static void staleTransportFailed(Context c){
        boolean eligible=mayRestart(c) && wifiDebugOn(c);
        if(!STALE.failed(SystemClock.elapsedRealtime(),eligible))return;
        if(!mayRestart(c) || !wifiDebugOn(c))return;
        try{
            if(!Settings.Global.putInt(c.getContentResolver(),SETTING,0))return;
            outcome="Porta ADB indisponível · reiniciando depuração autorizada.";
            android.util.Log.i("BridgeLocalADB","Recuperação: porta ADB indisponível após tentativas; reinício único da depuração.");
            try{Thread.sleep(1000L);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            // Never re-enable on another/unapproved network or after opt-out.
            if(mayRestart(c)){
                boolean requested=Settings.Global.putInt(c.getContentResolver(),SETTING,1);
                outcome=requested?"Depuração reativada · aguardando nova porta ADB.":"Firmware recusou a reativação.";
            }
        }catch(RuntimeException ex){outcome="Android bloqueou o reinício da depuração.";}
    }
    static synchronized boolean recoverIfNeeded(Context c){
        if(Build.VERSION.SDK_INT<30)return false;
        if(wifiDebugOn(c)){outcome="";return true;}
        if(!LocalAdbBridge.enabled(c) || !hasGrant(c) || !approved(c,current(c)))return false;
        long now=SystemClock.elapsedRealtime();
        if(lastAttempt!=0 && now-lastAttempt<30_000L)return false;
        lastAttempt=now;
        try{
            boolean requested=Settings.Global.putInt(c.getContentResolver(),SETTING,1);
            outcome=requested?"Reativação solicitada · aguardando Android.":"Firmware recusou a reativação.";
            return requested;
        }catch(SecurityException e){outcome="Android bloqueou a reativação · confira a autorização.";return false;}
        catch(RuntimeException e){outcome="Não foi possível reativar a depuração.";return false;}
    }
}
