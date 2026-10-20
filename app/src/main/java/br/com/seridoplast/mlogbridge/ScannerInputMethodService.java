package br.com.seridoplast.mlogbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * IME de leitura dedicado ao MLOG. Nao depende de acessibilidade.
 * Aceita broadcast do motor nativo CMX, so escreve no editor atualmente
 * conectado E somente quando o pacote do editor pertence ao MLOG.
 * Nao armazena, registra nem transmite textos ou codigos escaneados.
 */
public class ScannerInputMethodService extends InputMethodService {
    static final String TARGET = "com.targetsistemas.target_mlog2";
    static final String PREFS = "mlog_bridge_v2";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver receiver;
    private boolean receiverRegistered;
    private boolean editorStarted;
    private int editorSerial;
    private String previousCode = ""; // apenas em memoria, para deduplicar broadcasts
    private long previousAt;

    @Override public void onCreate() {
        super.onCreate();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String code = ScannerProtocol.barcode(intent);
                if (code != null) receiveBarcode(code);
            }
        };
        IntentFilter filter = new IntentFilter();
        for (String action : ScannerProtocol.ACTIONS) filter.addAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(receiver, filter);
            }
            receiverRegistered = true;
        } catch (RuntimeException ex) {
            report("Falha ao ouvir scanner: verifique a configuracao CMX");
        }
        report("Teclado de leitura iniciado. Selecione o campo do MLOG.");
    }

    @Override public View onCreateInputView() {
        // Sem teclado na tela: view minima e vazia, sem teclas nem capturas de toque.
        View empty = new View(this);
        empty.setMinimumHeight(0);
        empty.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1, 1));
        return empty;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        editorSerial++;
        editorStarted = true;
        if (attribute != null && TARGET.equals(attribute.packageName)) {
            report("Campo do MLOG conectado. Pronto para leitura.");
        } else {
            report("Aguardando o campo do MLOG.");
        }
    }

    @Override public void onFinishInput() {
        editorSerial++;
        editorStarted = false;
        report("Campo perdeu foco. Toque no campo de codigo do MLOG.");
        super.onFinishInput();
    }

    private boolean isPassword(EditorInfo info) {
        int type = info.inputType;
        int klass = type & InputType.TYPE_MASK_CLASS;
        int var = type & InputType.TYPE_MASK_VARIATION;
        return (klass == InputType.TYPE_CLASS_TEXT &&
                   (var == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
               (klass == InputType.TYPE_CLASS_NUMBER &&
                    var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private boolean validCode(String raw) {
        if (raw.length() < 1 || raw.length() > 128) return false;
        // Recusar controles, ENTER, quebras de linha, dados binarios e nao ASCII.
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch < 32 || ch > 126) return false;
        }
        return true;
    }

    private boolean targetAvailable() {
        EditorInfo current = getCurrentInputEditorInfo();
        return editorStarted && current != null && TARGET.equals(current.packageName)
            && !isPassword(current) && getCurrentInputConnection() != null;
    }

    private void receiveBarcode(String code) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean("enabled", true)) return;
        if (!targetAvailable()) {
            // Nunca escrever em outro aplicativo, nem em campo de senha.
            report("Leitura nao inserida: selecione o campo do MLOG.");
            return;
        }
        if (!validCode(code)) {
            report("Leitura rejeitada: codigo com caracteres nao suportados.");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (code.equals(previousCode) && now - previousAt < 320) return;
        previousCode = code;
        previousAt = now;

        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        int serialAtRead = editorSerial;
        boolean inserted;
        try { inserted = connection.commitText(code, 1); }
        catch (Exception e) { inserted = false; }
        if (!inserted) {
            report("MLOG nao aceitou o texto; tente outro metodo de entrada.");
            return;
        }
        report("Codigo entregue ao campo do MLOG (sem guardar o valor).");
        if (prefs.getBoolean("send_enter", false)) {
            handler.postDelayed(() -> {
                // Nao enviar Enter caso tenha mudado o campo ou aplicativo.
                if (editorSerial != serialAtRead || !targetAvailable()) return;
                InputConnection current = getCurrentInputConnection();
                if (current == null) return;
                current.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                current.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                report("Enter enviado ao MLOG.");
            }, 90);
        }
    }

    private void report(String message) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("last_status", message).apply();
    }

    @Override public void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(receiver); }
            catch (Exception ignored) { }
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
