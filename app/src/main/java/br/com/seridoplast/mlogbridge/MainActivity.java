package br.com.seridoplast.mlogbridge;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


/**
 * v1.7. Interface monocromatica, teclado nativo e recuperacao de Enter.
 * O teste exibe leituras somente na tela; o codigo nao vai para preferências.
 */
public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String themeChoice;
    private int page = 0;
    private int bg, card, line, ink, muted, inverse, inkSurface;
    private LinearLayout root, content, footer;
    private TextView preview, lastStatus, serviceState;
    private boolean receiving;
    private int scanCount;
    private String lastSample;
    private long lastReceivedAt;
    private String lastSymbology;
    private long lastThemeTapAt;

    private final BroadcastReceiver scannerTest = new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(!isActiveAppScreen() || page!=0)return;
            String code=ScannerProtocol.barcode(intent);
            if(code==null){
                lastSample=null;
                if(preview!=null)preview.setText("Sinal recebido sem codigo");
                return;
            }
            if(code.equals(lastSample) && android.os.SystemClock.elapsedRealtime()-lastReceivedAt<320)return;
            lastReceivedAt=android.os.SystemClock.elapsedRealtime();
            scanCount++;
            lastSample=code;
            lastSymbology=ScannerProtocol.type(intent);
            if(preview!=null)preview.setText(code);
            if(lastStatus!=null)lastStatus.setText("Leitura "+scanCount +
                    (lastSymbology==null || lastSymbology.isEmpty()?"":" · "+lastSymbology));
        }
    };
    private boolean isActiveAppScreen(){return !isFinishing() && !isDestroyed();}
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(serviceState!=null){
                boolean active=MlogFocusAccessibilityService.current()!=null;
                serviceState.setText(active?"Acessibilidade ativa":"Acessibilidade desativada");
            }
            if(page==2 && lastStatus!=null){
                lastStatus.setText(prefs.getString("last_status","Nenhum diagnostico ainda."));
            }
            handler.postDelayed(this,1000);
        }
    };

    @Override public void onCreate(Bundle saved){
        prefs=getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);
        themeChoice=prefs.getString("theme_choice","auto");
        // Corrige preferencia que podia ter sido alterada por Enter do scanner
        // em versoes antigas. A partir de agora, apenas toque real alterna tema.
        if(!prefs.getBoolean("migrated_theme_v8",false)){
            prefs.edit().putString("theme_choice","auto")
                .putBoolean("migrated_theme_v8",true).apply();
            themeChoice="auto";
        }
        if(!prefs.getBoolean("migrated_scan_v9",false)){
            // Remover ocultacao antiga e manter comportamento do scanner original.
            prefs.edit().putBoolean("hide_keyboard_on_scan",false)
                .putBoolean("layout_fallback",true)
                .putBoolean("migrated_scan_v9",true).apply();
        }
        if(!prefs.getBoolean("migrated_enter_v10",false)){
            // Registro da migracao anterior; a confirmacao agora e automatica,
            // sem preferencia visivel.
            prefs.edit().putBoolean("migrated_enter_v10",true).apply();
        }
        if(!prefs.getBoolean("migrated_bipe_only_v13",false)){
            // O teclado nativo deve ser acionado apenas com um bipe real.
            prefs.edit().putBoolean("prearm_field",false)
                .putBoolean("migrated_bipe_only_v13",true).apply();
        }
        boolean dark=darkNow();
        setTheme(dark?R.style.BridgeThemeDark:R.style.BridgeThemeLight);
        super.onCreate(saved);
        if(saved!=null)page=saved.getInt("page",0);
        palette(dark);
        displayPage();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("page",page);}
    private void palette(boolean dark){
        bg=dark?0xff0b0b0b:0xfffafafa;
        card=dark?0xff181818:0xffffffff;
        line=dark?0xff303030:0xffe5e5e5;
        ink=dark?0xfff6f6f6:0xff151515;
        muted=dark?0xffa6a6a6:0xff696969;
        inverse=dark?0xff111111:0xffffffff;
        inkSurface=ink;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);
        return d;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setIncludeFontPadding(true);return t;
    }
    private void space(LinearLayout dest,int px){View v=new View(this);dest.addView(v,new LinearLayout.LayoutParams(1,dp(px)));}
    private LinearLayout card(){
        LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));
        c.setBackground(shape(card,line,20));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(14);
        content.addView(c,cp);return c;
    }
    private void subtitle(LinearLayout c,String value){
        TextView t=label(value,13,muted,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(6);c.addView(t,lp);
    }
    private TextView action(String name,boolean filled,Runnable callback){
        TextView b=label(name,14,filled?inverse:ink,true);
        b.setGravity(Gravity.CENTER);b.setPadding(dp(14),dp(13),dp(14),dp(13));
        b.setBackground(shape(filled?ink:card,filled?0:line,13));
        b.setFocusable(false); // leitor em modo teclado nao deve ativar controles com Enter
        b.setFocusableInTouchMode(false);
        b.setOnClickListener(w->callback.run());b.setClickable(true);
        return b;
    }
    private void button(LinearLayout dest,String text,boolean filled,Runnable callback){
        TextView b=action(text,filled,callback);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);
        dest.addView(b,p);
    }
    private void displayPage(){
        root=column();root.setBackgroundColor(bg);
        setContentView(root);
        LinearLayout head=row();head.setPadding(dp(20),dp(19),dp(20),dp(13));
        LinearLayout left=row();
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.brand_monochrome);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setColorFilter(ink);
        left.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView name=label("mlog bridge",19,ink,true);
        LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(-2,-2);nl.leftMargin=dp(12);
        left.addView(name,nl);
        head.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        TextView moon=action(darkNow()?"☀":"☾",false,()->{});
        moon.setContentDescription("Alternar tema com toque na tela");
        // Apenas um toque fisico: o Enter/Tab enviado pelo scanner nunca alterna tema.
        moon.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_UP){
                long now=android.os.SystemClock.elapsedRealtime();
                if(now-lastThemeTapAt>650){lastThemeTapAt=now;nextTheme();}
                return true;
            }
            return true;
        });
        head.addView(moon,new LinearLayout.LayoutParams(dp(49),dp(48)));
        root.addView(head);
        View divider=new View(this);divider.setBackgroundColor(line);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setClipToPadding(false);
        content=column();content.setPadding(dp(20),dp(20),dp(20),dp(14));sc.addView(content);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        footer=row();footer.setPadding(dp(10),dp(8),dp(10),dp(9));
        String[] names={"Início","Ajustes","Diagnóstico","Sobre"};
        for(int i=0;i<names.length;i++){
            final int target=i;
            TextView tab=label(names[i],12,i==page?inverse:muted,i==page);
            tab.setGravity(Gravity.CENTER);tab.setPadding(dp(7),dp(13),dp(7),dp(13));
            tab.setBackground(shape(i==page?ink:bg,i==page?0:line,13));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(4);
            footer.addView(tab,tp);tab.setOnClickListener(v->show(target));
        }
        root.addView(footer);
        preview=null;lastStatus=null;serviceState=null;
        if(page==0)home();else if(page==1)settings();else if(page==2)diagnostic();else about();
    }
    private void show(int p){page=p;displayPage();}
    private void title(String s,String explanation){
        TextView t=label(s,27,ink,true);content.addView(t);
        if(explanation!=null && !explanation.isEmpty()){
            TextView e=label(explanation,14,muted,false);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(5);lp.bottomMargin=dp(22);
            content.addView(e,lp);
        }else space(content,22);
    }
    private void home(){
        title("Leitura de códigos","Aperte o gatilho físico para testar. Configuração do CMX preservada.");
        LinearLayout sample=card();
        TextView statusLabel=label("TESTE DO SCANNER",11,muted,true);sample.addView(statusLabel);
        space(sample,20);
        TextView scanner=label("▥",43,ink,true);scanner.setGravity(Gravity.CENTER);sample.addView(scanner);
        preview=label(lastSample==null?"Aguardando leitura...":lastSample,19,ink,true);
        preview.setGravity(Gravity.CENTER);preview.setTextIsSelectable(true);sample.addView(preview);
        lastStatus=label(scanCount==0?"Aperte o gatilho do coletor":"Leitura "+scanCount,12,muted,false);
        lastStatus.setGravity(Gravity.CENTER);space(sample,9);sample.addView(lastStatus);
        space(sample,18);
        subtitle(sample,"Use o gatilho fisico do coletor para testar a leitura. O Bridge nao modifica a configuracao do CMX.");
        LinearLayout service=card();
        service.addView(label("Serviço",17,ink,true));
        serviceState=label("Verificando acessibilidade...",14,muted,false);
        space(service,8);service.addView(serviceState);
        button(service,"Abrir MLOG",true,this::openMlog);
        button(service,"Acessibilidade do Android",false,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }
    private void settings(){
        title("Ajustes","Preferências de leitura e aparência.");
        LinearLayout read=card();read.addView(label("Leitura",17,ink,true));
        check(read,"Leitura assistida","enabled",false);
        subtitle(read,"O Bridge clica no botao de teclado do MLOG antes de inserir. Gboard continua disponivel.");
        // Preparacao antecipada removida: era a causa de campo abrindo antes do bipe.
        check(read,"Toque guiado (telas reconhecidas)","guided_taps",true);
        check(read,"Detectar teclado de produtos","layout_fallback",true);
        subtitle(read,"O CMX envia Enter primeiro. Se o codigo permanecer no campo, o Bridge tenta a tecla de confirmacao do Gboard. Sem ajuste duplicado.");
        LinearLayout appearance=card();appearance.addView(label("Aparência",17,ink,true));
        subtitle(appearance,"Seguir o sistema ou escolher manualmente.");
        LinearLayout bar=row();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(14);
        for(String[] option:new String[][]{{"auto","Sistema"},{"dark","Escuro"},{"light","Claro"}}){
            boolean selected=option[0].equals(themeChoice);
            TextView v=action(option[1],selected,()->chooseTheme(option[0]));
            LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,-2,1);op.leftMargin=dp(4);
            bar.addView(v,op);
        }
        appearance.addView(bar,bp);
        LinearLayout cmx=card();cmx.addView(label("CMX Scanner",17,ink,true));
        subtitle(cmx,"O Bridge agora apenas recebe leituras. Configure formatos, modo de saida e Enter no aplicativo CMX original.");
    }
    private void diagnostic(){
        title("Diagnóstico","Se uma tela nao funcionar, copie este resultado.");
        LinearLayout card=card();card.addView(label("Último evento",17,ink,true));
        lastStatus=label(prefs.getString("last_status","Nenhum diagnóstico ainda."),14,muted,false);
        lastStatus.setTextIsSelectable(true);space(card,11);card.addView(lastStatus);
        button(card,"Copiar diagnóstico",false,()->{
            android.content.ClipboardManager m=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(m!=null)m.setPrimaryClip(android.content.ClipData.newPlainText("MLOG Bridge diagnóstico",lastStatus.getText()));
            toast("Diagnóstico copiado. Não inclui códigos.");
        });
        LinearLayout help=card();help.addView(label("O que testar",17,ink,true));
        subtitle(help,"Leitura aguarda o foco do campo. Se o MLOG nao disponibilizar insercao de imediato, tenta novamente. O Enter usa primeiro o CMX e depois o Gboard reconhecido. Teste fora da operacao.");
        button(help,"Abrir MLOG",true,this::openMlog);
    }
    private void about(){
        title("Sobre","MLOG Scanner Bridge · v1.7");
        LinearLayout about=card();about.addView(label("Integração local",17,ink,true));
        subtitle(about,"Criado para o scanner físico CMX e o MLOG. Sem acesso à internet. Os códigos de leitura não são salvos em histórico.");
        space(about,10);
        subtitle(about,"Acessibilidade limitada ao pacote do MLOG. Toques automatizados e campos Flutter exigem testes no coletor.");
        LinearLayout control=card();control.addView(label("Permissões",17,ink,true));
        button(control,"Informações do app",false,()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        button(control,"Acessibilidade",false,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        subtitle(control,"O Android precisa autorizar o serviço. Não desative o Play Protect para usar o Bridge.");
    }
    private void check(LinearLayout dest,String text,String key,boolean fallback){
        CheckBox b=new CheckBox(this);b.setButtonTintList(android.content.res.ColorStateList.valueOf(ink));
        b.setText(text);b.setTextColor(ink);b.setTextSize(14);
        b.setPadding(0,dp(5),0,dp(5));b.setChecked(prefs.getBoolean(key,fallback));
        b.setOnCheckedChangeListener((control,on)->prefs.edit().putBoolean(key,on).apply());
        dest.addView(b);
    }
    private void openMlog(){
        Intent i=getPackageManager().getLaunchIntentForPackage(ScanCoordinator.TARGET);
        if(i==null){toast("MLOG não encontrado.");return;}
        startActivity(i);
    }
    private boolean darkNow(){
        if("dark".equals(themeChoice))return true;
        if("light".equals(themeChoice))return false;
        return (getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }
    private void chooseTheme(String value){
        prefs.edit().putString("theme_choice",value).apply();themeChoice=value;
        recreate();
    }
    private void nextTheme(){chooseTheme(darkNow()?"light":"dark");}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        // Protege botoes e tema contra Enter/Tab emitidos pelo scanner (keyboard wedge).
        // Esta tela nao tem campo de texto para edicao manual.
        int k=event.getKeyCode();
        if(k==KeyEvent.KEYCODE_ENTER || k==KeyEvent.KEYCODE_NUMPAD_ENTER ||
           k==KeyEvent.KEYCODE_TAB) return true;
        return super.dispatchKeyEvent(event);
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){
        super.onResume();
        IntentFilter f=new IntentFilter();for(String a:ScannerProtocol.ACTIONS)f.addAction(a);
        try{
            if(Build.VERSION.SDK_INT>=33)registerReceiver(scannerTest,f,Context.RECEIVER_EXPORTED);
            else registerReceiver(scannerTest,f);
            receiving=true;
        }catch(RuntimeException ignored){}
        handler.post(ticker);
    }
    @Override protected void onPause(){
        handler.removeCallbacks(ticker);
        if(receiving)try{unregisterReceiver(scannerTest);}catch(RuntimeException ignored){}
        receiving=false;
        super.onPause();
    }
}
