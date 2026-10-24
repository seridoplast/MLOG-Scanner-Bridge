package br.com.seridoplast.mlogbridge;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/** Checks signed Bridge updates on the company LAN; never installs silently. */
public final class BridgeUpdater {
 private static final String MANIFEST="http://192.168.0.134:8080/bridge-updates/update.json";
 private static final String CHANNEL="bridge_updates";
 private BridgeUpdater() {}
 public static void schedule(Context ctx) {
  AlarmManager am=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
  Intent i=new Intent(ctx,BridgeUpdateReceiver.class);
  PendingIntent pi=PendingIntent.getBroadcast(ctx,771,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
      SystemClock.elapsedRealtime()+15*60*1000L,6*60*60*1000L,pi);
 }
 public static void check(Context ctx) {
  HttpURLConnection c=null;
  try {
   c=open(MANIFEST); if(c.getResponseCode()!=200)return;
   byte[] data=read(c.getInputStream(),16384);JSONObject meta=new JSONObject(new String(data,"UTF-8"));
   int latest=meta.getInt("versionCode");
   PackageInfo own=ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0);
   long installed=Build.VERSION.SDK_INT>=28?own.getLongVersionCode():own.versionCode;
   if(latest<=installed)return;
   String url=meta.getString("apkUrl");
   if(!url.startsWith("http://192.168.0.134:8080/bridge-updates/"))return;
   String expected=meta.getString("sha256");
   if(!expected.matches("[a-fA-F0-9]{64}"))return;
   c.disconnect();c=open(url);if(c.getResponseCode()!=200)return;
   File folder=new File(ctx.getFilesDir(),"updates");if(!folder.exists()&&!folder.mkdirs())return;
   File tmp=new File(folder,"bridge-download.tmp");
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)) {
    byte[] b=new byte[16384];int n;long total=0;
    while((n=in.read(b))!=-1){total+=n;if(total>100*1024*1024L)throw new Exception("APK muito grande");digest.update(b,0,n);out.write(b,0,n);}
   }
   StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   if(!hash.toString().equalsIgnoreCase(expected)){tmp.delete();return;}
   File apk=new File(folder,"bridge-update.apk");if(apk.exists()&&!apk.delete())return;
   if(!tmp.renameTo(apk))return;
   Uri uri=FileProvider.getUriForFile(ctx,ctx.getPackageName()+".updates",apk);
   Intent install=new Intent(Intent.ACTION_VIEW);
   install.setDataAndType(uri,"application/vnd.android.package-archive");
   install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
   PendingIntent action=PendingIntent.getActivity(ctx,772,install,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
   if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Atualizações Scanner Bridge",NotificationManager.IMPORTANCE_HIGH));
   Notification.Builder nb=Build.VERSION.SDK_INT>=26?new Notification.Builder(ctx,CHANNEL):new Notification.Builder(ctx);
   nb.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Atualização do Scanner Bridge")
     .setContentText("Nova versão disponível. Toque para instalar.").setContentIntent(action).setAutoCancel(true);
   nm.notify(773,nb.build());
  }catch(Exception ignored) { /* Falhas de rede são esperadas quando o servidor está desligado. */ }
  finally {if(c!=null)c.disconnect();}
 }
 private static HttpURLConnection open(String url)throws Exception {
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
  c.setConnectTimeout(7000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);return c;
 }
 private static byte[] read(InputStream in,int max)throws Exception {
  try(InputStream input=in;java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
   byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if(out.size()+n>max)throw new Exception("Resposta grande");out.write(b,0,n);}return out.toByteArray();
  }
 }
}
