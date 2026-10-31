package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

/**
 * CMX/MediaTek compatibility experiment. Shizuku's UserService crashes inside
 * LoadedApk.makeApplicationInner on this firmware before our service is created.
 * Execute two tightly validated commands via Shizuku's legacy remote process API
 * instead. This private compatibility API may be removed in later Shizuku versions.
 * It is only used for a selected scan mode, never for arbitrary user commands.
 */
final class ShizukuScanClient {
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {
        Thread thread=new Thread(r,"bridge-shizuku-scans");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger pending=new AtomicInteger();
    private final AtomicBoolean cameraBusy=new AtomicBoolean(false);
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean attached;
    private final Shizuku.OnBinderDeadListener died;
    private final Shizuku.OnBinderReceivedListener arrived;

    ShizukuScanClient(Context context) {
        this.context=context.getApplicationContext();
        died=()->{
            ScanCoordinator.status(this.context,
                "Shizuku parou. Leituras bloqueadas; reinicie o Shizuku.");
            ShizukuHealthMonitor.refresh(this.context);
        };
        arrived=()->{
            Log.i("MLOGBridge","SHIZUKU_BINDER_AVAILABLE");
            ShizukuHealthMonitor.refresh(this.context);
        };
    }

    void start() {
        if(attached)return;
        attached=true;
        Shizuku.addBinderReceivedListenerSticky(arrived);
        Shizuku.addBinderDeadListener(died);
    }

    void connect() {
        if(ready())ScanCoordinator.status(context,
            "Shizuku autorizado. Injetor experimental aguardando leitura.");
        else ScanCoordinator.status(context,
            "Shizuku sem autorizacao/conexao. Nenhuma leitura sera injetada.");
    }

    boolean ready() {
        try {
            return Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED &&
                (Shizuku.getUid()==2000 || Shizuku.getUid()==0);
        } catch(RuntimeException ex) {return false;}
    }

    /** Nao abrir a camera enquanto ha comandos de leitura na fila Shizuku. */
    boolean hasPendingScans(){return pending.get()>0 || cameraBusy.get();}

    /** No coalescing by barcode value: two deliberate reads must remain two. */
    boolean scan(String code) {
        if(!ShizukuScanMode.supported(code)){
            ScanCoordinator.status(context,"Formato do codigo nao suportado; leitura bloqueada.");
            return false;
        }
        if(cameraBusy.get()){
            ScanCoordinator.status(context,"Recuperacao da camera em andamento. Bipe novamente depois.");
            return false;
        }
        if(!ready()){
            ScanCoordinator.status(context,"Shizuku sem conexao. Leitura nao enviada.");
            return false;
        }
        if(pending.incrementAndGet()>ShizukuScanMode.MAX_PENDING){
            pending.decrementAndGet();
            ScanCoordinator.status(context,"Fila cheia. Leitura nao enviada; confira o MLOG.");
            return false;
        }
        try {
            worker.execute(()->{
                try {
                    if(!context.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE)
                        .getBoolean(ShizukuScanMode.PREF,false))return;
                    if(!ready()){
                        ScanCoordinator.status(context,"Shizuku desconectado; leitura nao enviada.");
                        return;
                    }
                    // The tested ADB sequence, not InputConnection nor accessibility.
                    // A failure after the text step is ambiguous: never retry and
                    // accidentally register the barcode twice.
                    if(!runInput("text",code)){
                        ScanCoordinator.status(context,"Falha ao digitar. Confira o MLOG.");
                        return;
                    }
                    if(!runInput("keyevent","66")){
                        ScanCoordinator.status(context,"Falha no Enter. Confira o MLOG.");
                        return;
                    }
                    Log.i("MLOGBridge","SHIZUKU_SCAN_INJECTED_UNVERIFIED");
                } catch(Exception ex) {
                    Log.w("MLOGBridge","SHIZUKU_REMOTE_COMMAND_FAILED",ex);
                    ScanCoordinator.status(context,"Falha na injecao Shizuku. Confira o MLOG.");
                } finally {pending.decrementAndGet();}
            });
            return true;
        } catch(RuntimeException ex) {
            pending.decrementAndGet();
            Log.w("MLOGBridge","SHIZUKU_QUEUE_FAILED",ex);
            return false;
        }
    }

    interface CameraRecoveryCallback {
        void onComplete(boolean tapOk,boolean backOk);
    }

    /** Repete os dois comandos ADB validados no CMX, sem shell -c. */
    boolean recoverCamera(int x,int y,int pauseMs,CameraRecoveryCallback callback){
        if(callback==null || x<1 || y<1 || x>4096 || y>4096 ||
           pauseMs<350 || pauseMs>1800 || !ready() || pending.get()!=0 ||
           !cameraBusy.compareAndSet(false,true))return false;
        try{
            worker.execute(()->{
                boolean tapped=false,backed=false;
                try{
                    if(!ready())return;
                    tapped=runRemoteInput("tap",String.valueOf(x),String.valueOf(y));
                    Log.i("MLOGBridge",tapped?"CAMERA_SHIZUKU_TAP_OK":"CAMERA_SHIZUKU_TAP_FAILED");
                    if(tapped){
                        Thread.sleep(pauseMs);
                        if(ready())backed=runRemoteInput("keyevent","4");
                        Log.i("MLOGBridge",backed?"CAMERA_SHIZUKU_BACK_OK":"CAMERA_SHIZUKU_BACK_FAILED");
                    }
                }catch(Exception ex){
                    if(ex instanceof InterruptedException)Thread.currentThread().interrupt();
                    Log.w("MLOGBridge","CAMERA_SHIZUKU_COMMAND_FAILED",ex);
                }finally{
                    final boolean tapResult=tapped,backResult=backed;
                    cameraBusy.set(false);
                    main.post(()->callback.onComplete(tapResult,backResult));
                }
            });
            return true;
        }catch(RuntimeException ex){
            cameraBusy.set(false);
            Log.w("MLOGBridge","CAMERA_SHIZUKU_QUEUE_FAILED",ex);
            return false;
        }
    }

    private boolean runInput(String operation,String argument) throws Exception {
        if(!"text".equals(operation) && !"keyevent".equals(operation))return false;
        return runRemoteInput(operation,argument);
    }

    /** Argumentos separados: nunca interpretar codigos como comando de shell. */
    private boolean runRemoteInput(String operation,String... arguments) throws Exception {
        if(!"text".equals(operation) && !"keyevent".equals(operation) &&
           !"tap".equals(operation))return false;
        if("tap".equals(operation) && arguments.length!=2)return false;
        if(!"tap".equals(operation) && arguments.length!=1)return false;
        final Method method=Shizuku.class.getDeclaredMethod("newProcess",
                String[].class,String[].class,String.class);
        method.setAccessible(true);
        final String[] argv=new String[arguments.length+2];
        argv[0]="/system/bin/input";argv[1]=operation;
        System.arraycopy(arguments,0,argv,2,arguments.length);
        Object result;
        try {
            result=method.invoke(null,(Object)argv,null,null);
        } catch(InvocationTargetException ex) {
            Throwable cause=ex.getCause();
            throw new IllegalStateException("Shizuku remote command failed",cause);
        }
        if(!(result instanceof ShizukuRemoteProcess))return false;
        ShizukuRemoteProcess process=(ShizukuRemoteProcess)result;
        try {
            if(!process.waitForTimeout(2500,TimeUnit.MILLISECONDS)){
                process.destroy();
                Log.w("MLOGBridge","SHIZUKU_REMOTE_TIMEOUT");
                return false;
            }
            int exit=process.exitValue();
            if(exit!=0)Log.w("MLOGBridge","SHIZUKU_REMOTE_EXIT_"+exit);
            return exit==0;
        } finally {
            try{process.getInputStream().close();}catch(Exception ignored){}
            try{process.getErrorStream().close();}catch(Exception ignored){}
        }
    }

    void stop() {
        attached=false;
        Shizuku.removeBinderReceivedListener(arrived);
        Shizuku.removeBinderDeadListener(died);
        worker.shutdownNow();
    }
}
