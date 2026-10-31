package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

/** Installs only APKs revalidated by the pinned GitHubReleaseUpdater.
 * Shell stdin is streamed: Android 14 shell cannot read our private app directory.
 */
final class ShizukuApkInstaller {
    private static final AtomicBoolean INSTALLING=new AtomicBoolean(false);
    private ShizukuApkInstaller() { }
    static boolean ready() {
        try {return Shizuku.pingBinder()
            && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED
            && (Shizuku.getUid()==2000 || Shizuku.getUid()==0);
        } catch (RuntimeException ex) {return false;}
    }
    interface Callback {void done(boolean ok,String message);}

    static void installAsync(Context context,Callback callback) {
        Context app=context.getApplicationContext();
        new Thread(()->{
            boolean success=false;String status;
            try {
                GitHubReleaseUpdater.Update update=GitHubReleaseUpdater.cachedVerified(app);
                if(update==null)throw new IllegalStateException("Nenhuma atualizacao verificada foi baixada.");
                status=installVerified(app,update);
                success=true;
            } catch (Exception ex) {
                status="Falha de instalacao: "+ex.getMessage();
                BridgeUpdater.status(app,status);
            }
            final boolean ok=success; final String message=status;
            if(callback!=null)new Handler(Looper.getMainLooper()).post(()->callback.done(ok,message));
        },"bridge-shizuku-install").start();
    }

    /** Caller must already enforce production idle gating for unattended installations. */
    static String installVerified(Context context,GitHubReleaseUpdater.Update update) throws Exception {
        if(!INSTALLING.compareAndSet(false,true))throw new IllegalStateException("Outra instalacao em andamento.");
        ShizukuRemoteProcess process=null;
        try {
            if(!ready())throw new IllegalStateException("Shizuku desligado ou nao autorizado.");
            GitHubReleaseUpdater.Update rechecked=GitHubReleaseUpdater.cachedVerified(context);
            if(rechecked==null || !rechecked.tag.equals(update.tag))
                throw new IllegalStateException("APK nao passou na verificacao de assinatura/versao.");
            File apk=rechecked.apk;
            long len=apk.length();
            if(len<50*1024 || len>120L*1024L*1024L)
                throw new IllegalStateException("Tamanho de APK inesperado.");
            BridgeUpdater.status(context,"Instalando "+update.tag+" via Shizuku...");
            Method command=Shizuku.class.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);
            command.setAccessible(true);
            Object response;
            try {
                response=command.invoke(null,(Object)new String[]{"/system/bin/pm","install","-r","-S",Long.toString(len),"-"},null,null);
            } catch(InvocationTargetException ex) {
                throw new IllegalStateException("Shizuku nao criou processo pm",ex.getCause());
            }
            if(!(response instanceof ShizukuRemoteProcess))
                throw new IllegalStateException("Processo de instalacao indisponivel no Shizuku.");
            process=(ShizukuRemoteProcess)response;
            // Do not buffer the APK in RAM; shell stdin is a stream.
            try(InputStream in=new FileInputStream(apk);OutputStream out=process.getOutputStream()) {
                byte[] b=new byte[32768];int n;
                while((n=in.read(b))!=-1)out.write(b,0,n);
                out.flush();
            }
            if(!process.waitForTimeout(120,TimeUnit.SECONDS)) {
                process.destroy();
                throw new IllegalStateException("Tempo limite de instalacao excedido.");
            }
            int exit=process.exitValue();
            String stdout=readShort(process.getInputStream());
            String stderr=readShort(process.getErrorStream());
            if(exit!=0 || !stdout.contains("Success")) {
                Log.e("MLOGBridge","UPDATE_PM_FAILED exit="+exit+" stdout="+stdout+" stderr="+stderr);
                throw new IllegalStateException("pm install retornou "+exit+"; "+limited(stderr.isEmpty()?stdout:stderr));
            }
            BridgeUpdater.status(context,"Instalacao "+update.tag+" concluida.");
            return "Atualizacao "+update.tag+" instalada.";
        } finally {
            INSTALLING.set(false);
            if(process!=null) {
                try {process.getInputStream().close();}catch(Exception ignored) { }
                try {process.getErrorStream().close();}catch(Exception ignored) { }
            }
        }
    }
    private static String limited(String s) {return s.length()>250?s.substring(0,250):s;}
    private static String readShort(InputStream stream) throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] b=new byte[1024];int n;
        while((n=stream.read(b))!=-1 && out.size()<4096)out.write(b,0,Math.min(n,4096-out.size()));
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
