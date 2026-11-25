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
    private static final String HOST="local_adb_host_v3620";
    private static final String TAG="BridgeLocalADB";
    private static final Object PAIR_LOCK=new Object();
    private static final ScheduledExecutorService WATCH=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"bridge-adb-watch");t.setDaemon(true);return t;});
    private static volatile PairAttempt pairing;
    private static final String KEY="bridge_adb_wrap_v3619";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"bridge-local-adb");t.setDaemon(true);return t;});
    private static final AtomicBoolean checking=new AtomicBoolean();
    private static volatile Manager manager;
    private static volatile boolean connected;
    private static final AdbConnectionHealth HEALTH=new AdbConnectionHealth();
    private static volatile boolean validateRequested;
    private static volatile long lastCheck;
    private static volatile String status="Não pareado";
    private LocalAdbBridge(){}
    static boolean enabled(Context c){return Build.VERSION.SDK_INT>=30 && prefs(c).getBoolean(PREF,false);}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);}
    static boolean ready(){return connected;}
    static String status(){return status;}
    static void tick(Context context){
        Context c=context.getApplicationContext();
        if(!enabled(c) || pairing!=null)return;
        AdbNetworkWatch.start(c);
        long now=SystemClock.elapsedRealtime();
        if(lastCheck!=0 && now-lastCheck<(connected?30_000L:HEALTH.retryDelay()))return;
        if(!checking.compareAndSet(false,true))return;
        lastCheck=now;
        IO.execute(()->{try{ensure(c);}catch(Exception | LinkageError ex){if(pairing==null)connectionFailure(ex);}finally{try{if(enabled(c) && pairing==null){if(!connected){HEALTH.retryFailed();lastCheck=SystemClock.elapsedRealtime();}AdbConnectionNotice.update(c,HEALTH,connected);}}finally{checking.set(false);}}});
    }
    static void retry(Context c){lastCheck=0;tick(c);}
    static boolean checking(){return checking.get() || pairing!=null;}
    // Only fixed permission grants, after consent and verified UID 2000. No LAN command path.
    private static void provisionRecovery(Context c,Manager m){
        if(!AdbWifiRecovery.shouldProvision(c))return;
        try{
            if(AdbWifiRecovery.provisionConsent(c) && !AdbWifiRecovery.hasGrant(c))command(m,"/system/bin/pm grant br.com.seridoplast.mlogbridge android.permission.WRITE_SECURE_SETTINGS",5000);
            if(AdbWifiRecovery.provisionConsent(c) && !AdbWifiRecovery.hasBackground(c) && c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)
                command(m,"/system/bin/pm grant br.com.seridoplast.mlogbridge android.permission.ACCESS_BACKGROUND_LOCATION",5000);
        }catch(Exception ex){android.util.Log.w(TAG,"Autorização recuperação: "+ex.getClass().getSimpleName());}
        finally{AdbWifiRecovery.provisionResult(c);}
    }
    static void disable(Context context){
        synchronized(PAIR_LOCK){
            PairAttempt current=pairing;
            if(current!=null)finishPair(current,false,"Pareamento cancelado");
            prefs(context).edit().putBoolean(PREF,false).apply();connected=false;status="Desativado";
            HEALTH.reset();validateRequested=false;AdbConnectionNotice.clear(context);
        }
        IO.execute(()->{try{if(manager!=null)manager.disconnect();}catch(Exception ignored){}});
    }
    interface PairCallback{void done(boolean ok,String message);}
    private static final class PairAttempt {
        final PairCallback cb;
        volatile LocalAdbPairTransport transport;
        volatile ScheduledFuture<?> watchdog;
        PairAttempt(PairCallback cb){this.cb=cb;}
    }
    static boolean pairing(){return pairing!=null;}
    private static void finishPair(PairAttempt attempt,boolean ok,String message){
        synchronized(PAIR_LOCK){
            if(pairing!=attempt)return;
            pairing=null;status=message;
            if(!ok)connected=false;
            if(attempt.watchdog!=null)attempt.watchdog.cancel(false);
            if(attempt.transport!=null)attempt.transport.close();
        }
        attempt.cb.done(ok,message);
    }
    static void pair(Context context,String host,int port,String code,PairCallback cb){
        Context c=context.getApplicationContext();
        if(Build.VERSION.SDK_INT<30 || port<1 || port>65535 || code==null || !code.matches("[0-9]{6}")){cb.done(false,"Informe a porta de pareamento e o código de seis dígitos.");return;}
        final PairAttempt attempt=new PairAttempt(cb);
        synchronized(PAIR_LOCK){
            if(pairing!=null){cb.done(false,"Já existe uma tentativa de pareamento.");return;}
            pairing=attempt;connected=false;status="Pareando ADB (até 25 segundos)";
            HEALTH.reset();validateRequested=false;AdbConnectionNotice.clear(c);
            attempt.watchdog=WATCH.schedule(()->finishPair(attempt,false,"Tempo de pareamento esgotado. Gere um novo código."),25,TimeUnit.SECONDS);
        }
        IO.execute(()->{
            if(pairing!=attempt)return;
            try{
                Manager m=get(c);m.disconnect();
                if(pairing!=attempt)return;
                try(LocalAdbPairTransport transport=new LocalAdbPairTransport(host,port,18_000L)){
                    synchronized(PAIR_LOCK){if(pairing!=attempt)return;attempt.transport=transport;status="Pareando: autenticando com o Android";}
                    android.util.Log.i(TAG,"Pareamento: transporte local aberto; iniciando TLS");
                    boolean ok;
                    try{ok=m.pair("127.0.0.1",transport.port(),code);}
                    catch(Exception | LinkageError ex){
                        if(transport.timedOut())throw new java.net.SocketTimeoutException("Tempo de pareamento esgotado");
                        throw ex;
                    }
                    if(!ok || transport.timedOut())throw new IOException("Pareamento recusado ou expirado");
                }
                synchronized(PAIR_LOCK){
                    if(pairing!=attempt)return;
                    prefs(c).edit().putBoolean(PREF,true).remove(PORT).remove(HOST).apply();
                    android.util.Log.i(TAG,"Pareamento concluído; iniciando descoberta de conexão");
                    lastCheck=0;finishPair(attempt,true,"Pareado; procurando porta de conexão");
                }
                try{ensure(c);}catch(Exception | LinkageError ex){connectionFailure(ex);}finally{if(enabled(c) && pairing==null)AdbConnectionNotice.update(c,HEALTH,connected);}
            }catch(Exception | LinkageError ex){
                // Never log the pairing code, credentials or arbitrary exception messages.
                android.util.Log.w(TAG,"Pareamento falhou: "+ex.getClass().getSimpleName()+"; causa="+(ex.getCause()==null?"-":ex.getCause().getClass().getSimpleName()));
                String message=ex instanceof java.net.SocketTimeoutException?"Tempo de pareamento esgotado. Gere um novo código.":ex instanceof java.net.ConnectException?"Porta recusada. Confira a porta da janela de pareamento ou use IP:porta.":"Pareamento falhou: "+ex.getClass().getSimpleName()+". Gere um novo código.";
                finishPair(attempt,false,message);
            }
        });
    }
    static void setConnectionPort(Context c,String host,int port)throws IOException{
        if(port<1 || port>65535)throw new IOException("Porta inválida");
        LocalAdbPairTransport.localAddress(host);
        if(!enabled(c))throw new IOException("Pareie o ADB primeiro");
        prefs(c).edit().putInt(PORT,port).putString(HOST,host).apply();retry(c);
    }
    private static void connectionFailure(Throwable ex){
        android.util.Log.w(TAG,"Conexão falhou: "+ex.getClass().getSimpleName());
        failed("Pareado; conexão indisponível ("+ex.getClass().getSimpleName()+"). Informe a porta de conexão.");
    }
    private static boolean verifiedShell(Manager m)throws Exception{
        // Short non-interactive shell, no PTY prompt or echoed command.
        AdbStream stream=m.openStream("shell:/system/bin/id -u");
        try{return readUntil(stream,"(?:^|\\n)2000\\n",5_000).contains("\n2000\n");}
        finally{try{stream.close();}catch(IOException ignored){}}
    }
    private static void markConnected(Context c,Manager m){
        connected=enabled(c)&&pairing==null;
        if(connected){validateRequested=false;HEALTH.healthy();status="Conectado";provisionRecovery(c,m);}
    }
    private static void ensure(Context c)throws Exception{
        if(!enabled(c) || pairing!=null){connected=false;return;}
        try{AdbWifiRecovery.migrateLegacy(c);}catch(RuntimeException ex){android.util.Log.w(TAG,"Cadastro Wi-Fi indisponível");}
        Manager m=manager;
        // Preserve the already authenticated transport. A network callback or a
        // periodic tick is not a reason to open a shell or reconnect it.
        if(m!=null && m.isConnected() && connected){
            if(!validateRequested){status="Conectado";provisionRecovery(c,m);return;}
            // An actual failed operation requests validation, not a replay.
            boolean ok=false;
            try{ok=verifiedShell(m);}catch(Exception ex){android.util.Log.w(TAG,"Validação ADB: "+ex.getClass().getSimpleName());}
            if(ok){markConnected(c,m);return;}
            if(m.isConnected() && !HEALTH.probeFailed()){
                status="Conectado · verificando resposta do ADB";
                // A second independent validation is scheduled on the next tick.
                lastCheck=0;return;
            }
        }
        if(connected){HEALTH.lost(SystemClock.elapsedRealtime());connected=false;}
        validateRequested=false;
        if(m!=null){try{m.disconnect();}catch(Exception ignored){}}
        if(!AdbWifiRecovery.wifiDebugOn(c)){
            AdbWifiRecovery.recoverIfNeeded(c);
            status="Depuração Wi-Fi desligada · "+AdbWifiRecovery.status(c);return;
        }
        if(!wifiAvailable(c)){status="Aguardando conexão Wi-Fi";return;}
        m=get(c);
        status="Procurando ADB sem fio";
        int old=prefs(c).getInt(PORT,0);
        if(old>0){try{String host=prefs(c).getString(HOST,"127.0.0.1");LocalAdbPairTransport.localAddress(host);m.connect(host,old);}catch(Exception ignored){m.disconnect();}}
        if(!m.isConnected()){
            java.net.InetSocketAddress discovered=discover(c);
            if(discovered==null)throw new IOException("Porta ADB não anunciada");
            String host=discovered.getAddress().getHostAddress();
            m.disconnect();m.connect(host,discovered.getPort());
            if(m.isConnected())prefs(c).edit().putInt(PORT,discovered.getPort()).putString(HOST,host).apply();
        }
        if(!m.isConnected() || !verifiedShell(m))throw new IOException("ADB não autorizado");
        markConnected(c,m);
    }
    private static boolean wifiAvailable(Context c){
        try{
            android.net.ConnectivityManager cm=c.getSystemService(android.net.ConnectivityManager.class);
            if(cm==null)return true;
            for(android.net.Network n:cm.getAllNetworks()){
                android.net.NetworkCapabilities cap=cm.getNetworkCapabilities(n);
                if(cap!=null && cap.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI))return true;
            }
            return false;
        }catch(RuntimeException ex){return true;}
    }
    private static java.net.InetSocketAddress discover(Context c)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<java.net.InetSocketAddress> endpoint=new java.util.concurrent.atomic.AtomicReference<>();
        AdbMdns mdns=new AdbMdns(c,AdbMdns.SERVICE_TYPE_TLS_CONNECT,(address,p)->{
            try{if(address instanceof java.net.Inet4Address && p>0 && p<=65535 && (address.isLoopbackAddress() || NetworkInterface.getByInetAddress(address)!=null)){endpoint.compareAndSet(null,new java.net.InetSocketAddress(address,p));latch.countDown();}}catch(Exception ignored){}
        });
        try{mdns.start();latch.await(8,TimeUnit.SECONDS);return endpoint.get();}finally{mdns.stop();}
    }
    private static void failed(String message){if(connected)HEALTH.lost(SystemClock.elapsedRealtime());connected=false;validateRequested=false;if(pairing==null)status=message;try{if(manager!=null)manager.disconnect();}catch(Exception ignored){}}
    private static Manager get(Context c)throws Exception{if(manager==null)manager=new Manager(c);return manager;}
    private static <T> T submit(Callable<T> task,long ms)throws Exception{
        AtomicBoolean began=new AtomicBoolean();
        Future<T> f=IO.submit(()->{began.set(true);return task.call();});
        try{return f.get(ms,TimeUnit.MILLISECONDS);}
        catch(TimeoutException | InterruptedException ex){f.cancel(true);if(began.get())requestValidation();if(ex instanceof InterruptedException)Thread.currentThread().interrupt();throw ex;}
    }
    private static void requestValidation(){if(connected){validateRequested=true;lastCheck=0;}}
    private static void operationFailure(String message){
        Manager m=manager;
        if(m==null || !m.isConnected())failed(message);
        else {status="Conectado · operação sem confirmação";requestValidation();}
    }
    /** One atomic choice of backend for text+Enter; never replay uncertain operations. */
    static boolean scan(String code)throws Exception{
        if(!AdbScanMode.supported(code))return false;
        return execute("/system/bin/input text '"+code+"' && sleep 0.11 && /system/bin/input keyevent 66",6000);
    }
    static boolean volume(int x,int y,int pause)throws Exception{
        if(x<1||x>4096||y<1||y>4096||pause<1||pause>99)return false;
        return execute(VolumeRecoveryTarget.shellSequence(x,y,pause),6000);
    }
    private static boolean execute(String fixed,long ms)throws Exception{
        if(!ready())return false;
        return submit(()->{if(!ready())return false;try{return command(manager,fixed,ms).contains("\n__BRIDGE_RC_0\n");}catch(Exception ex){operationFailure("ADB desconectado");throw ex;}},ms+1500);
    }
    // Legacy shell streams can throw IOException at normal EOF. Only accept explicit exit marker.
    private static String command(Manager m,String fixed,long ms)throws Exception{
        if(m==null)throw new IOException("ADB ausente");
        // Send the command over the stream instead of putting it in the OPEN destination.
        AdbStream s=m.openStream("shell:");
        try{
            OutputStream out=s.openOutputStream();
            out.write((fixed+"; printf '\\n__BRIDGE_RC_%s\\n' $?; exit\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readUntil(s,"(?:^|\\n)__BRIDGE_RC_[0-9]+\\n",ms);
        }finally{try{s.close();}catch(IOException ignored){/* A confirmed exit marker remains valid. */}}
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
            }catch(Exception ex){operationFailure("Instalação ADB sem confirmação");throw ex;}
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
