package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;
import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Opt-in, local-device only ADB. Never accepts shell commands or APK URLs from LAN. */
final class LocalAdbBridge {
    static final String PREF="local_adb_enabled_v3619";
    private static final String PORT="local_adb_port_v3619";
    private static final String KEY="bridge_adb_wrap_v3619";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"bridge-local-adb");t.setDaemon(true);return t;});
    private static final AtomicBoolean checking=new AtomicBoolean();
    private static volatile Manager manager;
    private static volatile boolean connected;
    private static volatile long lastCheck;
    private static volatile String status="Não pareado";
    private LocalAdbBridge(){}
    static boolean enabled(Context c){return Build.VERSION.SDK_INT>=30 && prefs(c).getBoolean(PREF,false);}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);}
    static boolean ready(){return connected;}
    static String status(){return status;}
    static void tick(Context context){
        Context c=context.getApplicationContext();
        if(!enabled(c))return;
        long now=SystemClock.elapsedRealtime();
        if(lastCheck!=0 && now-lastCheck<30_000L)return;
        if(!checking.compareAndSet(false,true))return;
        lastCheck=now;
        IO.execute(()->{try{ensure(c);}catch(Exception | LinkageError ex){failed("ADB indisponível: "+ex.getClass().getSimpleName());}finally{checking.set(false);}});
    }
    static void retry(Context c){lastCheck=0;tick(c);}
    static void disable(Context context){prefs(context).edit().putBoolean(PREF,false).apply();connected=false;status="Desativado";IO.execute(()->{try{if(manager!=null)manager.disconnect();}catch(Exception ignored){}});}
    interface PairCallback{void done(boolean ok,String message);}
    static void pair(Context context,int port,String code,PairCallback cb){
        Context c=context.getApplicationContext();
        if(Build.VERSION.SDK_INT<30 || port<1 || port>65535 || code==null || !code.matches("[0-9]{6}")){cb.done(false,"Informe a porta de pareamento e o código de seis dígitos.");return;}
        IO.execute(()->{try{
            Manager m=get(c);connected=false;m.disconnect();
            if(!m.pair("127.0.0.1",port,code))throw new IOException("Pareamento recusado");
            prefs(c).edit().putBoolean(PREF,true).remove(PORT).apply();
            lastCheck=0;status="Pareado; procurando porta de conexão";
            cb.done(true,status);
            try{ensure(c);}catch(Exception ex){failed("Pareado; aguardando ADB sem fio");}
        }catch(Exception | LinkageError ex){failed("Pareamento não concluído: "+ex.getClass().getSimpleName());cb.done(false,status);}});
    }
    static void setConnectionPort(Context c,int port){if(port<1 || port>65535)return;prefs(c).edit().putInt(PORT,port).apply();retry(c);}
    private static void ensure(Context c)throws Exception{
        if(!enabled(c)){connected=false;return;}
        Manager m=get(c);
        if(m.isConnected()){
            if(command(m,"/system/bin/id -u",3_000).contains("\n2000\n")){connected=enabled(c);status="Conectado";return;}
            m.disconnect();
        }
        connected=false;status="Procurando ADB sem fio";
        int old=prefs(c).getInt(PORT,0);
        if(old>0){try{m.connect("127.0.0.1",old);}catch(Exception ignored){m.disconnect();}}
        if(!m.isConnected()){
            int discovered=discover(c);
            if(discovered<=0)throw new IOException("Porta ADB não anunciada");
            m.disconnect();m.connect("127.0.0.1",discovered);
            if(m.isConnected())prefs(c).edit().putInt(PORT,discovered).apply();
        }
        if(!m.isConnected() || !command(m,"/system/bin/id -u",3_000).contains("\n2000\n"))throw new IOException("ADB não autorizado");
        connected=enabled(c);status=connected?"Conectado":"Desativado";
    }
    private static int discover(Context c)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);AtomicInteger port=new AtomicInteger(-1);
        AdbMdns mdns=new AdbMdns(c,AdbMdns.SERVICE_TYPE_TLS_CONNECT,(address,p)->{
            try{if(address!=null && p>0 && p<=65535 && (address.isLoopbackAddress() || NetworkInterface.getByInetAddress(address)!=null)){port.set(p);latch.countDown();}}catch(Exception ignored){}
        });
        try{mdns.start();latch.await(8,TimeUnit.SECONDS);return port.get();}finally{mdns.stop();}
    }
    private static void failed(String message){connected=false;status=message;try{if(manager!=null)manager.disconnect();}catch(Exception ignored){}}
    private static Manager get(Context c)throws Exception{if(manager==null)manager=new Manager(c);return manager;}
    private static <T> T submit(Callable<T> task,long ms)throws Exception{
        Future<T> f=IO.submit(task);
        try{return f.get(ms,TimeUnit.MILLISECONDS);}catch(Exception ex){f.cancel(true);connected=false;status="Conexão ADB interrompida; reconectando";lastCheck=0;throw ex;}
    }
    /** One atomic choice of backend for text+Enter; never replay uncertain operations. */
    static boolean scan(String code)throws Exception{
        if(!ShizukuScanMode.supported(code))return false;
        return execute("/system/bin/input text '"+code+"' && sleep 0.11 && /system/bin/input keyevent 66",6000);
    }
    static boolean volume(int x,int y,int pause)throws Exception{
        if(x<1||x>4096||y<1||y>4096||pause<1||pause>99)return false;
        return execute(VolumeRecoveryTarget.shellSequence(x,y,pause),6000);
    }
    private static boolean execute(String fixed,long ms)throws Exception{
        if(!ready())return false;
        return submit(()->{if(!ready())return false;try{return command(manager,fixed,ms).contains("\n__BRIDGE_RC_0\n");}catch(Exception ex){failed("ADB desconectado");throw ex;}},ms+1500);
    }
    // Legacy shell streams can throw IOException at normal EOF. Only accept explicit exit marker.
    private static String command(Manager m,String fixed,long ms)throws Exception{
        if(m==null)throw new IOException("ADB ausente");
        // Send the command over the stream instead of putting it in the OPEN destination.
        try(AdbStream s=m.openStream("shell:")){
            OutputStream out=s.openOutputStream();
            out.write((fixed+"; printf '\\n__BRIDGE_RC_%s\\n' $?; exit\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readUntil(s,"(?:^|\\n)__BRIDGE_RC_[0-9]+\\n",ms);
        }
    }
    private static String readUntil(AdbStream s,String marker,long ms)throws Exception{
        java.util.regex.Pattern pattern=java.util.regex.Pattern.compile(marker);
        long deadline=SystemClock.elapsedRealtime()+ms;ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];
        while(SystemClock.elapsedRealtime()<deadline){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            int available;try{available=s.available();}catch(IOException e){break;}
            if(available<=0){if(s.isClosed())break;Thread.sleep(15);continue;}
            int n=s.read(buf,0,Math.min(buf.length,available));if(n<=0)break;
            bytes.write(buf,0,n);if(bytes.size()>65536)throw new IOException("Resposta ADB excedida");
            String out=bytes.toString("UTF-8").replace("\r","");
            if(pattern.matcher(out).find())return "\n"+out;
        }
        String out=bytes.toString("UTF-8").replace("\r","");
        if(pattern.matcher(out).find())return "\n"+out;
        throw new IOException("ADB sem confirmação");
    }
    /** Only the updater calls this, after package/version/pinned signer verification. */
    static boolean installBridge(File apk)throws Exception{
        if(!ready() || apk==null || !apk.isFile())return false;
        long size=apk.length();if(size<51200 || size>120L*1024*1024)return false;
        return submit(()->{
            try(AdbStream stream=manager.openStream("exec:/system/bin/pm install -r -S "+size);InputStream in=new FileInputStream(apk)){
                OutputStream out=stream.openOutputStream();byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();out.write(buf,0,n);}
                out.flush();return readUntil(stream,"(?:^|\\n)Success\\n",120_000L).contains("\nSuccess\n");
            }catch(Exception ex){failed("Instalação ADB sem confirmação");throw ex;}
        },180_000L);
    }
    /** A software RSA identity is required by TLS; encrypted at rest using AndroidKeyStore AES. */
    private static final class Manager extends AbsAdbConnectionManager{
        private final PrivateKey key;private final Certificate cert;
        Manager(Context c)throws Exception{
            setApi(Build.VERSION.SDK_INT);setTimeout(5,TimeUnit.SECONDS);setThrowOnUnauthorised(true);
            KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
            if(!ks.containsAlias(KEY)){
                KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
                gen.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());gen.generateKey();
            }
            SecretKey wrapping=(SecretKey)ks.getKey(KEY,null);
            android.util.AtomicFile file=new android.util.AtomicFile(new File(c.getNoBackupFilesDir(),"adb_identity_v3619.bin"));
            if(file.getBaseFile().exists()){
                byte[] blob=file.readFully();if(blob.length<29 || blob.length>16384)throw new IOException("Identidade ADB inválida");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,wrapping,new GCMParameterSpec(128,blob,0,12));
                try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(cipher.doFinal(blob,12,blob.length-12)))){
                    int n=in.readInt();if(n<=0||n>8192)throw new IOException("Chave inválida");byte[] k=new byte[n];in.readFully(k);
                    key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(k));cert=CertificateFactory.getInstance("X.509").generateCertificate(in);
                }
            }else{
                KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);KeyPair pair=gen.generateKeyPair();key=pair.getPrivate();
                X500Name name=new X500Name("CN=MLOG Bridge local");long now=System.currentTimeMillis();
                cert=new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(name,new BigInteger(120,new SecureRandom()).add(BigInteger.ONE),new Date(now-86400000L),new Date(now+10L*365*86400000L),name,pair.getPublic()).build(new JcaContentSignerBuilder("SHA256withRSA").build(key)));
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(bytes);byte[] pk=key.getEncoded();data.writeInt(pk.length);data.write(pk);data.write(cert.getEncoded());data.flush();
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,wrapping);byte[] encrypted=cipher.doFinal(bytes.toByteArray());
                FileOutputStream out=file.startWrite();try{out.write(cipher.getIV());out.write(encrypted);file.finishWrite(out);}catch(Exception ex){file.failWrite(out);throw ex;}
            }
        }
        @Override protected PrivateKey getPrivateKey(){return key;}
        @Override protected Certificate getCertificate(){return cert;}
        @Override protected String getDeviceName(){return "MLOG Scanner Bridge";}
    }
}
