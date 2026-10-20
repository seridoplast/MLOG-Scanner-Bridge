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
import android.view.Gravity;
import android.view.View;
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
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView status,live;
    private final LinkedHashMap<String,CheckBox> checkboxes = new LinkedHashMap<>();
    private boolean listening;
    private long lastDisplayAt;
    private String lastDisplay="";
    private final BroadcastReceiver demo = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx,Intent intent) {
            String code=ScannerProtocol.barcode(intent);
            if(code == null) {live.setText("Sinal do scanner, mas sem código reconhecido. Action="+intent.getAction());return;}
            long now=android.os.SystemClock.elapsedRealtime();
            if(code.equals(lastDisplay) && now-lastDisplayAt<180)return;
            lastDisplay=code;lastDisplayAt=now;
            live.setText("Lido: "+code+"\nFormato: "+ScannerProtocol.type(intent)+"\nOrigem: "+intent.getAction());
        }
    };
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if(status != null) {
                String txt=prefs.getString("last_status","Nenhuma leitura recebida pelo serviço ainda.");
                status.setText("Serviço: " + txt);
            }
            handler.postDelayed(this,1000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs=getSharedPreferences(MlogAccessibilityService.PREFS,MODE_PRIVATE);
        ScrollView sc=new ScrollView(this);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(20),dp(20),dp(26));
        sc.addView(root);setContentView(sc);

        title("MLOG Scanner Bridge");
        description("Protótipo CMX Supply • MLOG 9.3.5\nUse primeiro no ambiente de teste, não em separação real.");
        title("1. Integração MLOG");
        CheckBox active=new CheckBox(this);
        active.setText("Manter o campo do MLOG pronto para leitura");
        active.setChecked(prefs.getBoolean("active",true));
        active.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("active",checked).apply());
        root.addView(active);
        CheckBox submit=new CheckBox(this);
        submit.setText("Tentar confirmar automaticamente após ler (experimental)");
        submit.setChecked(prefs.getBoolean("submit",false));
        submit.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("submit",checked).apply());
        root.addView(submit);
        description("O Enter depende de o campo Flutter oferecer a ação de confirmação IME. Por segurança começa desligado.");

        button("Ativar serviço de acessibilidade",()->{
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        button("Aprender o campo de código do MLOG",()->{
            prefs.edit().putBoolean("learn",true).apply();
            Toast.makeText(this,"No MLOG, toque UMA VEZ no campo que recebe o código.",Toast.LENGTH_LONG).show();
            openMlog();
        });
        button("Abrir MLOG",this::openMlog);
        status=new TextView(this);
        status.setTextSize(14);status.setPadding(0,dp(10),0,dp(14));root.addView(status);

        title("2. Configurar leitor físico");
        description("Configuração obtida do CMX Scanner instalado: broadcast de leitura, sem sufixo e sem foco forçado pelo firmware. Isso altera as preferências globais do scanner.");
        button("Aplicar modo broadcast do CMX",()->{
            ScannerProtocol.configureBroadcast(this);
            Toast.makeText(this,"Comandos enviados ao serviço CMX; confirmar no teste de leitura.",Toast.LENGTH_LONG).show();
        });
        title("Códigos a habilitar");
        int defaultMask=8|2|1|16|32|4|8192|16384|524288;
        int savedMask=prefs.getInt("formats",defaultMask);
        for(Map.Entry<String,Integer> e:ScannerProtocol.FORMATS.entrySet()){
            CheckBox c=new CheckBox(this); c.setText(e.getKey()); c.setChecked((savedMask&e.getValue())!=0);
            checkboxes.put(e.getKey(),c);root.addView(c);
        }
        button("Salvar formatos no leitor",()->{
            int mask=0;
            for(Map.Entry<String,CheckBox> e:checkboxes.entrySet()) {
                if(Boolean.TRUE.equals(e.getValue().isChecked())) mask|=ScannerProtocol.FORMATS.get(e.getKey());
            }
            if(mask==0) {Toast.makeText(this,"Escolha pelo menos um formato.",Toast.LENGTH_SHORT).show();return;}
            prefs.edit().putInt("formats",mask).apply();
            ScannerProtocol.configureFormats(this,mask);
            Toast.makeText(this,"Máscara enviada: "+mask+". Valide cada formato no coletor.",Toast.LENGTH_LONG).show();
        });
        description("Não existe leitura de 'qualquer formato' se o motor óptico não oferecer suporte. EAN-13 depende de dígito verificador válido.");

        title("3. Teste sem MLOG");
        description("Neste teste, o código precisa aparecer abaixo mesmo sem nenhum campo de texto selecionado. Se o laser acender e não aparecer nada, o problema está no scanner/configuração do broadcast.");
        button("Iniciar leitura por software",()->ScannerProtocol.trigger(this));
        button("Parar leitura",()->ScannerProtocol.stop(this));
        live=new TextView(this);
        live.setText("Aguardando leitura de teste...");
        live.setTextSize(18);
        live.setTextIsSelectable(true);
        live.setPadding(dp(12),dp(16),dp(12),dp(16));
        live.setBackgroundColor(0xffe8ecef);
        root.addView(live);
        description("Se a leitura for recebida aqui mas não no MLOG, o problema é foco ou edição do campo. Se nem aqui aparecer, verifique formato, mecanismo de scanner ou se o firmware publica broadcasts.");
    }

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void title(String text){TextView x=new TextView(this);x.setText(text);x.setTextSize(22);x.setTextColor(0xff203040);x.setPadding(0,dp(20),0,dp(10));root.addView(x);}
    private void description(String text){TextView x=new TextView(this);x.setText(text);x.setTextSize(14);x.setTextColor(0xff52616b);x.setPadding(0,dp(4),0,dp(12));root.addView(x);}
    private void button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());root.addView(b);}
    private void openMlog(){
        Intent i=getPackageManager().getLaunchIntentForPackage(MlogAccessibilityService.TARGET);
        if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}
        else Toast.makeText(this,"MLOG não está instalado neste coletor.",Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume(){
        super.onResume();
        IntentFilter f=new IntentFilter();for(String a:ScannerProtocol.ACTIONS)f.addAction(a);
        try{
            if(Build.VERSION.SDK_INT>=33)registerReceiver(demo,f,Context.RECEIVER_EXPORTED);
            else registerReceiver(demo,f);
            listening=true;
        }catch(Exception e){if(live!=null)live.setText("Erro no receptor: "+e.getMessage());}
        handler.removeCallbacks(ticker);handler.post(ticker);
    }
    @Override protected void onPause(){
        handler.removeCallbacks(ticker);
        if(listening){try{unregisterReceiver(demo);}catch(Exception ignore){} listening=false;}
        super.onPause();
    }
}
