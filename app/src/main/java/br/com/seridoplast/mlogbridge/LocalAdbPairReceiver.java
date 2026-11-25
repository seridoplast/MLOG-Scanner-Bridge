package br.com.seridoplast.mlogbridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** UI entry point; all discovery and direct replies belong to the short-lived service. */
final class LocalAdbPairReceiver {
    private LocalAdbPairReceiver() {}
    static void showPairing(Activity a) {
        if (LocalAdbBridge.pairing()) { toast(a,"Pareamento em andamento. Aguarde o resultado."); return; }
        if (Build.VERSION.SDK_INT < 30) { toast(a,"ADB sem fio exige Android 11 ou superior."); return; }
        new AlertDialog.Builder(a).setTitle("Parear ADB local")
            .setMessage("Abra Depuração sem fio > Parear dispositivo com código. Assim que o código aparecer, o Bridge procurará a porta automaticamente. Toque em Digitar código na notificação e informe apenas os seis dígitos, sem fechar a janela do Android.")
            .setPositiveButton("Abrir configurações", (d,w) -> {
                if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3619);
                    toast(a,"Autorize as notificações e toque em Parear ADB novamente."); return;
                }
                NotificationManager nm = a.getSystemService(NotificationManager.class);
                if (nm == null || !nm.areNotificationsEnabled()) { toast(a,"Ative as notificações do Bridge."); return; }
                try {
                    LocalAdbPairService.createChannels(nm);
                    if (nm.getNotificationChannel(LocalAdbPairService.INPUT_CHANNEL).getImportance() < android.app.NotificationManager.IMPORTANCE_HIGH) {
                        toast(a,"Para o aviso aparecer na tela, permita notificações de pareamento em destaque.");
                    }
                    a.startForegroundService(new Intent(a,LocalAdbPairService.class).setAction(LocalAdbPairService.START));
                } catch (RuntimeException e) { toast(a,"Não foi possível iniciar a busca de pareamento. Tente novamente."); return; }
                try { a.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
                catch (Exception ignored) { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            }).setNegativeButton("Cancelar",null).show();
    }
    private static void toast(Activity a,String message) { Toast.makeText(a,message,Toast.LENGTH_LONG).show(); }
}
