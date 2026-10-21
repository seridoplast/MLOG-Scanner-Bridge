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
import android.content.res.Configuration;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.RadioButton;
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
    private int ink, subInk, panel;
    private String themeChoice;
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
        // Escolhe o estilo ANTES da criacao das views; AUTO segue o tema Android.
        SharedPreferences uiPrefs=getSharedPreferences(ScanCoordinator.PREFS, MODE_PRIVATE);
        themeChoice=uiPrefs.getString("theme_choice", "auto");
        boolean dark=effectiveDark(themeChoice);
        setTheme(dark ? R.style.BridgeThemeDark : R.style.BridgeThemeLight);
        super.onCreate(saved);
        prefs=uiPrefs;
        ink=dark?0xfff4f6fa:0xff183145;
        subInk=dark?0xffbec9d2:0xff52616b;
        panel=dark?0xff243547:0xffe8f0f4;
        ScrollView v=new ScrollView(this);
        layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18),dp(16),dp(18),dp(32));
        v.addView(layout);setContentView(v);
        layout.setBackgroundColor(dark?0xff121d29:0xfff7f9fc);

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView title=new TextView(this);
        title.setText("MLOG Scanner Bridge v0.4");
        title.setTextSize(21);
        title.setTextColor(ink);
        title.setTypeface(null,1);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
        Button moon=new Button(this);
        moon.setText(dark?"☀":"☾");
        moon.setAllCaps(false);
        moon.setContentDescription("Alternar tema: automatico, escuro e claro");
        moon.setOnClickListener(w->cycleTheme());
        top.addView(moon,new LinearLayout.LayoutParams(dp(64),-2));
        layout.addView(top);
        text("Tema atual: "+themeName(themeChoice)+". Use o botao ☾/☀ ou Configuracoes abaixo.");
        heading("⚙ Configuracoes / Aparencia");
        text("Automatico acompanha o modo claro ou escuro configurado no Android.");
        RadioGroup themeOptions=new RadioGroup(this);
        themeOptions.setOrientation(RadioGroup.HORIZONTAL);
        String[] modes={"auto","dark","light"};
        String[] captions={"Sistema","Escuro","Claro"};
        for(int j=0;j<modes.length;j++){
            final String mode=modes[j];
            RadioButton b=new RadioButton(this);
            b.setId(View.generateViewId());
            b.setText(captions[j]);
            b.setTextColor(ink);
            b.setTextSize(14);
            b.setChecked(mode.equals(themeChoice));
            themeOptions.addView(b,new RadioGroup.LayoutParams(0,-2,1));
            b.setOnClickListener(w->chooseTheme(mode));
        }
        layout.addView(themeOptions);

        heading("MLOG + CMX");
        text("Foco inteligente para CMX + MLOG. Usa teclado de leitura e acessibilidade OPCIONAL e limitada ao MLOG.");
        text("AVISO: a acessibilidade consegue ler elementos da tela do MLOG. Nao habilite ate entender isso. Nao ha internet, coleta em segundo plano nem armazenamento de codigos. Ainda e versao de testes.");

        heading("1. Permissoes e seguranca");
        button("Abrir acessibilidade no Android",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button("Abrir informacoes deste aplicativo",()->{
            Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+getPackageName()));
            startActivity(i);
        });
        text("Acessibilidade e opcional e depende da autorizacao do Android. Se aparecer 'Configuracao restrita': tente ativar o servico, volte a Configuracoes > Aplicativos > Ver todos > MLOG Scanner Bridge e procure 'Permitir configuracoes restritas' no menu ou na pagina. Se nao existir, a ROM CMX pode nao permitir essa autorizacao; nao desative o Play Protect e nao tente burlar a protecao. Use o teclado v2 ate haver distribuicao gerenciada ou uma configuracao autorizada pelo fabricante.");
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
        status.setBackgroundColor(panel);status.setTextColor(ink);layout.addView(status);
        button("Abrir MLOG",this::openMlog);

        heading("2. Scanner CMX");
        text("Configura o coletor para transmitir leituras por broadcast. Isso altera a configuracao do leitor fisico, nao do MLOG.");
        button("Configurar scanner para broadcast",()->{
            ScannerProtocol.configureBroadcast(this);toast("Comandos enviados. Teste a leitura abaixo.");
        });
        button("Acionar scanner (teste)",()->ScannerProtocol.trigger(this));
        preview=new TextView(this);preview.setText("Teste local: aguardando codigo...");
        preview.setBackgroundColor(panel);preview.setTextColor(ink);preview.setPadding(dp(12),dp(12),dp(12),dp(12));
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
        v.setTextColor(ink);v.setPadding(0,dp(16),0,dp(6));layout.addView(v);
    }
    private void text(String t){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(14);
        v.setTextColor(subInk);v.setPadding(0,dp(5),0,dp(10));layout.addView(v);
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
    private boolean effectiveDark(String mode){
        if("dark".equals(mode))return true;
        if("light".equals(mode))return false;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
    private String themeName(String mode){
        if("dark".equals(mode))return "escuro";
        if("light".equals(mode))return "claro";
        return "automatico (Android)";
    }
    private void chooseTheme(String mode){
        if(!mode.equals(themeChoice)){
            prefs.edit().putString("theme_choice",mode).apply();
            recreate();
        }
    }
    private void cycleTheme(){
        String next="auto".equals(themeChoice)?"dark":
                    "dark".equals(themeChoice)?"light":"auto";
        chooseTheme(next);
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
