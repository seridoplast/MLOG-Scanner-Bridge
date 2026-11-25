package br.com.seridoplast.mlogbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import io.github.muntashirakon.adb.android.AdbMdns;
import java.net.InetSocketAddress;
import java.util.UUID;

/** Foreground only for the explicit, bounded pairing session. Never accepts remote-device addresses. */
public final class LocalAdbPairService extends Service {
    static final String START="br.com.seridoplast.mlogbridge.PAIR_START";
    private static final String CODE="br.com.seridoplast.mlogbridge.PAIR_CODE";
    private static final String MANUAL="br.com.seridoplast.mlogbridge.PAIR_MANUAL";
    private static final String CANCEL="br.com.seridoplast.mlogbridge.PAIR_CANCEL";
    private static final String SESSION="session", GENERATION="generation", REPLY="pair_code";
    private static final String SEARCH_CHANNEL="bridge_adb_search_v3623";
    static final String INPUT_CHANNEL="bridge_adb_code_v3623";
    private static final int SEARCH_ID=36230, INPUT_ID=36231, RESULT_ID=36232;
    private static final long SEARCH_MS=125000L, TOTAL_MS=160000L;
    private final Handler main=new Handler(Looper.getMainLooper());
    private NotificationManager nm;
    private AdbMdns mdns;
    private InetSocketAddress endpoint;
    private String session;
    private long started;
    private int generation;
    private boolean submitting;
    private final Runnable expiry=()->finish("Tempo esgotado. Toque em Parear ADB e gere um novo código.");
    static void createChannels(NotificationManager nm) {
        NotificationChannel search=new NotificationChannel(SEARCH_CHANNEL,"Busca ADB",NotificationManager.IMPORTANCE_LOW);
        search.setDescription("Busca temporária e resultado do pareamento.");
        NotificationChannel input=new NotificationChannel(INPUT_CHANNEL,"Código de pareamento ADB",NotificationManager.IMPORTANCE_HIGH);
        input.setDescription("Aviso em destaque quando o Android exibe um código de pareamento.");
        input.enableVibration(true); input.setVibrationPattern(new long[]{0,90}); input.setSound(null,null);
        nm.createNotificationChannel(search); nm.createNotificationChannel(input);
    }
    @Override public void onCreate() { super.onCreate(); nm=getSystemService(NotificationManager.class); if(nm!=null)createChannels(nm); }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent i,int flags,int startId) {
        String action=i==null?null:i.getAction();
        if (START.equals(action)) { begin(); return START_NOT_STICKY; }
        if (session==null) { stopSelf(); return START_NOT_STICKY; }
        if (!session.equals(i==null?null:i.getStringExtra(SESSION))) return START_NOT_STICKY;
        if (CANCEL.equals(action)) { if(!submitting)finish(null); return START_NOT_STICKY; }
        if (submitting || (!CODE.equals(action)&&!MANUAL.equals(action))) return START_NOT_STICKY;
        if (CODE.equals(action) && i.getIntExtra(GENERATION,-1)!=generation) return START_NOT_STICKY;
        if (SystemClock.elapsedRealtime()-started>=SEARCH_MS) { finish("Código expirado. Inicie um novo pareamento."); return START_NOT_STICKY; }
        android.os.Bundle reply=RemoteInput.getResultsFromIntent(i);
        CharSequence raw=reply==null?null:reply.getCharSequence(REPLY);
        if(raw==null)return START_NOT_STICKY;
        try {
            LocalAdbPairInput input=CODE.equals(action)?LocalAdbPairInput.automatic(raw.toString(),endpoint):LocalAdbPairInput.manual(raw.toString());
            submit(input);
        } catch(java.io.IOException e) {
            if(CODE.equals(action)&&endpoint!=null)showInput(e.getMessage());
            else nm.notify(SEARCH_ID,searchNotification(e.getMessage(),true));
        }
        return START_NOT_STICKY;
    }
    private void begin() {
        // Duplicate taps do not restart a running session or invalidate a submitted code.
        if(session!=null)return;
        if(nm==null || Build.VERSION.SDK_INT<30){stopSelf();return;}
        session=UUID.randomUUID().toString(); started=SystemClock.elapsedRealtime(); generation=0; submitting=false; endpoint=null;
        try {
            Notification n=searchNotification("Abra Parear dispositivo com código no Android.",true);
            if(Build.VERSION.SDK_INT>=34)startForeground(SEARCH_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
            else startForeground(SEARCH_ID,n);
            nm.cancel(RESULT_ID); nm.cancel(INPUT_ID);
            if(LocalAdbBridge.pairing()){finish("Já existe um pareamento em andamento.");return;}
            final String current=session;
            mdns=new AdbMdns(this,AdbMdns.SERVICE_TYPE_TLS_PAIRING,(address,port)->main.post(()->{
                if(!current.equals(session)||submitting)return;
                if(port<1){ if(endpoint!=null){endpoint=null;generation++;nm.cancel(INPUT_ID);} return; }
                InetSocketAddress found=LocalAdbPairInput.localEndpoint(address,port);
                if(found==null || found.equals(endpoint))return;
                endpoint=found; generation++;
                showInput("Digite apenas os seis dígitos exibidos pelo Android.");
            }));
            mdns.start(); main.postDelayed(expiry,SEARCH_MS);
        } catch(RuntimeException e){finish("Busca automática indisponível. Abra o pareamento novamente.");}
    }
    private PendingIntent action(String type,int request,boolean mutable) {
        Intent intent=new Intent(this,LocalAdbPairService.class).setAction(type).putExtra(SESSION,session).putExtra(GENERATION,generation);
        // Each input notification is bound to the generation that announced the port.
        intent.setData(android.net.Uri.parse("bridge-pair://local/"+session+"/"+type+"/"+(CODE.equals(type)?generation:0)));
        return PendingIntent.getService(this,request,intent,PendingIntent.FLAG_UPDATE_CURRENT|(mutable?PendingIntent.FLAG_MUTABLE:PendingIntent.FLAG_IMMUTABLE));
    }
    private Notification.Action inputAction(boolean manual) {
        RemoteInput input=new RemoteInput.Builder(REPLY).setLabel(manual?"IP:porta código":"Código de 6 dígitos").build();
        return new Notification.Action.Builder(R.drawable.ui_terminal,manual?"Parear manualmente":"Digitar código",action(manual?MANUAL:CODE,manual?2:1,true)).addRemoteInput(input).setAllowGeneratedReplies(false).build();
    }
    private Notification.Builder base(String channel,String title,String text) {
        return new Notification.Builder(this,channel).setSmallIcon(R.drawable.ui_terminal).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setVisibility(Notification.VISIBILITY_PRIVATE).setShowWhen(false);
    }
    private Notification searchNotification(String text,boolean actions) {
        Notification.Builder b=base(SEARCH_CHANNEL,"MLOG Bridge · Pareamento",text).setOngoing(true).setOnlyAlertOnce(true);
        if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        if(actions)b.addAction(inputAction(true)).addAction(new Notification.Action.Builder(R.drawable.ui_terminal,"Cancelar",action(CANCEL,3,false)).build());
        else b.setProgress(0,0,true);
        return b.build();
    }
    private void showInput(String text) {
        nm.notify(INPUT_ID,base(INPUT_CHANNEL,"MLOG Bridge · Digitar código",text).setCategory(Notification.CATEGORY_STATUS).setPriority(Notification.PRIORITY_HIGH).setOngoing(true).setAutoCancel(false).addAction(inputAction(false)).addAction(new Notification.Action.Builder(R.drawable.ui_terminal,"Cancelar",action(CANCEL,4,false)).build()).build());
    }
    private void submit(LocalAdbPairInput input) {
        submitting=true; main.removeCallbacks(expiry); stopDiscovery(); nm.cancel(INPUT_ID);
        nm.notify(SEARCH_ID,searchNotification("Autenticando com o Android (até 25 segundos).",false));
        final String current=session;
        main.postDelayed(expiry,Math.max(1000L,TOTAL_MS-(SystemClock.elapsedRealtime()-started)));
        LocalAdbBridge.pair(this,input.host,input.port,input.code,(ok,message)->main.post(()->{if(current.equals(session))finish(message);}));
    }
    private void stopDiscovery() { if(mdns!=null){try{mdns.stop();}catch(RuntimeException ignored){}mdns=null;} }
    private void finish(String message) {
        session=null; endpoint=null; main.removeCallbacks(expiry); stopDiscovery();
        if(nm!=null){nm.cancel(INPUT_ID);stopForeground(STOP_FOREGROUND_REMOVE);nm.cancel(SEARCH_ID);
            if(message!=null)nm.notify(RESULT_ID,base(SEARCH_CHANNEL,"MLOG Bridge · ADB local",message).setAutoCancel(true).setTimeoutAfter(90000L).build());}
        stopSelf();
    }
    @Override public void onTimeout(int startId) { finish("Tempo de pareamento esgotado."); }
    @Override public void onDestroy() { session=null; main.removeCallbacksAndMessages(null); stopDiscovery(); if(nm!=null){nm.cancel(INPUT_ID);nm.cancel(SEARCH_ID);}super.onDestroy(); }
}
