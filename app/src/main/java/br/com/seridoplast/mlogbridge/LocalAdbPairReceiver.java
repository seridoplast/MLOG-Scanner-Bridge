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
        if(Build.VERSION.SDK_INT<30){Toast.makeText(a,"ADB sem fio exige Android 11 ou superior.",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(a).setTitle("Parear ADB local")
            .setMessage("Abra as opções do desenvolvedor > Depuração sem fio > Parear dispositivo com código. Sem fechar a janela, abra a cortina de notificações e responda à notificação do Bridge com a porta e o código, separados por um espaço. Exemplo: 37123 012345. A porta de pareamento muda a cada tentativa.")
            .setPositiveButton("Abrir configurações",(d,w)->{
                if(Build.VERSION.SDK_INT>=33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                    a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3619);
                    Toast.makeText(a,"Autorize as notificações e toque em Parear ADB novamente.",Toast.LENGTH_LONG).show();return;
                }
                NotificationManager nm=a.getSystemService(NotificationManager.class);
                if(nm==null || !nm.areNotificationsEnabled()){Toast.makeText(a,"Ative as notificações do Bridge.",Toast.LENGTH_LONG).show();return;}
                nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Pareamento ADB",NotificationManager.IMPORTANCE_DEFAULT));
                PendingIntent pi=PendingIntent.getBroadcast(a,ID,new Intent(a,LocalAdbPairReceiver.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
                RemoteInput input=new RemoteInput.Builder(REPLY).setLabel("Porta Código").build();
                Notification.Action action=new Notification.Action.Builder(android.R.drawable.ic_menu_send,"Informar porta e código",pi).addRemoteInput(input).build();
                nm.notify(ID,new Notification.Builder(a,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("MLOG Bridge · Parear ADB").setContentText("Abra o código de pareamento e responda aqui: porta código").addAction(action).setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(180000L).setAutoCancel(false).build());
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
        if(parts.length!=2 || !parts[0].matches("[0-9]{1,5}") || !parts[1].matches("[0-9]{6}")){
            Toast.makeText(c,"Use porta e código de seis dígitos, separados por espaço.",Toast.LENGTH_LONG).show();return;
        }
        Context app=c.getApplicationContext();NotificationManager nm=app.getSystemService(NotificationManager.class);
        if(nm!=null)nm.cancel(ID);
        PendingResult pending=goAsync();
        // Finish the broadcast even if the firmware delays the pairing handshake.
        java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable finish=()->{if(finished.compareAndSet(false,true))pending.finish();};
        Handler main=new Handler(Looper.getMainLooper());main.postDelayed(finish,8500L);
        LocalAdbBridge.pair(app,Integer.parseInt(parts[0]),parts[1],(ok,message)->{
            main.post(()->{Toast.makeText(app,message,Toast.LENGTH_LONG).show();finish.run();});
        });
    }
}
