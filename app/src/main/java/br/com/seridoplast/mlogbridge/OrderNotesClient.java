package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
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
    private static final AtomicBoolean DISCOVERING=new AtomicBoolean(false);
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
    static void reconnect(Context c){
        Context app=c.getApplicationContext();
        IO.execute(()->discover(app,true));
    }
    /** Periodic connection/poll. No operator configuration required on a trusted LAN. */
    static void heartbeat(Context c){
        final Context app=c.getApplicationContext();
        IO.execute(()->{
            if(!configured(app))discover(app,false);
            if(!configured(app))return;
            try{
                JSONObject body=new JSONObject();
                body.put("id",deviceId(app));body.put("name",deviceName(app));body.put("order",LAST_ORDER);
                JSONObject data=request(app,"POST","/api/device/heartbeat",body);
                ArrayList<Broadcast> messages=new ArrayList<>();
                JSONArray arr=data.optJSONArray("broadcasts");
                if(arr!=null)for(int i=0;i<arr.length()&&i<60;i++){
                    JSONObject item=arr.optJSONObject(i);if(item==null)continue;
                    String id=item.optString("id","");String message=item.optString("text","");
                    if(id.matches("[0-9a-f]{32}")&&!message.isEmpty()&&message.length()<=1500)
                        messages.add(new Broadcast(id,message));
                }
                MAIN.post(()->FloatingToolsService.showBroadcasts(messages));
            }catch(Exception ex){
                // A previous PC IP or key may have changed. Retry LAN discovery.
                discover(app,true);
            }
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
    private static void discover(Context app,boolean force){
        if(!DISCOVERING.compareAndSet(false,true))return;
        try{
            // Keep a working manually configured server unless force is required.
            if(!force&&configured(app))return;
            LinkedHashSet<String> addresses=new LinkedHashSet<>();
            addresses.add("255.255.255.255");
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            if(interfaces!=null)while(interfaces.hasMoreElements()){
                NetworkInterface ni=interfaces.nextElement();
                if(!ni.isUp()||ni.isLoopback())continue;
                for(InterfaceAddress ia:ni.getInterfaceAddresses()){
                    InetAddress broadcast=ia.getBroadcast();
                    if(broadcast!=null)addresses.add(broadcast.getHostAddress());
                }
            }
            byte[] query=DISCOVER.getBytes(StandardCharsets.UTF_8);
            try(DatagramSocket udp=new DatagramSocket()){
                udp.setBroadcast(true);udp.setSoTimeout(850);
                for(String address:addresses){
                    try{udp.send(new DatagramPacket(query,query.length,InetAddress.getByName(address),DISCOVER_PORT));}
                    catch(Exception ignored){}
                }
                long deadline=android.os.SystemClock.elapsedRealtime()+1500L;
                while(android.os.SystemClock.elapsedRealtime()<deadline){
                    byte[] buffer=new byte[2048];
                    DatagramPacket response=new DatagramPacket(buffer,buffer.length);
                    try{udp.receive(response);}catch(SocketTimeoutException timeout){break;}
                    try{
                        JSONObject info=new JSONObject(new String(response.getData(),0,response.getLength(),StandardCharsets.UTF_8));
                        if(!"SERIDOPLAST_NOTES_V1".equals(info.optString("service")))continue;
                        String host=info.optString("host","");String key=info.optString("token","");
                        if(!validHost(host)||!key.matches("[0-9a-f]{64}"))continue;
                        URI address=new URI(host);
                        if(!address.getHost().equals(response.getAddress().getHostAddress()))continue;
                        JSONObject ping=requestAt(host,key,"GET","/api/ping",null);
                        if(!ping.optBoolean("ok",false))continue;
                        saveConnection(app,host,key,deviceName(app));
                        if(!LAST_ORDER.isEmpty())observedOrder(app,LAST_ORDER);
                        return;
                    }catch(Exception ignored){}
                }
            }
        }catch(Exception ignored){}finally{DISCOVERING.set(false);}
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
