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
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,CheckBox> formats=new LinkedHashMap<>();
    private SharedPreferences prefs;
    private LinearLayout layout;
    private TextView status,preview;
    private boolean subscribed;
    private final BroadcastReceiver scanTest=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(preview==null)return;
            String b=ScannerProtocol.barcode(intent);
            if(b==null)preview.setText("Evento do scanner recebido, sem codigo reconhecido.");
            else preview.setText("TESTE LOCAL: "+b+"\nFormato: "+ScannerProtocol.type(intent));
            // Apenas a tela de teste mostra o codigo. Nenhum dado e persistido.
        }
    };
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(status!=null){
                String a=MlogFocusAccessibilityService.current()!=null?"ATIVA":"INATIVA";
                status.setText("Acessibilidade: "+a+"\n"+
                    prefs.getString("last_status","Sem diagnostico ainda."));
            }
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        prefs=getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);
        ScrollView v=new ScrollView(this);
        layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18),dp(16),dp(18),dp(32));
        v.addView(layout);setContentView(v);

        heading("MLOG Scanner Bridge v0.3");
        text("Foco inteligente para CMX + MLOG. Usa teclado de leitura e acessibilidade OPCIONAL e limitada ao MLOG.");
        text("AVISO: a acessibilidade consegue ler elementos da tela do MLOG. Nao habilite ate entender isso. Nao ha internet, coleta em segundo plano nem armazenamento de codigos. Ainda e versao de testes.");

        heading("1. Permissoes e seguranca");
        button("Abrir acessibilidade no Android",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        text("Ative somente 'MLOG Bridge - foco inteligente'. O sistema pode restringir apps instalados por APK. Nao existe codigo que garanta retirar esse bloqueio ou o aviso do Play Protect.");
        button("Habilitar teclado de leitura",()->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("Selecionar teclado MLOG",()->{
            InputMethodManager m=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(m!=null)m.showInputMethodPicker();
        });
        option("Ativar as leituras assistidas", "enabled",false);
        option("Pre-armar campo automaticamente em separacao", "auto_focus",true);
        option("Permitir tentativa por layout relativo (apenas separacao)", "layout_fallback",false);
        text("Deixe a tentativa por layout DESLIGADA ate verificar o diagnostico. O app nao clica por coordenada fixa e nao age sobre Quantidade.");
        option("Enviar Enter apos inserir codigo (TESTAR antes)", "send_enter",false);
        option("Modo v2 sem acessibilidade, exigindo foco manual", "compat_fallback",false);
        status=new TextView(this);
        status.setTextSize(15);status.setPadding(dp(10),dp(10),dp(10),dp(10));
        status.setBackgroundColor(0xffe8f0f4);layout.addView(status);
        button("Abrir MLOG",this::openMlog);

        heading("2. Scanner CMX");
        text("Configura o coletor para transmitir leituras por broadcast. Isso altera a configuracao do leitor fisico, nao do MLOG.");
        button("Configurar scanner para broadcast",()->{
            ScannerProtocol.configureBroadcast(this);toast("Comandos enviados. Teste a leitura abaixo.");
        });
        button("Acionar scanner (teste)",()->ScannerProtocol.trigger(this));
        preview=new TextView(this);preview.setText("Teste local: aguardando codigo...");
        preview.setBackgroundColor(0xffe8ecef);preview.setPadding(dp(12),dp(12),dp(12),dp(12));
        preview.setTextIsSelectable(true);layout.addView(preview);
        text("O codigo so aparece nesta tela de teste, sem ser salvo. No MLOG, os valores nunca aparecem no historico deste app.");

        heading("3. Formatos (opcional)");
        int defaults=1|2|4|8|16|32|8192|16384|524288;
        int mask=prefs.getInt("formats",defaults);
        for(Map.Entry<String,Integer> e:ScannerProtocol.FORMATS.entrySet()){
            CheckBox cb=new CheckBox(this);cb.setText(e.getKey());
            cb.setChecked((mask&e.getValue())!=0);formats.put(e.getKey(),cb);layout.addView(cb);
        }
        button("Aplicar formatos",()->{
            int m=0;
            for(Map.Entry<String,CheckBox> e:formats.entrySet())
                if(e.getValue().isChecked())m|=ScannerProtocol.FORMATS.get(e.getKey());
            if(m==0){toast("Selecione pelo menos um formato.");return;}
            prefs.edit().putInt("formats",m).apply();ScannerProtocol.configureFormats(this,m);
            toast("Enviado ao hardware. Suporte depende do scanner.");
        });

        heading("4. Procedimento recomendado");
        text("1. Teste o scanner aqui antes de abrir pedidos reais.\n"+
             "2. Habilite o teclado sem teclas e selecione ele.\n"+
             "3. Ative a acessibilidade, SE o Android autorizar, e ative as leituras assistidas.\n"+
             "4. Abra uma separacao de teste. O diagnostico deve identificar a tela e o botao teclado.\n"+
             "5. Faça uma leitura com Enter desligado. Confira o campo antes de confirmar.\n"+
             "6. Se o teclado nao for encontrado, volte a este app e leia o diagnostico. Nao habilite tentativa de layout sem testar.");
        text("Observacao: telas de notas ainda dependem de o Flutter publicar um controle identificado como teclado. Caso contrario e necessario um diagnostico adicional do layout.");
    }
    private void openMlog(){
        Intent i=getPackageManager().getLaunchIntentForPackage(ScanCoordinator.TARGET);
        if(i==null){toast("MLOG nao foi encontrado.");return;}startActivity(i);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void heading(String t){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(21);
        v.setTextColor(0xff233849);v.setPadding(0,dp(16),0,dp(6));layout.addView(v);
    }
    private void text(String t){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(14);
        v.setTextColor(0xff52616b);v.setPadding(0,dp(5),0,dp(10));layout.addView(v);
    }
    private void button(String t,Runnable r){
        Button v=new Button(this);v.setText(t);v.setAllCaps(false);
        v.setOnClickListener(w->r.run());layout.addView(v);
    }
    private void option(String t,String k,boolean defaultValue){
        CheckBox v=new CheckBox(this);v.setText(t);
        v.setChecked(prefs.getBoolean(k,defaultValue));
        v.setOnCheckedChangeListener((b,value)->prefs.edit().putBoolean(k,value).apply());
        layout.addView(v);
    }
    private void toast(String t){Toast.makeText(this,t,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){
        super.onResume();
        IntentFilter f=new IntentFilter();for(String a:ScannerProtocol.ACTIONS)f.addAction(a);
        try{
            if(Build.VERSION.SDK_INT>=33)registerReceiver(scanTest,f,Context.RECEIVER_EXPORTED);
            else registerReceiver(scanTest,f);
            subscribed=true;
        }catch(RuntimeException ignored){}
        handler.post(tick);
    }
    @Override protected void onPause(){
        handler.removeCallbacks(tick);
        if(subscribed)try{unregisterReceiver(scanTest);}catch(RuntimeException ignored){}
        subscribed=false;super.onPause();
    }
}
