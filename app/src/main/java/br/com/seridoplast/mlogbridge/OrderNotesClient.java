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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** LAN-only observation sync. Never blocks barcode injection or uses GitHub API. */
final class OrderNotesClient {
    static final String PREF_HOST="order_notes_host_v369";
    static final String PREF_KEY="order_notes_token_v369";
    static final String PREF_DEVICE_NAME="order_notes_device_name_v369";
    static final class Note {
        final String id,order,text;
        Note(String id,String order,String text){this.id=id;this.order=order;this.text=text;}
    }
    interface Callback { void done(boolean ok,String message); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final AtomicInteger GENERATION=new AtomicInteger();
    private static String base(Context c){return c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).getString(PREF_HOST,"").trim();}
    private static String token(Context c){return c.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).getString(PREF_KEY,"").trim();}
    static boolean configured(Context c){return validHost(base(c)) && token(c).matches("[a-fA-F0-9]{64}");}
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
        if(order==null || !configured(c))return;
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
                String device=app.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE).getString(PREF_DEVICE_NAME,"").trim();
                if(device.isEmpty())device=Build.MODEL==null?"coletor":Build.MODEL;
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
        HttpURLConnection connection=(HttpURLConnection)new java.net.URL(host+path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(2500);connection.setReadTimeout(3500);
        connection.setRequestMethod(method);connection.setRequestProperty("X-Bridge-Token",token(c));
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
