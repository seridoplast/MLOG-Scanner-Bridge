package br.com.seridoplast.mlogbridge;

import android.app.Activity;
import android.app.AlertDialog;
import androidx.core.content.FileProvider;
import java.io.File;
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
import android.widget.EditText;
import android.text.InputType;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodInfo;
import java.util.List;


/**
 * v1.8. Interface monocromatica, teclado nativo e recuperacao de Enter.
 * O teste exibe leituras somente na tela; o codigo nao vai para preferências.
 */
public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String themeChoice;
    private int page = 0;
    private int bg, card, line, ink, muted, inverse, inkSurface;
    private LinearLayout root, content, footer;
    private TextView preview, lastStatus, barcodeFormatStatus, barcodeArtifactStatus, barcodeFieldsStatus, serviceState;
    private LinearLayout setupWarningCard;
    private TextView setupWarningLabel;
    private TextView setupAccessibilityStep, setupKeyboardEnabledStep,
            setupKeyboardSelectedStep, setupFloatingStep;
    private boolean receiving;
    private int scanCount;
    private String lastSample;
    private long lastReceivedAt;
    private String lastSymbology;
    private String lastMetadataFields;
    private long lastThemeTapAt;
    private TextView githubUpdateStatus;
    private boolean githubChecking;
    private File githubPendingInstall;

    private final BroadcastReceiver scannerTest = new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(!isActiveAppScreen() || page!=0)return;
            String code=ScannerProtocol.barcodeRaw(intent);
            if(code==null){
                lastSample=null;
                if(preview!=null)preview.setText("Sinal recebido sem codigo");
                if(barcodeFormatStatus!=null)barcodeFormatStatus.setText("Formato lido: nao informado");
                if(barcodeArtifactStatus!=null)barcodeArtifactStatus.setText("Não foi possível inspecionar o código recebido.");
                return;
            }
            if(code.equals(lastSample) && android.os.SystemClock.elapsedRealtime()-lastReceivedAt<140){
                // Alguns CMX enviam dois broadcasts para a mesma leitura.
                // Aproveitar metadados do segundo mesmo que o valor seja eco.
                String maybeType=ScannerProtocol.type(intent);
                if(!BarcodeFormatLabel.UNKNOWN.equals(maybeType)){
                    lastSymbology=maybeType;
                    if(barcodeFormatStatus!=null)barcodeFormatStatus.setText("Formato lido: "+maybeType);
                }
                return;
            }
            lastReceivedAt=android.os.SystemClock.elapsedRealtime();
            scanCount++;
            lastSample=code;
            lastSymbology=ScannerProtocol.type(intent);
            lastMetadataFields=ScannerProtocol.receivedFields(intent);
            String visibleCode=BarcodeCleaner.clean(code);
            if(preview!=null)preview.setText(visibleCode==null || visibleCode.isEmpty()?
                    "Somente caracteres invisíveis":visibleCode);
            if(barcodeArtifactStatus!=null){
                String report=BarcodeArtifactReport.describe(code);
                barcodeArtifactStatus.setText(report);
                barcodeArtifactStatus.setTextColor(report.startsWith("Atenção:")?0xffd97706:muted);
            }
            if(lastStatus!=null)lastStatus.setText("Leitura "+scanCount);
            if(barcodeFormatStatus!=null)barcodeFormatStatus.setText("Formato lido: "+lastSymbology);
            if(barcodeFieldsStatus!=null){
                barcodeFieldsStatus.setText(BarcodeFormatLabel.UNKNOWN.equals(lastSymbology)?
                    "CMX não informou o tipo. Campos recebidos: "+lastMetadataFields:
                    "Simbologia declarada pelo scanner. Valor acima é o conteúdo decodificado.");
            }
        }
    };
    private boolean isActiveAppScreen(){return !isFinishing() && !isDestroyed();}
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(serviceState!=null){
                boolean active=MlogFocusAccessibilityService.current()!=null;
                serviceState.setText(active?"Acessibilidade ativa":"Acessibilidade desativada");
            }
            refreshSetupWarning();
            if(page==2 && lastStatus!=null){
                lastStatus.setText(prefs.getString("last_status","Nenhum diagnostico ainda."));
            }
            handler.postDelayed(this,1000);
        }
    };

    @Override public void onCreate(Bundle saved){
        prefs=getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);
        BridgePreferences.enableOnUpgrade(prefs);
        if(!ScanAssistState.AUTO_BIPS_ENABLED){
            prefs.edit().putBoolean(ScanAssistState.ARMED,false)
                .putBoolean(ScanAssistState.BATCH,false).apply();
        }
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
        // Pre-foco autorizado na v2.3. Se o usuario desligar depois,
        // esta migracao nao volta a alterar a escolha.
        if(!prefs.getBoolean("prefocus_v23_migrated",false)){
            prefs.edit().putBoolean("trigger_prefocus",true)
                .putBoolean("prefocus_v23_migrated",true).apply();
        }
        boolean dark=darkNow();
        setTheme(dark?R.style.BridgeThemeDark:R.style.BridgeThemeLight);
        super.onCreate(saved);
        if(saved!=null)page=saved.getInt("page",0);
        palette(dark);
        displayPage();
        MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
        if(svc!=null)svc.refreshEventThrottle();
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
        TextView t=label(value,12,muted,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
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
        ImageView logo=new ImageView(this);logo.setImageResource(darkNow()?R.drawable.brand_home_dark:R.drawable.brand_home_light);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // Logo 2 acompanha claro/escuro; a cor azul permanece.

        left.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView name=label("mlog bridge",19,ink,true);
        LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(-2,-2);nl.leftMargin=dp(12);
        left.addView(name,nl);
        head.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        TextView moon=action(darkNow()?"☀":"☾",false,()->{});
        moon.setContentDescription("Alternar tema com toque na tela");
        // Apenas toque fisico: o Enter do scanner nunca alterna tema.
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
        githubUpdateStatus=null;
        preview=null;lastStatus=null;barcodeFormatStatus=null;barcodeArtifactStatus=null;
        barcodeFieldsStatus=null;serviceState=null;setupWarningCard=null;setupWarningLabel=null;setupAccessibilityStep=null;setupKeyboardEnabledStep=null;setupKeyboardSelectedStep=null;setupFloatingStep=null;
        if(page==0)home();else if(page==1)settings();else if(page==2)diagnostic();else about();
    }
    private void show(int p){page=p;displayPage();}
    private void title(String s,String explanation){
        TextView t=label(s,27,ink,true);content.addView(t);
        if(explanation!=null && !explanation.isEmpty()){
            TextView e=label(explanation,12,muted,false);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(5);lp.bottomMargin=dp(22);
            content.addView(e,lp);
        }else space(content,22);
    }
    // Android sempre exige que o operador autorize acessibilidade, IME e overlay.
    // Nunca tentar ativar permissões protegidas silenciosamente.
    private boolean accessibilityReady(){
        return MlogFocusAccessibilityService.current()!=null;
    }
    private boolean keyboardEnabled(){
        InputMethodManager manager=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(manager==null)return false;
        for(InputMethodInfo ime:manager.getEnabledInputMethodList()){
            if(getPackageName().equals(ime.getPackageName()))return true;
        }
        return false;
    }
    private boolean keyboardSelected(){
        String selected=Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
        return keyboardEnabled() && selected!=null && selected.startsWith(getPackageName()+"/");
    }
    private boolean keyboardReady(){return keyboardSelected();}
    private boolean floatingReady(){
        boolean allowed=Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this);
        return allowed && FloatingToolsService.isRunning();
    }
    private String missingRequiredSetup(){
        if(prefs.getBoolean(ZebraImeMode.PREF,false)){
            return keyboardReady()?"":"• Selecione o teclado Bridge nas configuracoes do Android";
        }
        StringBuilder missing=new StringBuilder();
        if(!accessibilityReady())missing.append("• Acessibilidade desativada\n");
        if(!keyboardReady())missing.append("• Teclado Bridge não habilitado/selecionado\n");
        if(!floatingReady())missing.append("• Botão flutuante não autorizado/ativado\n");
        return missing.toString().trim();
    }
    private void openAccessibilitySettings(){
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
    private void openKeyboardSettings(){
        startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
    }
    private void openOverlaySettings(){
        prefs.edit().putBoolean("floating_auto_start_v302",true).apply();
        if(Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this)){
            startService(new Intent(this,FloatingToolsService.class));
            return;
        }
        if(Build.VERSION.SDK_INT>=23){
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:"+getPackageName())));
        }
    }
    /** Cada autorização mostra um check verde ASSIM QUE o Android confirmar. */
    private TextView setupStep(String title,Runnable open){
        TextView t=label(title,14,0xffffffff,true);
        t.setPadding(dp(13),dp(12),dp(13),dp(12));
        t.setBackground(shape(0xff742b32,0xff9a5158,12));
        t.setOnClickListener(v->open.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(8);setupWarningCard.addView(t,lp);
        return t;
    }
    private void updateSetupStep(TextView t,boolean ready,String label){
        if(t==null)return;
        t.setText((ready?"✓  ":"○  ")+label+(ready?"  — Ativado":"  — Pendente"));
        t.setTextColor(ready?0xffdcffe8:0xffffffff);
        t.setBackground(shape(ready?0xff167a43:0xff742b32,
                              ready?0xff28d17b:0xff9a5158,12));
        t.setContentDescription(label+(ready?", concluído":", precisa ativar"));
    }
    private void showSetupWarning(){
        setupWarningCard=column();
        setupWarningCard.setPadding(dp(17),dp(15),dp(17),dp(17));
        setupWarningCard.setBackground(shape(0xff641e25,0xffd85762,16));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(14);content.addView(setupWarningCard,lp);
        setupWarningCard.addView(label("⚠ Configuração necessária",17,0xffffffff,true));
        setupWarningLabel=label("",13,0xffffffff,false);
        space(setupWarningCard,7);setupWarningCard.addView(setupWarningLabel);
        setupAccessibilityStep=setupStep("1. Acessibilidade",this::openAccessibilitySettings);
        setupKeyboardEnabledStep=setupStep("2. Habilitar teclado Bridge",this::openKeyboardSettings);
        setupKeyboardSelectedStep=setupStep("3. Selecionar teclado Bridge",()->{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showInputMethodPicker();
        });
        setupFloatingStep=setupStep("4. Botão flutuante",this::openOverlaySettings);
        refreshSetupWarning();
    }
    private void refreshSetupWarning(){
        if(setupWarningCard==null || setupWarningLabel==null)return;
        boolean access=accessibilityReady();
        boolean enabled=keyboardEnabled();
        boolean selected=keyboardSelected();
        boolean floating=floatingReady();
        updateSetupStep(setupAccessibilityStep,access,"Acessibilidade");
        updateSetupStep(setupKeyboardEnabledStep,enabled,"Teclado habilitado");
        updateSetupStep(setupKeyboardSelectedStep,selected,"Teclado selecionado");
        updateSetupStep(setupFloatingStep,floating,"Botão flutuante");
        boolean complete=access && enabled && selected && floating;
        setupWarningCard.setVisibility(complete?View.GONE:View.VISIBLE);
        setupWarningLabel.setText(complete?"Tudo configurado.":
            "Ativado: "+((access?1:0)+(enabled?1:0)+(selected?1:0)+(floating?1:0))+
            "/4. Toque nas pendências.");
    }
    private void maybeShowFirstSetupDialog(){
        if(prefs.getBoolean("first_setup_prompt_v302",false))return;
        prefs.edit().putBoolean("first_setup_prompt_v302",true).apply();
        if(missingRequiredSetup().isEmpty())return;
        new AlertDialog.Builder(this)
            .setTitle("Configurar MLOG Scanner Bridge")
            .setMessage("Ative Acessibilidade, Teclado Bridge e Botão flutuante. " +
                "Confirme as permissões no Android.")
            .setPositiveButton("Começar",(dialog,which)->openAccessibilitySettings())
            .setNegativeButton("Depois",(dialog,which)->{})
            .show();
    }

    private void home(){
        showSetupWarning();
        title("Leitura de códigos","Bipe para testar o leitor.");
        subtitle(content, "Modo ativo: "+(prefs.getBoolean(ZebraImeMode.PREF,false)?
            "Bipe Zebra (IME)":"Bipe antigo (acessibilidade)"));
        LinearLayout sample=card();
        TextView statusLabel=label("TESTE DO SCANNER",11,muted,true);sample.addView(statusLabel);
        space(sample,20);
        TextView scanner=label("▥",43,ink,true);scanner.setGravity(Gravity.CENTER);sample.addView(scanner);
        preview=label(lastSample==null?"Aguardando leitura...":BarcodeCleaner.clean(lastSample),19,ink,true);
        preview.setGravity(Gravity.CENTER);preview.setTextIsSelectable(true);sample.addView(preview);
        lastStatus=label(scanCount==0?"Aperte o gatilho do coletor":"Leitura "+scanCount,12,muted,false);
        lastStatus.setGravity(Gravity.CENTER);space(sample,9);sample.addView(lastStatus);
        barcodeFormatStatus=label("Formato lido: "+(lastSymbology==null?"Aguardando leitura":lastSymbology),14,ink,true);
        barcodeFormatStatus.setGravity(Gravity.CENTER);
        space(sample,6);sample.addView(barcodeFormatStatus);
        space(sample,4);
        barcodeFieldsStatus=label(lastSample==null?"O CMX ainda não enviou metadados.":
            BarcodeFormatLabel.UNKNOWN.equals(lastSymbology)?
                "CMX não informou o tipo. Campos recebidos: "+lastMetadataFields:
                "Simbologia informada pelo leitor.",11,muted,false);
        barcodeFieldsStatus.setGravity(Gravity.CENTER);
        sample.addView(barcodeFieldsStatus);
        space(sample,10);
        barcodeArtifactStatus=label(lastSample==null?
            "Aguardando leitura para verificar caracteres invisíveis.":
            BarcodeArtifactReport.describe(lastSample),12,muted,false);
        barcodeArtifactStatus.setGravity(Gravity.CENTER);
        barcodeArtifactStatus.setTextColor(lastSample!=null &&
            BarcodeArtifactReport.describe(lastSample).startsWith("Atenção:")?0xffd97706:muted);
        sample.addView(barcodeArtifactStatus);
        space(sample,18);
        LinearLayout service=card();
        service.addView(label("Serviço",17,ink,true));
        serviceState=label("Verificando acessibilidade...",14,muted,false);
        space(service,8);service.addView(serviceState);
        LinearLayout tools=card();
        tools.addView(label("Ferramentas flutuantes",17,ink,true));
        subtitle(tools,"Tratador de caracteres e ajustes rápidos.");
        button(tools,"Ativar / abrir botão flutuante",true,this::openFloatingTools);
        button(tools,"Desativar botão flutuante",false,()->{
            stopService(new Intent(this,FloatingToolsService.class));
            prefs.edit().putBoolean("floating_auto_start_v302",false)
                .putBoolean(ScanAssistState.CLEAN,false)
                .putBoolean(ScanAssistState.BATCH,false)
                .putBoolean(ScanAssistState.ARMED,false).apply();
            toast("Ferramentas desligadas.");
        });
        button(service,"Abrir MLOG",true,this::openMlog);
        button(service,"Acessibilidade do Android",false,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }
    /** Dois modos exclusivos: antigo por acessibilidade ou Zebra por IME.
     * Ambos utilizam a mesma decodificacao nativa, nunca digitam simultaneamente. */
    private void selectReadMode(boolean zebra){
        boolean current=prefs.getBoolean(ZebraImeMode.PREF,false);
        if(current==zebra){
            String key=zebra?"zebra_options_expanded_v327":"legacy_options_expanded_v327";
            prefs.edit().putBoolean(key,!prefs.getBoolean(key,true)).apply();
            displayPage();return;
        }
        if(zebra && !NativeScannerController.isInstalled(this)){
            toast("Servico nativo do leitor nao encontrado. Modo antigo preservado.");return;
        }
        if(zebra && !NativeScannerController.configure(this)){
            toast("Nao foi possivel enviar configuracao ao leitor.");return;
        }
        prefs.edit()
            .putBoolean(ZebraImeMode.PREF,zebra)
            .putBoolean(NativeScannerController.PREF,true)
            .putBoolean(DirectScanPolicy.PREF,true)
            .putBoolean(BridgeEnterPolicy.PREFERENCE,!zebra)
            .putBoolean("enabled",true)
            .putBoolean(zebra?"zebra_options_expanded_v327":"legacy_options_expanded_v327",true)
            .apply();
        BridgeScanInputMethodService.resetForModeChange();
        MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
        if(svc!=null)svc.refreshEventThrottle();
        displayPage();
        if(zebra && !keyboardSelected()){
            new AlertDialog.Builder(this)
                .setTitle("Selecionar teclado Bridge")
                .setMessage("O modo Zebra exige selecionar MLOG Bridge como teclado no Android e abrir o campo de leitura no MLOG. O teclado nao pode criar foco sozinho sem acessibilidade.")
                .setPositiveButton("Escolher teclado",(dialog,which)->{
                    if(!keyboardEnabled())startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                    else{
                        InputMethodManager im=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                        if(im!=null)im.showInputMethodPicker();
                    }
                })
                .setNegativeButton("Depois",null).show();
        }
    }
    private void settings(){
        title("Ajustes","Selecione um modo de leitura.");
        final boolean zebra=prefs.getBoolean(ZebraImeMode.PREF,false);
        LinearLayout modeOld=card();
        modeOld.addView(label(zebra?"Bipe antigo":"✓ Bipe antigo · Ativo",18,ink,true));
        subtitle(modeOld,"Leitor nativo + foco e Enter pela acessibilidade.");
        button(modeOld,zebra?"Ativar bipe antigo":
            (prefs.getBoolean("legacy_options_expanded_v327",true)?"Ocultar ajustes":"Configurar bipe antigo"),
            !zebra,()->selectReadMode(false));
        if(!zebra && prefs.getBoolean("legacy_options_expanded_v327",true)){
            space(modeOld,8);
            check(modeOld,"Leitura assistida","enabled",true);
            check(modeOld,"Detectar botao de teclado","layout_fallback",true);
            check(modeOld,"Toques guiados em telas reconhecidas","guided_taps",true);
            check(modeOld,"Enter pelo Bridge",BridgeEnterPolicy.PREFERENCE,true);
            check(modeOld,"Repetir ao segurar gatilho 1 s (teste)",
                NativeScannerController.LONG_HOLD_PREF,false);
            button(modeOld,"Reaplicar configuracao do leitor",false,()->{
                boolean ok=NativeScannerController.configure(this);
                toast(ok?"Comandos enviados ao scanner.":"Nao foi possivel configurar scanner.");
            });
            button(modeOld,"Abrir Acessibilidade",false,this::openAccessibilitySettings);
        }
        LinearLayout modeZebra=card();
        modeZebra.addView(label(zebra?"✓ Bipe Zebra · Ativo":"Bipe Zebra",18,ink,true));
        subtitle(modeZebra,"Codigo e Enter pelo teclado Android do Bridge (IME).");
        button(modeZebra,zebra?
            (prefs.getBoolean("zebra_options_expanded_v327",true)?"Ocultar ajustes":"Configurar bipe Zebra"):
            "Ativar bipe Zebra",zebra,()->selectReadMode(true));
        if(zebra && prefs.getBoolean("zebra_options_expanded_v327",true)){
            space(modeZebra,8);
            check(modeZebra,"Enter depois do codigo",ZebraImeMode.ENTER_PREF,true);
            check(modeZebra,"Caracteres como teclas (teste DataWedge)",
                ZebraImeMode.CHAR_EVENTS_PREF,false);
            check(modeZebra,"Mostrar barra do teclado",ZebraImeMode.SHOW_PREF,true);
            check(modeZebra,"Abrir campo pelo Bridge (precisa acessibilidade)",
                ZebraImeMode.AUTO_FOCUS_PREF,true);
            check(modeZebra,"Ação IME se nenhum Enter for aceito (teste)",
                ZebraImeMode.ACTION_FALLBACK_PREF,false);
            button(modeZebra,"1. Habilitar teclado no Android",false,
                ()->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
            button(modeZebra,"2. Selecionar teclado Bridge",true,()->{
                InputMethodManager im=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(im!=null)im.showInputMethodPicker();
            });
            subtitle(modeZebra,"Teclado: "+(keyboardSelected()?"selecionado ✓":
                keyboardEnabled()?"habilitado, mas nao selecionado":"nao habilitado"));
            subtitle(modeZebra,"Campo do MLOG: "+
                (BridgeScanInputMethodService.hasMlogInput()?"conectado ✓":"sem foco / desconectado"));
            button(modeZebra,"3. Abrir MLOG e tocar no icone de teclado",false,this::openMlog);
            button(modeZebra,"Reaplicar configuracao do leitor",false,()->{
                toast(NativeScannerController.configure(this)?
                    "Comandos enviados. Teste o bipe no MLOG.":"Scanner indisponivel.");
            });
            subtitle(modeZebra,"Se o Android nao abriu o campo, toque no icone de teclado do proprio MLOG. " +
                "A abertura automatica opcional usa acessibilidade apenas para foco; codigo e Enter continuam pelo IME.");
        }
        LinearLayout tempo=card();
        tempo.addView(label("Intervalo entre leituras (ms)",17,ink,true));
        int current=prefs.getInt(ScanAssistState.CLICK_COOLDOWN_MS,100);
        TextView timeLabel=label("Atual: "+current+" ms",14,ink,true);
        space(tempo,8);tempo.addView(timeLabel);
        LinearLayout rowTime=row();
        EditText value=new EditText(this);
        value.setInputType(InputType.TYPE_CLASS_NUMBER);
        value.setSingleLine(true);
        value.setText(String.valueOf(current));
        value.setTextColor(ink);
        rowTime.addView(value,new LinearLayout.LayoutParams(0,dp(54),1));
        rowTime.addView(label(" ms",13,muted,false));
        tempo.addView(rowTime);
        button(tempo,"Aplicar intervalo",true,()->{
            int ms;
            try{ms=Integer.parseInt(value.getText().toString().trim());}
            catch(NumberFormatException ex){toast("Informe um inteiro de 0 ms ou mais.");return;}
            if(ms<0){toast("Valor nao pode ser negativo.");return;}
            prefs.edit().putInt(ScanAssistState.CLICK_COOLDOWN_MS,ms).apply();
            MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
            if(svc!=null)svc.updateClickCooldownMs(ms);
            timeLabel.setText("Atual: "+ms+" ms");
            toast("Intervalo aplicado: "+ms+" ms");
        });
        LinearLayout appearance=card();
        appearance.addView(label("Aparencia",17,ink,true));
        LinearLayout themes=row();
        for(String[] opt:new String[][]{{"auto","Sistema"},{"dark","Escuro"},{"light","Claro"}}){
            TextView choice=action(opt[1],opt[0].equals(themeChoice),()->chooseTheme(opt[0]));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
            p.leftMargin=dp(4);themes.addView(choice,p);
        }
        space(appearance,10);appearance.addView(themes);
    }
    private void diagnostic(){
        title("Diagnóstico","Copie o último evento.");
        LinearLayout card=card();card.addView(label("Último evento",17,ink,true));
        lastStatus=label(prefs.getString("last_status","Nenhum diagnóstico ainda."),14,muted,false);
        lastStatus.setTextIsSelectable(true);space(card,11);card.addView(lastStatus);
        button(card,"Copiar diagnóstico",false,()->{
            android.content.ClipboardManager m=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(m!=null)m.setPrimaryClip(android.content.ClipData.newPlainText("MLOG Bridge diagnóstico",lastStatus.getText()));
            toast("Diagnóstico copiado. Não inclui códigos.");
        });
        button(card,"Abrir MLOG",true,this::openMlog);
    }
    private void about(){
        String installedVersion="";
        try{installedVersion=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored){installedVersion="3.2.3";}
        title("Sobre","MLOG Scanner Bridge · v"+installedVersion);
        LinearLayout updates=card();updates.addView(label("Atualização",17,ink,true));
        subtitle(updates,"Verifica a última versão assinada no GitHub Releases.");
        githubUpdateStatus=label("Versão instalada: "+installedVersion,13,muted,false);
        space(updates,9);updates.addView(githubUpdateStatus);
        button(updates,"Buscar atualização no GitHub",true,this::checkGitHubUpdate);
        subtitle(updates,"O Android pede confirmação para instalar. Seus dados são mantidos numa atualização compatível.");
        LinearLayout about=card();about.addView(label("Integração local",17,ink,true));
        subtitle(about,"Integração do leitor nativo com o MLOG.");
        LinearLayout control=card();control.addView(label("Permissões",17,ink,true));
        button(control,"Informações do app",false,()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        button(control,"Acessibilidade",false,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        subtitle(control,accessibilityReady()?"Acessibilidade: ativa":"Acessibilidade: verificar ou reativar após atualizar");
        subtitle(control,keyboardSelected()?"Teclado Bridge: selecionado":"Teclado Bridge: conferir seleção no Android");
    }
    private void setUpdateStatus(String text){
        if(githubUpdateStatus!=null) githubUpdateStatus.setText(text);
    }
    private void checkGitHubUpdate(){
        if(githubChecking){ toast("Verificação em andamento.");return; }
        githubChecking=true;
        GitHubReleaseUpdater.checkAndDownload(this,new GitHubReleaseUpdater.Callback(){
            @Override public void status(String text){ setUpdateStatus(text); }
            @Override public void upToDate(String text){ githubChecking=false;setUpdateStatus(text); }
            @Override public void error(String message){
                githubChecking=false;
                setUpdateStatus("Não foi possível atualizar: "+message);
            }
            @Override public void ready(File file,String tag){
                githubChecking=false;
                setUpdateStatus("APK "+tag+" verificado. Instalação pendente.");
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Atualização verificada")
                    .setMessage("A nova versão foi baixada e sua assinatura foi validada. O Android pedirá confirmação para atualizar sem desinstalar o aplicativo.")
                    .setPositiveButton("Instalar",(d,w)->installVerifiedApk(file))
                    .setNegativeButton("Depois",(d,w)->{})
                    .show();
            }
        });
    }
    private void installVerifiedApk(File apk){
        if(apk==null || !apk.isFile()){setUpdateStatus("Arquivo da atualização não encontrado.");return;}
        if(Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()){
            githubPendingInstall=apk;
            new AlertDialog.Builder(this)
                .setTitle("Permissão para instalar atualização")
                .setMessage("O Android precisa autorizar a instalação de atualizações por este aplicativo. Ao retornar, a instalação poderá continuar. Acessibilidade não será ativada automaticamente.")
                .setPositiveButton("Abrir configurações",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+getPackageName()))))
                .setNegativeButton("Cancelar",(d,w)->githubPendingInstall=null)
                .show();
            return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",apk);
            Intent installer=new Intent(Intent.ACTION_VIEW);
            installer.setDataAndType(uri,"application/vnd.android.package-archive");
            installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installer);
            setUpdateStatus("Instalador aberto. Confirme a atualização no Android.");
        }catch(Exception ex){setUpdateStatus("Não foi possível abrir o instalador: "+ex.getMessage());}
    }

    private void check(LinearLayout dest,String text,String key,boolean fallback){
        CheckBox b=new CheckBox(this);b.setButtonTintList(android.content.res.ColorStateList.valueOf(ink));
        b.setText(text);b.setTextColor(ink);b.setTextSize(14);
        b.setPadding(0,dp(5),0,dp(5));b.setChecked(prefs.getBoolean(key,fallback));
        b.setOnCheckedChangeListener((control,on)->{
            prefs.edit().putBoolean(key,on).apply();
            if("fast_ime_mode".equals(key) || "experimental_bridge_ime".equals(key) ||
                "trigger_prefocus".equals(key) || NativeScannerController.LONG_HOLD_PREF.equals(key)){
                if(NativeScannerController.LONG_HOLD_PREF.equals(key) && !on){
                    MlogFocusAccessibilityService active=MlogFocusAccessibilityService.current();
                    if(active!=null)active.stopLongHold();
                }
                MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
                if(svc!=null)svc.refreshEventThrottle();
            }
        });
        dest.addView(b);
    }
    private void openFloatingTools(){
        if(android.os.Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
            toast("Autorize Exibir sobre outros apps e volte para ativar a bolha.");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:"+getPackageName())));
            return;
        }
        prefs.edit().putBoolean("floating_auto_start_v302",true).apply();
        startService(new Intent(this,FloatingToolsService.class));
        toast("Engrenagem flutuante ativada; toque para abrir.");
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
        // Protege botoes e tema contra Enter emitido pelo scanner (keyboard wedge).
        // Esta tela nao tem campo de texto para edicao manual.
        int k=event.getKeyCode();
        if(k==KeyEvent.KEYCODE_ENTER || k==KeyEvent.KEYCODE_NUMPAD_ENTER ||
           k==KeyEvent.KEYCODE_TAB) return true;
        return super.dispatchKeyEvent(event);
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){
        super.onResume();
        if(githubPendingInstall!=null && (Build.VERSION.SDK_INT<26 || getPackageManager().canRequestPackageInstalls())){
            File pending=githubPendingInstall; githubPendingInstall=null;
            handler.post(() -> installVerifiedApk(pending));
        }
        BridgeUpdater.schedule(this);
        new Thread(() -> BridgeUpdater.check(getApplicationContext()), "bridge-update-start").start();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},778);
        IntentFilter f=new IntentFilter();for(String a:ScannerProtocol.ACTIONS)f.addAction(a);
        try{
            if(Build.VERSION.SDK_INT>=33)registerReceiver(scannerTest,f,Context.RECEIVER_EXPORTED);
            else registerReceiver(scannerTest,f);
            receiving=true;
        }catch(RuntimeException ignored){}
        if((Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this)) &&
            prefs.getBoolean("floating_auto_start_v302",true) &&
            !FloatingToolsService.isRunning()){
            startService(new Intent(this,FloatingToolsService.class));
        }
        handler.post(ticker);
        refreshSetupWarning();
        handler.post(this::maybeShowFirstSetupDialog);
    }
    @Override protected void onPause(){
        handler.removeCallbacks(ticker);
        if(receiving)try{unregisterReceiver(scannerTest);}catch(RuntimeException ignored){}
        receiving=false;
        super.onPause();
    }
}
