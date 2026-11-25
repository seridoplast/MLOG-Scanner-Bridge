package br.com.seridoplast.mlogbridge;

import java.util.Set;
import java.util.Locale;

/** An explicitly enrolled AP only; Android still enforces its own Wi-Fi trust. */
final class AdbRecoveryPolicy {
    private AdbRecoveryPolicy(){}
    static String normalize(String bssid){
        if(bssid==null)return "";
        String v=bssid.toLowerCase(Locale.ROOT);
        if(!v.matches("[0-9a-f]{2}(:[0-9a-f]{2}){5}") || v.equals("02:00:00:00:00:00") || v.equals("00:00:00:00:00:00") || v.equals("ff:ff:ff:ff:ff:ff"))return "";
        return v;
    }
    // Android network ID + SSID + authenticated security type, never SSID alone.
    static String networkKey(int id,String ssid,int security){
        if(id<0 || ssid==null || ssid.isEmpty() || ssid.equalsIgnoreCase("<unknown ssid>"))return "";
        if(security!=2 && security!=3 && security!=4 && security!=5 && security!=9)return "";
        return id+":"+security+":"+ssid;
    }
    static boolean approved(String bssid,String network,Set<String> aps,Set<String> networks){
        String ap=normalize(bssid);
        return !ap.isEmpty() && ((aps!=null && aps.contains(ap)) ||
            (network!=null && !network.isEmpty() && networks!=null && networks.contains(network)));
    }
    static boolean mayEnable(boolean paired,boolean granted,boolean connected,String bssid,Set<String> allowed){
        String ap=normalize(bssid);
        return paired && granted && connected && !ap.isEmpty() && allowed!=null && allowed.contains(ap);
    }
}
