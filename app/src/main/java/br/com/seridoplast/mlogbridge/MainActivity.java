package br.com.seridoplast.mlogbridge;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, CheckBox> formats = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView status;
    private TextView scanPreview;
    private boolean receiverRegistered;
    private final BroadcastReceiver testReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String code = ScannerProtocol.barcode(intent);
            if (scanPreview == null) return;
            if (code == null) {
                scanPreview.setText("Sinal recebido, mas nao foi possivel extrair o codigo.\nAcao: " + intent.getAction());
            } else {
                scanPreview.setText("Leitura recebida nesta tela:\n" + code +
                    "\nTipo: " + ScannerProtocol.type(intent) +
                    "\nOrigem: " + intent.getAction());
            }
        }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (status != null) {
                status.setText("Estado: " + prefs.getString("last_status", "Aguardando teclado de leitura."));
            }
            handler.postDelayed(this, 750);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(ScannerInputMethodService.PREFS, MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setPadding(dp(20), dp(14), dp(20), dp(28));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        setContentView(scroll);

        title("MLOG Scanner Bridge v0.2");
        note("Versao SEM acessibilidade • sem internet • sem teclado visivel.\nUse inicialmente fora da operacao de separacao.");

        title("1. Ativar teclado de leitura");
        note("Este teclado especial recebe o codigo do scanner e o insere SOMENTE no campo que estiver selecionado no MLOG. Nao consegue selecionar um campo sozinho.");
        button("Habilitar o teclado MLOG Scanner", () ->
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("Selecionar teclado MLOG Scanner", () -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        });
        CheckBox enabled = new CheckBox(this);
        enabled.setText("Aceitar leituras no MLOG");
        enabled.setChecked(prefs.getBoolean("enabled", true));
        enabled.setOnCheckedChangeListener((v, checked) ->
            prefs.edit().putBoolean("enabled", checked).apply());
        content.addView(enabled);

        CheckBox enter = new CheckBox(this);
        enter.setText("Enviar Enter depois de cada leitura (teste opcional)");
        enter.setChecked(prefs.getBoolean("send_enter", false));
        enter.setOnCheckedChangeListener((v, checked) ->
            prefs.edit().putBoolean("send_enter", checked).apply());
        content.addView(enter);
        note("Comece SEM Enter. O comportamento do MLOG pode variar conforme a tela.");
        button("Abrir MLOG", this::openMlog);
        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, dp(12), 0, dp(14));
        content.addView(status);

        title("2. Configurar coletor CMX");
        note("Comandos obtidos do CMX Scanner fornecido; sao experimentais neste firmware. " +
             "A opcao abaixo pode mudar a configuracao global do leitor fisico.");
        button("Configurar entrega por broadcast", () -> {
            ScannerProtocol.configureBroadcast(this);
            toast("Comandos enviados ao motor do CMX. Confirme pelo teste abaixo.");
        });
        title("Formatos a habilitar");
        int defaults = 1 | 2 | 4 | 8 | 16 | 32 | 8192 | 16384 | 524288;
        int mask = prefs.getInt("formats", defaults);
        for (Map.Entry<String,Integer> entry: ScannerProtocol.FORMATS.entrySet()) {
            CheckBox box = new CheckBox(this);
            box.setText(entry.getKey());
            box.setChecked((mask & entry.getValue()) != 0);
            content.addView(box);
            formats.put(entry.getKey(), box);
        }
        button("Aplicar formatos", () -> {
            int total = 0;
            for (Map.Entry<String,CheckBox> entry : formats.entrySet()) {
                if (entry.getValue().isChecked()) total |= ScannerProtocol.FORMATS.get(entry.getKey());
            }
            if (total == 0) {toast("Escolha pelo menos um formato."); return;}
            ScannerProtocol.configureFormats(this,total);
            prefs.edit().putInt("formats", total).apply();
            toast("Formatos enviados. Teste com codigos fisicos.");
        });
        note("Nao e possivel ler todos os formatos se o leitor optico nao oferecer suporte.");

        title("3. Teste do CMX (sem MLOG)");
        button("Iniciar leitura", () -> ScannerProtocol.trigger(this));
        button("Parar leitura", () -> ScannerProtocol.stop(this));
        scanPreview = new TextView(this);
        scanPreview.setTextSize(17);
        scanPreview.setText("Aguardando codigo do scanner...");
        scanPreview.setTextIsSelectable(true);
        scanPreview.setBackgroundColor(0xffe8ecef);
        scanPreview.setPadding(dp(12), dp(14), dp(12), dp(14));
        content.addView(scanPreview);
        note("O codigo aparece aqui apenas enquanto a tela esta aberta. Nao fica salvo.");
        title("Como testar no MLOG");
        note("1) Ative e selecione o teclado MLOG Scanner.\n"+
             "2) Configure o broadcast do CMX e confirme que o teste acima recebe codigos.\n"+
             "3) Abra MLOG e TOQUE UMA VEZ no campo do codigo de barras.\n"+
             "4) Aperte o gatilho. O codigo deve entrar sem teclado visivel.\n"+
             "5) Se funcionar, teste Enter opcional. Se o MLOG perder foco, sera necessario tocar novamente.");
    }

    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private void title(String label) {
        TextView view = new TextView(this); view.setText(label); view.setTextColor(0xff253747);
        view.setTextSize(21); view.setPadding(0, dp(16), 0, dp(7)); content.addView(view);
    }
    private void note(String message) {
        TextView view = new TextView(this); view.setText(message); view.setTextColor(0xff52616b);
        view.setTextSize(14); view.setPadding(0, dp(4), 0, dp(12)); content.addView(view);
    }
    private void button(String label, Runnable fn) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false);
        view.setOnClickListener(v -> fn.run()); content.addView(view);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void openMlog() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(ScannerInputMethodService.TARGET);
        if (launch == null) {toast("MLOG nao encontrado no aparelho."); return;}
        startActivity(launch);
    }
    @Override protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        for (String action : ScannerProtocol.ACTIONS) filter.addAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(testReceiver, filter, Context.RECEIVER_EXPORTED);
            else registerReceiver(testReceiver, filter);
            receiverRegistered = true;
        } catch (RuntimeException e) {
            if (scanPreview != null) scanPreview.setText("Falha ao escutar leitor CMX.");
        }
        handler.post(refresh);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        if (receiverRegistered) {
            try {unregisterReceiver(testReceiver);} catch (Exception ignored) {}
            receiverRegistered = false;
        }
        super.onPause();
    }
}
