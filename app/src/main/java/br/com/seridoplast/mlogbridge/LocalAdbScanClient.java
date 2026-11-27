package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local privileged input (detached service or authorized ADB); bounded serialized scan queue. */
final class LocalAdbScanClient {
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {
        Thread thread=new Thread(r,"bridge-adb-scans");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger pending=new AtomicInteger();
    private final AtomicBoolean cameraBusy=new AtomicBoolean(false);
    private final Handler main=new Handler(Looper.getMainLooper());
    LocalAdbScanClient(Context context) {this.context=context.getApplicationContext();}
    void start(){LocalAdbBridge.tick(context);}
    void connect(){LocalAdbBridge.retry(context);}
    boolean ready(){return !ZebraPassiveMode.enabled(context)&&LocalAdbBridge.ready();}
    void stop(){worker.shutdownNow();}

    /** Nao recuperar a tela enquanto houver comandos de leitura na fila. */
    boolean hasPendingScans(){return pending.get()>0 || cameraBusy.get();}

    /** No coalescing by barcode value: two deliberate reads must remain two. */
    boolean scan(String raw) {return scan(raw,null);}
    boolean scan(String raw,ProductivityLog.Event event) {
        // O CMX pode anexar CR/LF/TAB/NUL ou marcadores invisiveis. O teste
        // de leitura mostra o codigo, mas o caminho Adb antigo recusava
        // o payload completo antes de digitar no Flutter.
        final String code=AdbBarcodePayload.prepare(raw);
        if(!AdbScanMode.supported(code)){
            Log.w("MLOGBridge","ADB_PAYLOAD_UNSUPPORTED_OR_EMPTY");
            ScanCoordinator.status(context,"Leitura nao enviada: caracteres incompatíveis ou codigo vazio.");
            return false;
        }
        if(raw!=null && raw.length()!=code.length())
            Log.i("MLOGBridge","ADB_TRANSPORT_SUFFIX_REMOVED_COUNT_"+(raw.length()-code.length()));
        if(cameraBusy.get()){
            ScanCoordinator.status(context,"Recuperacao de Volume em andamento. Bipe novamente depois.");
            return false;
        }
        if(!ready()){
            ScanCoordinator.status(context,"Serviço local e ADB indisponíveis. Leitura não enviada.");
            return false;
        }
        context.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE)
            .edit().putLong("bridge_last_adb_scan_wall",System.currentTimeMillis()).apply();
        if(pending.incrementAndGet()>AdbScanMode.MAX_PENDING){
            pending.decrementAndGet();
            ScanCoordinator.status(context,"Fila cheia. Leitura nao enviada; confira o MLOG.");
            return false;
        }
        try {
            worker.execute(()->{
                try {
                    if(!context.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE)
                        .getBoolean(AdbScanMode.PREF,false))return;
                    if(!ready()){
                        ScanCoordinator.status(context,"Serviço local indisponível; leitura não enviada.");
                        return;
                    }
                    // The tested ADB sequence, not InputConnection nor accessibility.
                    // A failure after the text step is ambiguous: never retry and
                    // accidentally register the barcode twice.
                    if(!LocalAdbBridge.scan(code))ScanCoordinator.status(context,"Leitura sem confirmação. Confira o MLOG.");
                    else ProductivityLog.record(context,event);
                } catch(Exception ex) {
                    Log.w("MLOGBridge","ADB_REMOTE_COMMAND_FAILED",ex);
                    ScanCoordinator.status(context,"Falha na injeção local. Confira o MLOG.");
                } finally {pending.decrementAndGet();}
            });
            return true;
        } catch(RuntimeException ex) {
            pending.decrementAndGet();
            Log.w("MLOGBridge","ADB_QUEUE_FAILED",ex);
            return false;
        }
    }

    interface VolumeRecoveryCallback {
        /** true = comandos aceitos pelo shell; NAO confirma retorno da tela Flutter. */
        void onComplete(boolean commandsOk);
    }

    /**
     * Toca no Volume e depois na seta da tela interna do MLOG, na mesma
     * sessao shell Adb. A pausa curta foi validada via ADB no piloto.
     * Nao garante que a UI Flutter tenha terminado de navegar.
     */
    boolean recoverVolume(int x,int y,int pauseMs,VolumeRecoveryCallback callback){
        if(callback==null || x<1 || y<1 || x>4096 || y>4096 ||
           pauseMs<1 || pauseMs>99 || !ready() || pending.get()!=0 ||
           !cameraBusy.compareAndSet(false,true))return false;
        try{
            worker.execute(()->{
                boolean ok=false;
                long started=android.os.SystemClock.elapsedRealtime();
                try{
                    if(ready()){
                        // Apenas inteiros validados; nenhum argumento arbitrario.
                        // Se o toque falhar, '&&' impede o BACK.
                        String sequence=VolumeRecoveryTarget.shellSequence(x,y,pauseMs);
                        ok=LocalAdbBridge.volume(x,y,pauseMs);
                    }
                    Log.i("MLOGBridge",(ok?"VOLUME_ADB_SEQUENCE_OK":"VOLUME_ADB_SEQUENCE_FAILED")+
                        " elapsed_ms="+(android.os.SystemClock.elapsedRealtime()-started)+
                        " between_commands_ms="+pauseMs);
                }catch(Exception ex){
                    Log.w("MLOGBridge","VOLUME_ADB_COMMAND_FAILED",ex);
                }finally{
                    final boolean result=ok;
                    cameraBusy.set(false);
                    main.post(()->callback.onComplete(result));
                }
            });
            return true;
        }catch(RuntimeException ex){
            cameraBusy.set(false);
            Log.w("MLOGBridge","VOLUME_ADB_QUEUE_FAILED",ex);
            return false;
        }
    }

}
