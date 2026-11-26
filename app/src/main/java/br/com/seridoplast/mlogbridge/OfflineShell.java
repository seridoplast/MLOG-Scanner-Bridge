package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separate health/command path: never waits for ADB discovery or Wi-Fi. */
final class OfflineShell {
    private static final String PORT="offline_shell_port_v3628", KEY="offline_shell_key_v3628";
    private static final ExecutorService WATCH=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"bridge-offline-watch");t.setDaemon(true);return t;});
    private static final AtomicBoolean probing=new AtomicBoolean();
    private static final Object RPC=new Object();
    private static volatile boolean ready;
    private static volatile Context app;
    private static long lastLaunch=-1;
    private static volatile long lastProbe=-1;
    private OfflineShell(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);}
    static boolean ready(){Context c=app;return ready && c!=null && LocalAdbBridge.enabled(c);}
    static void tick(Context c){
        app=c.getApplicationContext();
        if(!LocalAdbBridge.enabled(app)){ready=false;return;}
        long now=SystemClock.elapsedRealtime();if(lastProbe>=0 && now-lastProbe<4000)return;
        if(!probing.compareAndSet(false,true))return;
        WATCH.execute(()->{try{synchronized(RPC){if(LocalAdbBridge.enabled(app))probe(app);else ready=false;}}finally{lastProbe=SystemClock.elapsedRealtime();probing.set(false);}});
    }
    // IO thread only. Reuse the saved loopback agent after app restart BEFORE
    // attempting discovery or turning wireless debugging back on.
    static boolean confirm(Context c){
        app=c.getApplicationContext();
        synchronized(RPC){return probe(app);}
    }
    private static boolean probe(Context c){
        try{ready=LocalAdbBridge.enabled(c)&&call(c,OfflineShellProtocol.PING,1200);}catch(Exception e){ready=false;}
        return ready;
    }
    private static boolean call(Context c,String request,int timeout)throws Exception{
        SharedPreferences p=prefs(c);int port=p.getInt(PORT,0);String secret=p.getString(KEY,"");
        if(port<30000||port>55000||secret.isEmpty())return false;
        return OfflineShellProtocol.call(port,OfflineShellProtocol.key(secret),request,timeout);
    }
    interface Launcher{boolean launch(String fixed)throws Exception;}
    /** Called only after UID 2000 has been verified over authorized ADB. */
    static void ensure(Context c,Launcher launcher){
        app=c.getApplicationContext();if(!LocalAdbBridge.enabled(app)||ready())return;
        synchronized(RPC){
            if(!LocalAdbBridge.enabled(app)||probe(app))return;
            long now=SystemClock.elapsedRealtime();if(lastLaunch>=0 && now-lastLaunch<30_000L)return;lastLaunch=now;
            try{
                SharedPreferences p=prefs(app);int port=p.getInt(PORT,0);String secret=p.getString(KEY,"");
                if(port<30000||port>55000||!secret.matches("[0-9a-f]{64}")){
                    port=30000+new SecureRandom().nextInt(25001);secret=OfflineShellProtocol.hex(OfflineShellProtocol.nonce());
                    if(!p.edit().putInt(PORT,port).putString(KEY,secret).commit())return;
                }
                String apk=app.getApplicationInfo().sourceDir;
                if(apk==null||!apk.startsWith("/data/app/")||!apk.matches("[A-Za-z0-9_./=+~-]+\\.apk"))return;
                // Key is passed on stdin (not the agent's argv). Redirect output and detach
                // from the ADB shell session so loss of the transport need not kill the agent.
                String fixed="printf '%s\\n' '"+secret+"' | CLASSPATH='"+apk+"' /system/bin/setsid /system/bin/nohup /system/bin/app_process /system/bin br.com.seridoplast.mlogbridge.OfflineShellAgent "+port+" >/dev/null 2>&1 &";
                // Existing command() appends a semicolon; terminate with a harmless command.
                launcher.launch(fixed+" true");
                for(int i=0;i<6 && LocalAdbBridge.enabled(app);i++){
                    if(probe(app))return;Thread.sleep(150L);
                }
                android.util.Log.w("BridgeOffline","Serviço local ainda não confirmado; ADB permanece disponível.");
            }catch(Exception ex){if(ex instanceof InterruptedException)Thread.currentThread().interrupt();android.util.Log.w("BridgeOffline","Inicialização local não confirmada: "+ex.getClass().getSimpleName());}
        }
    }
    static boolean execute(String request)throws Exception{
        synchronized(RPC){
            Context c=app;if(c==null||!LocalAdbBridge.enabled(c)||!ready)throw new OfflineShellProtocol.BeforeDispatch(new IOException("Serviço local indisponível"));
            try{return call(c,request,6500);}catch(Exception ex){ready=false;throw ex;}
        }
    }
    static void disable(Context c){
        ready=false;Context context=c.getApplicationContext();
        WATCH.execute(()->{synchronized(RPC){if(LocalAdbBridge.enabled(context))return;
            try{call(context,OfflineShellProtocol.STOP,1200);}catch(Exception ignored){}
            prefs(context).edit().remove(PORT).remove(KEY).apply();ready=false;lastLaunch=-1;lastProbe=-1;
        }});
    }
}
