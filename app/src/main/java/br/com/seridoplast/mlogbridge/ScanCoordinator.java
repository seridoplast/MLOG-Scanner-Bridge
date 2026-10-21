package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

/** Scanner broadcast intake: in-memory deduplication, no stored scan payload. */
final class ScanCoordinator {
    static final String TARGET="com.targetsistemas.target_mlog2";
    static final String PREFS="mlog_bridge_v3"; // preserve v3/v4 preferences
    private static long lastAt;
    private static String lastCode="";
    private ScanCoordinator(){}
    static void status(Context c,String message){
        Log.i("MLOGBridge","elapsed="+SystemClock.elapsedRealtime()+" status="+message);
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("last_status",message).apply();
    }
    static boolean isActive(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getBoolean("enabled",false);
    }
    static synchronized boolean accept(String code){return accept(code,false);}
    static synchronized boolean accept(String code,boolean fast){
        if(code==null || code.length()<1 || code.length()>128)return false;
        for(int i=0;i<code.length();i++){
            char ch=code.charAt(i);
            if(ch<32 || ch>126)return false;
        }
        long t=SystemClock.elapsedRealtime();
        // Filtra broadcasts duplicados do mesmo gatilho sem limitar a sessao.
        // Modo rapido aceita repeticao do mesmo produto apos 65 ms.
        if(code.equals(lastCode) && t-lastAt<(fast?65:140))return false;
        lastAt=t;lastCode=code;return true;
    }
}
