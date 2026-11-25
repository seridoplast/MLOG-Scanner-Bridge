package br.com.seridoplast.mlogbridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.Toast;

/** Explicit user authorization only. Pairing code never persisted or logged. */
public final class LocalAdbPairReceiver extends BroadcastReceiver {
    private static final String ACTION="br.com.seridoplast.mlogbridge.PAIR_LOCAL_ADB";
    private static final String CHANNEL="bridge_adb_pairing_v3619";
    private static final String REPLY="port_code";
    private static final int ID=36190;

    static void showPairing(Activity a){
        if(LocalAdbBridge.pairing()){Toast.makeText(a,"Pareamento em andamento. Aguarde o resultado.",Toast.LENGTH_LONG).show();return;}
        if(Build.VERSION.SDK_INT<30){Toast.makeText(a,"ADB sem fio exige Android 11 ou superior.",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(a).setTitle("Parear ADB local")
            .setMessage("Abra as opções do desenvolvedor > Depuração sem fio > Parear dispositivo com código. Sem fechar a janela, abra a cortina de notificações e responda à notificação do Bridge com o IP:porta e o código, separados por um espaço. Exemplo: 192.168.1.20:37123 012345. Também pode usar apenas a porta. Mantenha a janela do código aberta.")
            .setPositiveButton("Abrir configurações",(d,w)->{
                if(Build.VERSION.SDK_INT>=33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                    a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3619);
                    Toast.makeText(a,"Autorize as notificações e toque em Parear ADB novamente.",Toast.LENGTH_LONG).show();return;
                }
                NotificationManager nm=a.getSystemService(NotificationManager.class);
                if(nm==null || !nm.areNotificationsEnabled()){Toast.makeText(a,"Ative as notificações do Bridge.",Toast.LENGTH_LONG).show();return;}
                nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Pareamento ADB",NotificationManager.IMPORTANCE_LOW));
                PendingIntent pi=PendingIntent.getBroadcast(a,ID,new Intent(a,LocalAdbPairReceiver.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
                RemoteInput input=new RemoteInput.Builder(REPLY).setLabel("IP:porta código").build();
                Notification.Action action=new Notification.Action.Builder(R.drawable.ui_terminal,"Informar porta e código",pi).addRemoteInput(input).build();
                nm.notify(ID,new Notification.Builder(a,CHANNEL).setSmallIcon(R.drawable.ui_terminal).setContentTitle("MLOG Bridge · Parear ADB").setContentText("Abra o código de pareamento e responda aqui: IP:porta código").addAction(action).setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(180000L).setAutoCancel(false).build());
                try{a.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(Exception ignored){a.startActivity(new Intent(Settings.ACTION_SETTINGS));}
            }).setNegativeButton("Cancelar",null).show();
    }
    @Override public void onReceive(Context c,Intent i){
        if(i==null || !ACTION.equals(i.getAction()))return;
        android.os.Bundle reply=RemoteInput.getResultsFromIntent(i);
        if(reply==null)return;
        CharSequence raw=reply.getCharSequence(REPLY);
        if(raw==null)return;
        String[] parts=raw.toString().trim().split("\\s+");
        Context app=c.getApplicationContext();NotificationManager nm=app.getSystemService(NotificationManager.class);
        if(LocalAdbBridge.pairing())return;
        if(parts.length!=2 || !parts[0].matches("(?:[0-9]{1,3}(?:\\.[0-9]{1,3}){3}:)?[0-9]{1,5}") || !parts[1].matches("[0-9]{6}")){
            result(app,nm,"Formato inválido. Abra Parear ADB novamente.");return;
        }
        String[] endpoint=parts[0].split(":");
        String host=endpoint.length==2?endpoint[0]:"127.0.0.1";
        int port=Integer.parseInt(endpoint[endpoint.length-1]);
        if(nm!=null)nm.notify(ID,new Notification.Builder(app,CHANNEL).setSmallIcon(R.drawable.ui_terminal)
            .setContentTitle("MLOG Bridge · Pareando").setContentText("Autenticando com o Android (até 25 segundos)")
            .setProgress(0,0,true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(30000L).build());
        PendingResult pending=goAsync();
        java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable finish=()->{if(finished.compareAndSet(false,true))pending.finish();};
        Handler main=new Handler(Looper.getMainLooper());main.postDelayed(finish,8000L);
        LocalAdbBridge.pair(app,host,port,parts[1],(ok,message)->{
            main.post(()->{try{result(app,nm,message);}finally{finish.run();}});
        });
    }
    private static void result(Context app,NotificationManager nm,String message){
        if(nm!=null)nm.notify(ID,new Notification.Builder(app,CHANNEL).setSmallIcon(R.drawable.ui_terminal)
            .setContentTitle("MLOG Bridge · ADB local").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
            .setProgress(0,0,false).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setTimeoutAfter(90000L).build());
        Toast.makeText(app,message,Toast.LENGTH_LONG).show();
    }
}
