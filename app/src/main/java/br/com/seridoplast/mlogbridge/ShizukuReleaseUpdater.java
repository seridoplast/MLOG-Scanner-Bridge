package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/**
 * Optional update channel for the separately installed, official Shizuku manager.
 * Only public stable GitHub releases; never auto-install unless separately opted in.
 * A downloaded APK must match the installed manager's signing certificate.
 */
final class ShizukuReleaseUpdater {
    static final String PACKAGE="moe.shizuku.privileged.api";
    static final String PREF_CHECK="shizuku_update_daily_optin_v364";
    static final String PREF_INSTALL="shizuku_update_install_optin_v364";
    static final String STATUS="shizuku_update_status_v364";
    private static final String API="https://api.github.com/repos/RikkaApps/Shizuku/releases/latest";
    private static final String PREFIX="https://github.com/RikkaApps/Shizuku/releases/download/";
    private static final String PREF_HASH="shizuku_cached_sha256_v364";
    private static final String PREF_CODE="shizuku_cached_versioncode_v364";
    private static final String PREF_TAG="shizuku_cached_tag_v364";
    private static final long MAX_APK=80L*1024L*1024L;
    private static final Object LOCK=new Object();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private ShizukuReleaseUpdater(){}

    interface Callback { void result(boolean available,String message); }
    static void checkAsync(Context context,Callback cb){
        Context app=context.getApplicationContext();
        new Thread(()->{
            boolean found=false;String message;
            try { found=fetch(app); message=app.getSharedPreferences(ScanCoordinator.PREFS,0)
                .getString(STATUS,"Consulta concluída."); }
            catch(Exception e){ message="Shizuku: "+brief(e);status(app,message); }
            final boolean ready=found;final String msg=message;
            if(cb!=null)MAIN.post(()->cb.result(ready,msg));
        },"shizuku-release-check").start();
    }
    static void status(Context ctx,String s){
        ctx.getSharedPreferences(ScanCoordinator.PREFS,0).edit().putString(STATUS,s).apply();
    }
    static boolean fetch(Context ctx) throws Exception {
        synchronized(LOCK){
            PackageInfo installed=installed(ctx);
            if(installed==null){status(ctx,"Shizuku não instalado. Instale o app oficial primeiro.");return false;}
            JSONObject release=new JSONObject(readJson(API));
            if(release.optBoolean("draft",false) || release.optBoolean("prerelease",false))
                throw new IOException("A versão consultada não é uma Release estável.");
            String tag=release.optString("tag_name","");
            if(!ShizukuUpdatePolicy.isStableTag(tag))throw new IOException("Tag do Shizuku inválida.");
            if(!ShizukuUpdatePolicy.newerThan(tag,installed.versionName)){
                status(ctx,"Shizuku já atualizado ("+installed.versionName+").");return false;
            }
            JSONArray assets=release.optJSONArray("assets");
            JSONObject selected=null;
            if(assets!=null)for(int i=0;i<assets.length();i++){
                JSONObject item=assets.optJSONObject(i);
                if(item==null)continue;
                String name=item.optString("name","").toLowerCase(Locale.ROOT);
                if(ShizukuUpdatePolicy.officialApk(tag,name,
                        item.optString("browser_download_url",""))){
                    selected=item;break;
                }
            }
            if(selected==null){status(ctx,"A Release não contém APK oficial compatível.");return false;}
            long size=selected.optLong("size",0L);
            if(size<50*1024 || size>MAX_APK)throw new IOException("Tamanho de APK inválido.");
            File current=cachedVerified(ctx);
            if(current!=null && tag.equals(ctx.getSharedPreferences(ScanCoordinator.PREFS,0).getString(PREF_TAG,""))){
                status(ctx,"Shizuku "+tag+" baixado e pronto para instalar.");return true;
            }
            File dir=new File(ctx.getFilesDir(),"updates");
            if(!dir.exists() && !dir.mkdirs())throw new IOException("Não foi possível preparar pasta de atualizações.");
            File tmp=new File(dir,"shizuku-release.tmp.apk");
            File apk=new File(dir,"shizuku-release.apk");
            String url=selected.optString("browser_download_url","");
            String digest=selected.optString("digest","");
            try {
                download(url,tmp,size);
                String hash=sha256(tmp);
                if(digest.startsWith("sha256:") && !digest.equalsIgnoreCase("sha256:"+hash))
                    throw new SecurityException("SHA-256 não corresponde ao publicado no GitHub.");
                if(!digest.isEmpty() && !"null".equals(digest) && !digest.startsWith("sha256:"))
                    throw new SecurityException("Algoritmo de digest do GitHub inesperado.");
                PackageInfo archive=archive(ctx,tmp);
                if(archive==null || !PACKAGE.equals(archive.packageName))
                    throw new SecurityException("O APK não pertence ao Shizuku oficial.");
                if(code(archive)<=code(installed)){
                    status(ctx,"Shizuku já está atualizado ("+installed.versionName+").");return false;
                }
                if(!sameSigner(installed,archive))
                    throw new SecurityException("Assinatura do APK diferente da versão instalada. Download rejeitado.");
                if(apk.exists() && !apk.delete())throw new IOException("Falha ao substituir arquivo anterior.");
                if(!tmp.renameTo(apk))throw new IOException("Falha ao concluir download.");
                ctx.getSharedPreferences(ScanCoordinator.PREFS,0).edit()
                    .putString(PREF_HASH,hash).putLong(PREF_CODE,code(archive))
                    .putString(PREF_TAG,tag).apply();
                status(ctx,"Shizuku "+tag+" baixado. Instalação pendente.");
                return true;
            } finally {if(tmp.exists())tmp.delete();}
        }
    }
    static File cachedVerified(Context ctx) throws Exception {
        synchronized(LOCK){
            SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,0);
            String expected=sp.getString(PREF_HASH,"");
            if(expected.length()!=64)return null;
            File apk=new File(new File(ctx.getFilesDir(),"updates"),"shizuku-release.apk");
            if(!apk.isFile() || apk.length()<50*1024 || apk.length()>MAX_APK)return null;
            PackageInfo installed=installed(ctx);
            PackageInfo archive=archive(ctx,apk);
            if(installed==null || archive==null || !PACKAGE.equals(archive.packageName) ||
                code(archive)<=code(installed) || code(archive)!=sp.getLong(PREF_CODE,-1) ||
                !sameSigner(installed,archive) || !expected.equalsIgnoreCase(sha256(apk)))return null;
            return apk;
        }
    }
    /** An in-flight manager update can sever its own Binder after the package commit. */
    static boolean stagedVersionInstalled(Context ctx){
        PackageInfo pkg=installed(ctx);
        String tag=ctx.getSharedPreferences(ScanCoordinator.PREFS,0).getString(PREF_TAG,"");
        if(pkg==null || !ShizukuUpdatePolicy.isStableTag(tag))return false;
        return !ShizukuUpdatePolicy.newerThan(tag,pkg.versionName);
    }
    static PackageInfo installed(Context ctx){
        try {return ctx.getPackageManager().getPackageInfo(PACKAGE,signingFlag());}
        catch(PackageManager.NameNotFoundException e){return null;}
    }
    private static PackageInfo archive(Context ctx,File apk){
        return ctx.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),signingFlag());
    }
    private static int signingFlag(){
        return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
    }
    private static Signature[] signatures(PackageInfo pkg){
        if(pkg==null)return null;
        if(Build.VERSION.SDK_INT>=28 && pkg.signingInfo!=null)return pkg.signingInfo.getApkContentsSigners();
        return pkg.signatures;
    }
    private static boolean sameSigner(PackageInfo a,PackageInfo b){
        Signature[] x=signatures(a), y=signatures(b);
        if(x==null || y==null || x.length==0 || x.length!=y.length)return false;
        for(Signature s:x){
            boolean found=false;
            for(Signature t:y)if(MessageDigest.isEqual(s.toByteArray(),t.toByteArray())){found=true;break;}
            if(!found)return false;
        }
        return true;
    }
    private static long code(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    private static String readJson(String url) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent","MLOG-Scanner-Bridge");
        c.setRequestProperty("Accept","application/vnd.github+json");
        try {
            if(c.getResponseCode()!=200)throw new IOException("GitHub HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>1024*1024)throw new IOException("Resposta muito grande.");}
                return out.toString("UTF-8");
            }
        } finally{c.disconnect();}
    }
    private static void download(String url,File file,long reportedSize) throws Exception {
        if(!url.startsWith(PREFIX))throw new SecurityException("URL de APK não oficial.");
        URL next=new URL(url);
        for(int step=0;step<6;step++){
            if(!"https".equalsIgnoreCase(next.getProtocol()))throw new SecurityException("Download sem HTTPS.");
            String host=next.getHost().toLowerCase(Locale.ROOT);
            if(!(host.equals("github.com") || host.equals("release-assets.githubusercontent.com") ||
                 host.equals("objects.githubusercontent.com") || host.equals("github-releases.githubusercontent.com")))
                throw new SecurityException("Redirecionamento para host não autorizado.");
            HttpURLConnection c=(HttpURLConnection)next.openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","MLOG-Scanner-Bridge");
            try {
                int http=c.getResponseCode();
                if(http==301 || http==302 || http==303 || http==307 || http==308){
                    String location=c.getHeaderField("Location");
                    if(location==null)throw new IOException("GitHub não retornou URL do arquivo.");
                    next=new URL(next,location);continue;
                }
                if(http!=200)throw new IOException("APK HTTP "+http);
                long total=0;
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(file)){
                    byte[] buf=new byte[16*1024];int n;
                    while((n=in.read(buf))!=-1){
                        total+=n;if(total>MAX_APK)throw new IOException("APK excedeu o limite de tamanho.");
                        out.write(buf,0,n);
                    }
                    out.getFD().sync();
                }
                if(total!=reportedSize)throw new IOException("Download incompleto (tamanho divergente).");
                return;
            } finally {c.disconnect();}
        }
        throw new IOException("Redirecionamentos demais no GitHub.");
    }
    private static String sha256(File file) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(file)){
            byte[] buf=new byte[16384];int n;
            while((n=in.read(buf))!=-1)md.update(buf,0,n);
        }
        StringBuilder s=new StringBuilder(64);
        for(byte b:md.digest())s.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return s.toString();
    }
    private static String brief(Exception e){
        String message=e.getMessage();
        return message==null?e.getClass().getSimpleName():message;
    }
}
