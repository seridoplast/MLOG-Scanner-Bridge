package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.net.wifi.WifiManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;

/** LAN-only observation sync. Never blocks barcode injection or uses GitHub API. */
final class OrderNotesClient {
    static final String PREF_HOST="order_notes_host_v369";
    static final String PREF_KEY="order_notes_token_v369";
    static final String PREF_DEVICE_NAME="order_notes_device_name_v369";
    static final String PREF_DEVICE_ID="order_notes_device_id_v3610";
    private static final String DISCOVER="SERIDOPLAST_NOTES_DISCOVER_V1";
    private static final int DISCOVER_PORT=8766;
    private static final String BEACON_GROUP="239.255.42.99";
    private static final int BEACON_PORT=8767;
    private static final String BEACON="SERIDOPLAST_NOTES_BEACON_V1";
    private static final AtomicBoolean BEACON_LISTENING=new AtomicBoolean(false);
    private static volatile Thread beaconThread;
    private static volatile MulticastSocket beaconSocket;
    private static volatile long lastGoodAt=0L;
    private static volatile long lastDiscoveryAt=0L;
    // Five-minute backoff when the PC is unavailable. First attempt stays immediate.
    private static boolean canDiscoverAgain(){
        return NotesDiscoveryRetryPolicy.ready(SystemClock.elapsedRealtime(),lastDiscoveryAt);
    }
    private static volatile String lastProblem="Aguardando descoberta da rede.";

    private static volatile String LAST_ORDER="";
    static final class Broadcast {
        final String id,text;
        Broadcast(String id,String text){this.id=id;this.text=text;}
    }
    static final class Note {
        final String id,order,text;
        Note(String id,String order,String text){this.id=id;this.order=order;this.text=text;}
    }
    interface Callback { void done(boolean ok,String message); }
    private static final ExecutorService IO=Executors.newFixedThreadPool(2);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final AtomicInteger GENERATION=new AtomicInteger();
    private static String base(Context c){return c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).getString(PREF_HOST,"").trim();}
    private static String token(Context c){return c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).getString(PREF_KEY,"").trim();}
    static boolean configured(Context c){return validHost(base(c)) && token(c).matches("[a-fA-F0-9]{64}");}
    private static String deviceId(Context c){
        SharedPreferences preferences=c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        String id=preferences.getString(PREF_DEVICE_ID,"");
        if(id.matches("[0-9a-f]{32}"))return id;
        id=UUID.randomUUID().toString().replace("-","");
        preferences.edit().putString(PREF_DEVICE_ID,id).apply();return id;
    }
    private static String deviceName(Context c){
        String custom=c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).getString(PREF_DEVICE_NAME,"").trim();
        if(!custom.isEmpty())return custom;
        String model=Build.MODEL==null?"Coletor":Build.MODEL;
        return (model.length()>55?model.substring(0,55):model)+" · "+deviceId(c).substring(28);
    }
    static void reconnect(Context c){ reconnect(c,null); }
    /** Informa o resultado real, sem esperar um tempo fixo que pode terminar cedo. */
    static void reconnect(Context c,Callback callback){
        Context app=c.getApplicationContext();
        IO.execute(()->{
            boolean found=discover(app,true,null);
            if(callback!=null){
                String message=found?"Conectado: "+base(app):"Não encontrou o painel. "+lastProblem;
                MAIN.post(()->callback.done(found,message));
            }
        });
    }
    static String connectionStatus(Context c){
        if(configured(c)&&lastGoodAt>0 && SystemClock.elapsedRealtime()-lastGoodAt<45000L)
            return "Conectado: "+base(c);
        return "Desconectado · "+lastProblem;
    }
    /** Escuta anúncios sem credenciais; descobre o endereço novo por UDP unicast. */
    static void startBeaconListener(Context c){
        if(!BEACON_LISTENING.compareAndSet(false,true))return;
        final Context app=c.getApplicationContext();
        Thread t=new Thread(()->{
            WifiManager.MulticastLock lock=null;
            try{
                WifiManager wifi=(WifiManager)app.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if(wifi!=null){
                    lock=wifi.createMulticastLock("mlog-bridge-observacoes");
                    lock.setReferenceCounted(false);
                    lock.acquire();
                }
                try(MulticastSocket udp=new MulticastSocket(BEACON_PORT)){
                    beaconSocket=udp;
                    udp.setSoTimeout(3500);
                    InetAddress group=InetAddress.getByName(BEACON_GROUP);
                    udp.joinGroup(group);
                    while(BEACON_LISTENING.get()){
                        byte[] buf=new byte[128];
                        DatagramPacket packet=new DatagramPacket(buf,buf.length);
                        try{udp.receive(packet);}catch(SocketTimeoutException e){continue;}
                        String message=new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8);
                        if(!BEACON.equals(message))continue;
                        String sender=packet.getAddress().getHostAddress();
                        if(!validHost("http://"+sender+":8765"))continue;
                        long now=SystemClock.elapsedRealtime();
                        if(lastGoodAt>0 && now-lastGoodAt<45000L)continue;
                        String expected="http://"+sender+":8765";
                        // New PC IP is handled immediately; same-IP retry stays at 5 min.
                        boolean newIP=!expected.equals(base(app));
                        if(!newIP && !canDiscoverAgain())continue;
                        if(newIP && now-lastDiscoveryAt<10000L)continue;
                        IO.execute(()->discover(app,true,sender));
                    }
                    udp.leaveGroup(group);
                }
            }catch(Exception e){lastProblem="Anúncio multicast indisponível; busca UDP continua ativa.";}
            finally{
                beaconSocket=null;
                if(lock!=null)try{if(lock.isHeld())lock.release();}catch(Exception ignored){}
                BEACON_LISTENING.set(false);
            }
        },"bridge-notes-beacon");
        t.setDaemon(true);beaconThread=t;t.start();
    }
    static void stopBeaconListener(){
        BEACON_LISTENING.set(false);
        MulticastSocket socket=beaconSocket;
        if(socket!=null)socket.close();
        Thread t=beaconThread;
        if(t!=null)t.interrupt();
    }
    /** Periodic connection/poll. No operator configuration required on a trusted LAN. */
    static void heartbeat(Context c){
        final Context app=c.getApplicationContext();
        IO.execute(()->{
            if(!configured(app)){
                if(canDiscoverAgain())discover(app,true,null);
                if(!configured(app))return;
            }
            // A previously unreachable server must not trigger a heavy scan
            // on every 15-second heartbeat. Retry automatically every 5 minutes.
            if(lastGoodAt==0L && lastDiscoveryAt>0L && !canDiscoverAgain())return;
            try{
                JSONObject body=new JSONObject();
                body.put("id",deviceId(app));body.put("name",deviceName(app));body.put("order",LAST_ORDER);
                JSONObject data=request(app,"POST","/api/device/heartbeat",body);
                lastGoodAt=SystemClock.elapsedRealtime();
                lastProblem="";
                ArrayList<Broadcast> messages=new ArrayList<>();
                JSONArray arr=data.optJSONArray("broadcasts");
                if(arr!=null)for(int i=0;i<arr.length()&&i<60;i++){
                    JSONObject item=arr.optJSONObject(i);if(item==null)continue;
                    String id=item.optString("id","");String message=item.optString("text","");
                    if(id.matches("[0-9a-f]{32}")&&!message.isEmpty()&&message.length()<=1500)
                        messages.add(new Broadcast(id,message));
                }
                MAIN.post(()->FloatingToolsService.showBroadcasts(messages,()->received(app,messages)));
            }catch(Exception ex){
                // A previous PC IP or key may have changed. Retry LAN discovery.
                lastGoodAt=0L;
                lastProblem="Servidor não responde; nova busca automática em até 5 minutos.";
                if(canDiscoverAgain())discover(app,true,null);
            }
        });
    }
    /** Server acknowledgement that the Bridge actually accepted the message. */
    private static void received(Context c,List<Broadcast> messages){
        if(messages==null || messages.isEmpty())return;
        final Context app=c.getApplicationContext();
        final JSONArray ids=new JSONArray();
        for(Broadcast b:messages)if(b!=null && b.id.matches("[0-9a-f]{32}"))ids.put(b.id);
        if(ids.length()==0)return;
        IO.execute(()->{
            try{
                JSONObject body=new JSONObject();
                body.put("id",deviceId(app));body.put("broadcastIds",ids);
                request(app,"POST","/api/device/received",body);
            }catch(Exception ignored){ /* Retry on next heartbeat; do not interfere with scans. */ }
        });
    }
    static void seen(Context c,String broadcastId,Callback callback){
        if(broadcastId==null||!broadcastId.matches("[0-9a-f]{32}")){callback.done(false,"Aviso inválido.");return;}
        Context app=c.getApplicationContext();
        IO.execute(()->{
            try{
                JSONObject body=new JSONObject();body.put("id",deviceId(app));body.put("broadcastId",broadcastId);
                request(app,"POST","/api/device/seen",body);
                MAIN.post(()->callback.done(true,"Aviso confirmado."));
            }catch(Exception ex){MAIN.post(()->callback.done(false,"Sem conexão; tente novamente."));}
        });
    }
    private static synchronized boolean discover(Context app,boolean force,String preferredIP){
        lastDiscoveryAt=SystemClock.elapsedRealtime();
        try{
            if(!force && configured(app) && lastGoodAt>0)return true;
            LinkedHashSet<String> addresses=new LinkedHashSet<>();
            if(preferredIP!=null && validHost("http://"+preferredIP+":8765"))addresses.add(preferredIP);
            // IPv4 broadcast por interface: importante para coletores com varias interfaces.
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            if(interfaces!=null)while(interfaces.hasMoreElements()){
                NetworkInterface ni=interfaces.nextElement();
                if(!ni.isUp()||ni.isLoopback())continue;
                for(InterfaceAddress ia:ni.getInterfaceAddresses()){
                    InetAddress broadcast=ia.getBroadcast();
                    if(broadcast!=null)addresses.add(broadcast.getHostAddress());
                }
            }
            addresses.add("255.255.255.255");
            byte[] query=DISCOVER.getBytes(StandardCharsets.UTF_8);
            boolean hadResponse=false;
            try(DatagramSocket udp=new DatagramSocket()){
                udp.setBroadcast(true);
                // Duas rodadas para contornar perda ocasional de broadcast no Wi-Fi.
                for(int round=0;round<2;round++){
                    for(String address:addresses){
                        try{InetAddress target=InetAddress.getByName(address);
                            udp.send(new DatagramPacket(query,query.length,target,DISCOVER_PORT));
                        }catch(Exception ignored){}
                    }
                    long deadline=SystemClock.elapsedRealtime()+1400L;
                    while(SystemClock.elapsedRealtime()<deadline){
                        udp.setSoTimeout((int)Math.max(100L,deadline-SystemClock.elapsedRealtime()));
                        byte[] buffer=new byte[2048];
                        DatagramPacket response=new DatagramPacket(buffer,buffer.length);
                        try{udp.receive(response);}catch(SocketTimeoutException timeout){break;}
                        hadResponse=true;
                        try{
                            JSONObject info=new JSONObject(new String(response.getData(),0,response.getLength(),StandardCharsets.UTF_8));
                            if(!"SERIDOPLAST_NOTES_V1".equals(info.optString("service")))continue;
                            String host=info.optString("host","");String key=info.optString("token","");
                            if(!validHost(host)||!key.matches("[0-9a-fA-F]{64}"))continue;
                            URI address=new URI(host);
                            if(!address.getHost().equals(response.getAddress().getHostAddress()))continue;
                            JSONObject ping=requestAt(host,key,"GET","/api/ping",null);
                            if(!ping.optBoolean("ok",false))continue;
                            saveConnection(app,host,key,deviceName(app));
                            lastGoodAt=SystemClock.elapsedRealtime();
                            lastProblem="";
                            if(!LAST_ORDER.isEmpty())observedOrder(app,LAST_ORDER);
                            return true;
                        }catch(Exception ignored){}
                    }
                }
            }
            lastProblem=hadResponse?"Servidor respondeu, mas não passou na validação HTTP/credenciais.":
                "Sem resposta UDP 8766: confira firewall do PC, mesma sub-rede e isolamento do Wi-Fi.";
            return false;
        }catch(Exception e){lastProblem="Falha de rede: "+e.getClass().getSimpleName();return false;}
    }

    /** Avoid sending a bearer token to any public IP, DNS alias or arbitrary URL. */
    static boolean validHost(String input){
        if(input==null)return false;
        try{
            URI u=new URI(input);
            if(!"http".equals(u.getScheme())||u.getHost()==null||u.getPort()<1||u.getPort()>65535 ||
               (u.getRawPath()!=null&&!u.getRawPath().isEmpty())||u.getRawQuery()!=null||u.getRawFragment()!=null||u.getUserInfo()!=null)return false;
            String host=u.getHost();
            if(!host.matches("[0-9.]+"))return false;
            String[] v=host.split("\\.",-1);
            if(v.length!=4)return false;
            int[] a=new int[4];for(int i=0;i<4;i++){a[i]=Integer.parseInt(v[i]);if(a[i]<0||a[i]>255)return false;}
            return a[0]==10 || a[0]==192&&a[1]==168 || a[0]==172&&a[1]>=16&&a[1]<=31;
        }catch(Exception ignored){return false;}
    }
    static void saveConnection(Context c,String host,String key,String deviceName){
        if(!validHost(host)||key==null||!key.trim().matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("Informe URL http://IP:8765 da rede local e a chave de 64 caracteres.");
        String device=deviceName==null?"":deviceName.trim();
        if(device.length()>80 || device.contains("\n") || device.contains("\r"))throw new IllegalArgumentException("Nome do coletor deve ter até 80 caracteres.");
        c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).edit().putString(PREF_HOST,host.trim()).putString(PREF_KEY,key.trim()).putString(PREF_DEVICE_NAME,device).apply();
        GENERATION.incrementAndGet();
    }
    static void ping(Context c,Callback callback){
        Context app=c.getApplicationContext();
        if(!configured(app)){callback.done(false,"Configure o endereço e a chave.");return;}
        IO.execute(()->{
            boolean ok=false;String message;
            try{JSONObject data=request(app,"GET","/api/ping",null);ok=data.optBoolean("ok",false);message=ok?"Painel conectado.":"Resposta inesperada.";}
            catch(Exception e){message="Sem conexão com o painel: "+e.getMessage();}
            boolean answer=ok;String result=message;MAIN.post(()->callback.done(answer,result));
        });
    }
    static void observedOrder(Context c,String raw){
        String order=OrderNumberPolicy.normalize(raw);
        if(order==null)return;
        LAST_ORDER=order;
        if(!configured(c)){reconnect(c);return;}
        final Context app=c.getApplicationContext();
        final int generation=GENERATION.incrementAndGet();
        IO.execute(()->{
            try{
                JSONObject result=request(app,"GET","/api/notes?order="+URLEncoder.encode(order,"UTF-8"),null);
                JSONArray arr=result.optJSONArray("notes");
                ArrayList<Note> list=new ArrayList<>();
                if(arr!=null){for(int i=0;i<arr.length()&&i<100;i++){
                    JSONObject o=arr.optJSONObject(i);if(o==null)continue;
                    String id=o.optString("id","");String number=o.optString("order","");String text=o.optString("text","");
                    if(id.matches("[0-9a-f]{32}")&&order.equals(number)&&!text.isEmpty())list.add(new Note(id,number,text));
                }}
                MAIN.postDelayed(()->{
                    if(GENERATION.get()==generation)FloatingToolsService.showOrderNotes(order,list);
                },850);
            }catch(Exception e){MAIN.post(()->{if(GENERATION.get()==generation)FloatingToolsService.report("Observações: painel sem conexão.");});}
        });
    }
    static void refresh(Context c,String order){if(order!=null)observedOrder(c,order);}
    static void complete(Context c,String order,List<Note> notes,Callback callback){
        final String n=OrderNumberPolicy.normalize(order);
        if(n==null||notes==null||notes.isEmpty()){callback.done(false,"Nenhuma observação para concluir.");return;}
        final JSONArray ids=new JSONArray();for(Note note:notes)if(note!=null&&n.equals(note.order))ids.put(note.id);
        if(ids.length()==0){callback.done(false,"Pedido incorreto.");return;}
        final Context app=c.getApplicationContext();
        IO.execute(()->{
            String result;boolean ok=false;
            try{
                String device=deviceName(app);
                if(device.length()>80)device=device.substring(0,80);
                JSONObject body=new JSONObject();body.put("order",n);body.put("ids",ids);body.put("device",device);
                JSONObject reply=request(app,"POST","/api/complete",body);
                ok=reply.optBoolean("ok",false);
                result=ok?"Pedido concluído e sincronizado.":"Não foi possível confirmar a conclusão.";
            }catch(Exception e){result="Sem sincronização: "+e.getMessage();}
            boolean success=ok;String msg=result;
            MAIN.post(()->callback.done(success,msg));
        });
    }
    private static JSONObject request(Context c,String method,String path,JSONObject body)throws Exception{
        if(!configured(c))throw new IOException("Configuração ausente");
        String host=base(c);if(!validHost(host))throw new IOException("Endereço não autorizado");
        return requestAt(host,token(c),method,path,body);
    }
    private static JSONObject requestAt(String host,String accessToken,String method,String path,JSONObject body)throws Exception{
        if(!validHost(host)||accessToken==null||!accessToken.matches("[0-9a-fA-F]{64}"))throw new IOException("Servidor inválido");
        HttpURLConnection connection=(HttpURLConnection)new java.net.URL(host+path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(2500);connection.setReadTimeout(3500);
        connection.setRequestMethod(method);connection.setRequestProperty("X-Bridge-Token",accessToken);
        connection.setRequestProperty("Accept","application/json");
        try{
            if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
                try(OutputStream o=connection.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int response=connection.getResponseCode();
            if(response<200 || response>=300)throw new IOException(response==401?"chave inválida": "HTTP "+response);
            StringBuilder text=new StringBuilder();
            try(InputStream input=connection.getInputStream();BufferedReader b=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
                String line;while((line=b.readLine())!=null){text.append(line);if(text.length()>160000)throw new IOException("Resposta muito grande");}
            }
            return new JSONObject(text.toString());
        }finally{connection.disconnect();}
    }
}
