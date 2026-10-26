package br.com.seridoplast.mlogbridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Controle opt-in do servico de decodificacao embarcado Hikrobot/CMX.
 * Os broadcasts sao o protocolo do firmware, NAO requerem o CMXScanner
 * nem o aplicativo visual Assistente de Digitalizacao em execucao.
 *
 * ATENCAO: os comandos alteram a configuracao GLOBAL de saida do leitor.
 * Alguns firmwares podem ignorar comandos ou exigir privilegio do sistema.
 * Enviar um comando sem SecurityException nao prova que ele foi aplicado.
 */
final class NativeScannerController {
    static final String PREF = "native_scanner_managed_v315";
    static final String PACKAGE = "com.hikrobotics.pdaservice";
    static final String RECEIVER = "com.pda.service.broadcast.ServiceControlReceiver";
    static final String ACTION_SCAN = DirectScanPolicy.ACTION;
    private NativeScannerController() {}

    static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            Log.w("MLOGBridge", "NATIVE_PACKAGE_QUERY_FAILED", exception);
            return false;
        }
    }

    /**
     * Configurar saida UNICA como broadcast, sem teclado/clipboard ou Enter
     * emitido pelo firmware. O Bridge e quem digita e confirma.
     *
     * @return true apenas se todos os Intents foram ENVIADOS sem excecao;
     *         a confirmacao real requer receber um barcode em ACTION_SCAN.
     */
    static boolean configure(Context context) {
        if (!isInstalled(context)) return false;
        try {
            // Configurar acao e extra ANTES de habilitar o modo broadcast.
            sendString(context, "com.service.scanner.config.broad.action", "broad_action", ACTION_SCAN);
            sendString(context, "com.service.scanner.config.broad.code", "broad_code", "ScanCode");
            sendString(context, "com.service.scanner.config.broad.code.type", "broad_code_type", "ScanCodeType");
            sendString(context, "com.service.scanner.scan.end.type", "scanEndType", "none");
            sendString(context, "com.service.scanner.suffix", "scanCodeSuffix", "");
            sendBoolean(context, "com.service.scanner.clipboard.output", "clipboardOutput", false);
            sendString(context, "com.service.scanner.outputway", "outputWay", "background");
            sendString(context, "com.service.scanner.outputmode", "outputMode", "broadcast");
            sendBoolean(context, "com.service.scanner.broadcast.output", "broadcastOutput", true);
            Log.i("MLOGBridge", "NATIVE_SCANNER_CONFIG_SENT_ACTION_DIRECT");
            return true;
        } catch (RuntimeException exception) {
            Log.e("MLOGBridge", "NATIVE_SCANNER_CONFIG_DENIED", exception);
            return false;
        }
    }

    // Emular novos acionamentos pelo comando do servico nativo, SEM sintetizar
    // KeyEvent privilegiado e SEM mudar o scanMode global para continuous.
    // O comando apenas solicita ao firmware uma captura. O hardware pode nao
    // obedecer; o teste fisico e indispensavel.
    static boolean pulseTrigger(Context context) {
        if (!isInstalled(context)) return false;
        try {
            // Cada pulso inicia uma tentativa; sem ACTION_STOP a cada ciclo,
            // pois alguns firmwares cancelam a decodificacao ao receber STOP
            // antes do broadcast resultante.
            context.sendBroadcast(new Intent("com.service.scanner.start.scanning"));
            Log.i("MLOGBridge", "LONG_HOLD_PULSE_SENT");
            return true;
        } catch (RuntimeException exception) {
            Log.e("MLOGBridge", "LONG_HOLD_PULSE_FAILED", exception);
            return false;
        }
    }

    static void stopContinuous(Context context) {
        try {context.sendBroadcast(new Intent("com.service.scanner.stop.scanning"));}
        catch(RuntimeException ex){Log.w("MLOGBridge","LONG_HOLD_STOP_FAILED",ex);}
        // Nao alterar scanMode: agora os pulsos sao independentes.
    }
    static final String LONG_HOLD_PREF="long_hold_trigger_v320";

    private static Intent target(String action) {
        Intent intent = new Intent(action);
        intent.setComponent(new ComponentName(PACKAGE, RECEIVER));
        return intent;
    }
    private static void sendString(Context context, String action, String key, String value) {
        Intent intent = target(action);
        intent.putExtra(key, value);
        context.sendBroadcast(intent);
    }
    private static void sendBoolean(Context context, String action, String key, boolean value) {
        Intent intent = target(action);
        intent.putExtra(key, value);
        context.sendBroadcast(intent);
    }
}
