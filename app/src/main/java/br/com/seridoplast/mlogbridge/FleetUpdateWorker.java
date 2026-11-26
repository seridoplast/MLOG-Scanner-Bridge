package br.com.seridoplast.mlogbridge;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/** A specific admin-selected, corporate-signed APK from the discovered LAN server. */
public final class FleetUpdateWorker extends Worker {
    private static final long MAX=120L*1024*1024;
    public FleetUpdateWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    static void offer(Context c,JSONObject j){
        String id=j.optString("id"),hash=j.optString("packageId"),tag=j.optString("tag");
        if(!id.matches("[a-f0-9]{32}")||!hash.matches("[a-f0-9]{64}")||GitHubReleaseVersion.versionCode(tag)<0)return;
        if(!OrderNotesClient.deviceId(c).equals(j.optString("deviceId")))return;
        OneTimeWorkRequest w=new OneTimeWorkRequest.Builder(FleetUpdateWorker.class)
            .setInputData(new Data.Builder().putString("job",id).putString("hash",hash).putString("tag",tag).build())
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).build();
        WorkManager.getInstance(c).enqueueUniqueWork("fleet_apk_"+id,ExistingWorkPolicy.KEEP,w);
    }
    private void status(Context c,String id,String state,String detail)throws Exception{
        if(detail==null)detail="Falha sem detalhe";
        JSONObject j=new JSONObject();j.put("id",OrderNotesClient.deviceId(c));j.put("job",id);j.put("status",state);j.put("detail",detail.length()>160?detail.substring(0,160):detail);
        OrderNotesClient.request(c,"POST","/api/device/apk/status",j);
        BridgeUpdater.status(c,detail);
    }
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();String id=getInputData().getString("job"),hash=getInputData().getString("hash"),tag=getInputData().getString("tag");
        if(id==null||!id.matches("[a-f0-9]{32}")||hash==null||!hash.matches("[a-f0-9]{64}")||GitHubReleaseVersion.versionCode(tag)<0)return Result.failure();
        File dir=new File(c.getFilesDir(),"fleet-updates");File apk=new File(dir,hash+".apk"),tmp=new File(dir,hash+".part");
        try{
            long version=GitHubReleaseVersion.versionCode(tag);
            if(version<=GitHubReleaseUpdater.code(c.getPackageManager().getPackageInfo(c.getPackageName(),0))){apk.delete();return Result.success();}
            if(!OrderNotesClient.configured(c))return Result.retry();
            // Revalidate assignment before every attempt, including after restart/cancel.
            status(c,id,"waiting","Aguardando coletor livre e ADB autorizado");
            if(!LocalAdbApkInstaller.available(c)||!BridgeIdleInstallWorker.isCollectorIdle(c))return Result.retry();
            if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Pasta de atualização indisponível");
            if(!apk.isFile()||!hash.equals(GitHubReleaseUpdater.sha256(apk))){
                status(c,id,"downloading","Baixando APK da Central pela rede local");
                String host=OrderNotesClient.base(c);if(!OrderNotesClient.validHost(host))throw new IOException("Central inválida");
                HttpURLConnection http=(HttpURLConnection)new URL(host+"/api/device/apk?id="+OrderNotesClient.deviceId(c)+"&job="+id).openConnection();
                http.setInstanceFollowRedirects(false);http.setConnectTimeout(5000);http.setReadTimeout(20000);http.setRequestProperty("X-Bridge-Token",OrderNotesClient.token(c));
                try{
                    if(http.getResponseCode()!=200)throw new IOException("HTTP "+http.getResponseCode());
                    long size=http.getContentLengthLong();if(size>MAX)throw new IOException("APK grande demais");
                    long n=0;try(InputStream in=http.getInputStream();OutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[65536];int r;while((r=in.read(b))!=-1){if(isStopped())throw new IOException("Download interrompido");n+=r;if(n>MAX)throw new IOException("APK grande demais");out.write(b,0,r);}}
                    if(n<50*1024||!hash.equals(GitHubReleaseUpdater.sha256(tmp)))throw new SecurityException("SHA-256 do APK divergente");
                    if(apk.exists()&&!apk.delete())throw new IOException("Cache ocupado");if(!tmp.renameTo(apk))throw new IOException("Cache indisponível");
                }finally{http.disconnect();tmp.delete();}
            }
            try{GitHubReleaseUpdater.verifyApk(c.getPackageManager(),c.getPackageName(),apk,version,tag.substring(1));}catch(Exception ex){throw new SecurityException("APK rejeitado: "+ex.getMessage());}
            if(isStopped()||!LocalAdbApkInstaller.available(c)||!BridgeIdleInstallWorker.isCollectorIdle(c)){status(c,id,"waiting","APK verificado; aguardando coletor livre");return Result.retry();}
            status(c,id,"installing","Instalando "+tag+"; aguardando confirmação da versão");
            LocalAdbApkInstaller.installFleet(c,apk,tag);apk.delete();return Result.success();
        }catch(SecurityException ex){apk.delete();try{status(c,id,"rejected",ex.getMessage());}catch(Exception ignored){}return Result.failure();}
        catch(Exception ex){tmp.delete();String message=String.valueOf(ex.getMessage());if(message.contains("HTTP 404")||message.contains("HTTP 403")){apk.delete();return Result.success();}try{status(c,id,"retry","Nova tentativa: "+message);}catch(Exception ignored){}return Result.retry();}
    }
}
