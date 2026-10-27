package br.com.seridoplast.mlogbridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import rikka.shizuku.Shizuku;

/** Only used when the Shizuku mode is selected. No accessibility typing/taps. */
final class ShizukuScanClient {
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {
        Thread thread=new Thread(r,"bridge-shizuku-scans");
        thread.setDaemon(true);return thread;
    });
    private final AtomicInteger pending=new AtomicInteger();
    private volatile IShizukuScanService remote;
    private volatile boolean connecting;
    private boolean attached;
    private final Shizuku.OnBinderReceivedListener arrived=()->connect();
    // Instantiate after context has been assigned: Java definite assignment
    // forbids referencing a blank final field from an earlier field initializer.
    private final Shizuku.OnBinderDeadListener died;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            remote=IShizukuScanService.Stub.asInterface(binder);
            connecting=false;
            ScanCoordinator.status(context,"Shizuku conectado. Teste uma leitura no MLOG.");
            Log.i("MLOGBridge","SHIZUKU_SCAN_SERVICE_CONNECTED");
        }
        @Override public void onServiceDisconnected(ComponentName name){
            remote=null; connecting=false;
        }
    };

    ShizukuScanClient(Context context){
        this.context=context.getApplicationContext();
        this.died=()->{
            remote=null;
            connecting=false;
            ScanCoordinator.status(this.context,
                "Shizuku parou. Leitura bloqueada; reinicie o Shizuku.");
        };
    }

    void start(){
        if (attached)return;
        attached=true;
        Shizuku.addBinderReceivedListenerSticky(arrived);
        Shizuku.addBinderDeadListener(died);
    }

    void connect(){
        if (!attached || remote!=null || connecting)return;
        try {
            if (!Shizuku.pingBinder() ||
                Shizuku.checkSelfPermission()!=android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            connecting=true;
            Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(
                new ComponentName(context,ShizukuShellScanService.class))
                .daemon(false).processNameSuffix("scan-shell").version(1);
            Shizuku.bindUserService(args,connection);
        } catch (Exception ex) {
            connecting=false;
            Log.w("MLOGBridge","SHIZUKU_BIND_FAILED",ex);
        }
    }

    boolean ready(){
        try{return remote!=null && Shizuku.pingBinder();}
        catch(RuntimeException ignored){return false;}
    }

    /** Every admitted broadcast represents one scan. Never coalesce equal barcodes. */
    void scan(String code){
        if (!ShizukuScanMode.supported(code)) {
            ScanCoordinator.status(context,"Codigo com caracteres nao suportados; leitura nao enviada.");
            Log.w("MLOGBridge","SHIZUKU_UNSUPPORTED_FORMAT");return;
        }
        if (!ready()){
            ScanCoordinator.status(context,"Shizuku sem conexao. Leitura nao enviada.");
            connect();return;
        }
        if (pending.incrementAndGet()>ShizukuScanMode.MAX_PENDING){
            pending.decrementAndGet();
            ScanCoordinator.status(context,"Fila Shizuku cheia. Leitura nao enviada.");return;
        }
        try{worker.execute(()->{
            try {
                // A pending scan belongs to this mode only. A mode switch
                // must not release delayed barcodes into another workflow.
                if (!context.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE)
                    .getBoolean(ShizukuScanMode.PREF,false))return;
                IShizukuScanService service=remote;
                if (service==null || !service.injectScan(code)) {
                    ScanCoordinator.status(context,"Falha Shizuku: conferir ultimo produto no MLOG.");
                    Log.w("MLOGBridge","SHIZUKU_SCAN_FAILED_UNVERIFIED");
                } else {
                    Log.i("MLOGBridge","SHIZUKU_SCAN_INJECTED_UNVERIFIED");
                }
            } catch(RemoteException|RuntimeException ex){
                ScanCoordinator.status(context,"Shizuku perdeu conexao; confira leitura.");
                Log.w("MLOGBridge","SHIZUKU_REMOTE_FAILURE",ex);
                remote=null;
            } finally {pending.decrementAndGet();}
        });}catch(java.util.concurrent.RejectedExecutionException ex){
            pending.decrementAndGet();
            Log.w("MLOGBridge","SHIZUKU_QUEUE_STOPPED");
        }
    }
    void stop(){
        attached=false;
        Shizuku.removeBinderReceivedListener(arrived);
        Shizuku.removeBinderDeadListener(died);
        remote=null;
        worker.shutdownNow();
    }
}
