package br.com.seridoplast.mlogbridge;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable, private outbox. Delete only the event IDs acknowledged by the Central. */
final class ProductivityLog extends SQLiteOpenHelper {
    private static ProductivityLog instance;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"bridge-productivity");t.setDaemon(true);return t;});
    private static final AtomicBoolean sending=new AtomicBoolean();
    private static volatile long retryAt;
    private ProductivityLog(Context c){super(c.getApplicationContext(),"productivity_outbox.db",null,2);}
    private static synchronized ProductivityLog db(Context c){if(instance==null)instance=new ProductivityLog(c);return instance;}
    @Override public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE events (id TEXT PRIMARY KEY, at INTEGER NOT NULL, kind TEXT NOT NULL, operator TEXT NOT NULL, code TEXT NOT NULL, order_no TEXT NOT NULL, screen TEXT NOT NULL DEFAULT '')");d.execSQL("CREATE INDEX event_time ON events(at,id)");}
    @Override public void onUpgrade(SQLiteDatabase d,int old,int next){if(old<2)d.execSQL("ALTER TABLE events ADD COLUMN screen TEXT NOT NULL DEFAULT ''");}
    static final class Event {
        final String id=UUID.randomUUID().toString().replace("-","");
        final long at=System.currentTimeMillis();
        final String kind,operator,code,order,screen;
        Event(Context c,String kind,String code,String order,String screen){this.kind=kind;String user=MlogLoginIdentityPolicy.username(OrderNotesClient.mlogOperator(c));this.operator=user==null?"":user;this.code=code;this.order=order==null?"":order;this.screen=screen==null?"":screen;}
    }
    static Event capture(Context c,String kind,String code,String order){
        return capture(c,kind,code,order,"");
    }
    static Event capture(Context c,String kind,String code,String order,String screen){
        if(kind==null || kind.isEmpty() || code==null || code.isEmpty() || code.length()>128)return null;
        return new Event(c,kind,code,order,screen);
    }
    static void record(Context c,Event e){
        if(e==null)return;
        try{ContentValues v=new ContentValues();v.put("id",e.id);v.put("at",e.at);v.put("kind",e.kind);v.put("operator",e.operator);v.put("code",e.code);v.put("order_no",e.order);v.put("screen",e.screen);
            db(c).getWritableDatabase().insertOrThrow("events",null,v);
        }catch(Exception ex){Log.e("BridgeProductivity","Falha ao gravar evento de produtividade",ex);}
    }
    static void recordOrder(Context c,String order){
        Context app=c.getApplicationContext();Event e=capture(app,"order",order,order);
        if(e!=null)IO.execute(()->record(app,e));
    }
    static void networkAvailable(Context c){retryAt=0;tick(c);}
    static void tick(Context c){
        if(SystemClock.elapsedRealtime()<retryAt || !sending.compareAndSet(false,true))return;
        Context app=c.getApplicationContext();IO.execute(()->{
            try{
                // Bounded batches below the existing 8 KiB protocol limit.
                for(int batch=0;batch<4;batch++){
                    SQLiteDatabase d=db(app).getWritableDatabase();JSONArray events=new JSONArray();
                    try(Cursor rows=d.rawQuery("SELECT id,at,kind,operator,code,order_no,screen FROM events ORDER BY at,id LIMIT 12",null)){
                        while(rows.moveToNext()){
                            JSONObject e=new JSONObject();e.put("id",rows.getString(0));e.put("at",rows.getLong(1));e.put("kind",rows.getString(2));e.put("operator",rows.getString(3));e.put("code",rows.getString(4));e.put("order",rows.getString(5));if(!rows.getString(6).isEmpty())e.put("screen",rows.getString(6));events.put(e);
                        }
                    }
                    if(events.length()==0)break;
                    JSONObject body=new JSONObject();body.put("id",OrderNotesClient.deviceId(app));body.put("events",events);
                    JSONObject response=OrderNotesClient.request(app,"POST","/api/device/productivity",body);
                    JSONArray ack=response.optJSONArray("accepted");if(ack==null || ack.length()==0)throw new java.io.IOException("Sem confirmação de produtividade");
                    java.util.HashSet<String> sent=new java.util.HashSet<>();for(int i=0;i<events.length();i++)sent.add(events.getJSONObject(i).getString("id"));
                    d.beginTransaction();try{for(int i=0;i<ack.length();i++){String id=ack.optString(i,"");if(sent.contains(id))d.delete("events","id=?",new String[]{id});}d.setTransactionSuccessful();}finally{d.endTransaction();}
                }
                retryAt=SystemClock.elapsedRealtime()+5000;
            }catch(Exception ex){retryAt=SystemClock.elapsedRealtime()+30000;}
            finally{sending.set(false);}
        });
    }
}
