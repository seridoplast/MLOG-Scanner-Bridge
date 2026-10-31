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
import rikka.shizuku.Shizuku;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodInfo;
import java.util.List;
import java.util.ArrayDeque;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * v1.8. Interface monocromatica, teclado nativo e recuperacao de Enter.
 * O teste exibe leituras somente na tela; o codigo nao vai para preferências.
 */
public final class MainActivity extends Activity {
    private static final class ScanRecord {
        final String code;
        final String format;
        final String time;
        ScanRecord(String code,String format,String time){
            this.code=code;
            this.format=format;
            this.time=time;
        }
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String themeChoice;
    private int page = 0;
    private int bg, card, line, ink, muted, inverse, inkSurface;
    private LinearLayout root, content, footer;
    private TextView preview, lastStatus, barcodeFormatStatus, barcodeArtifactStatus, barcodeFieldsStatus, serviceState, shizukuSettingsStatus, scannerConnectionStatus;
    private LinearLayout recentReadsList;
    private final ArrayDeque<ScanRecord> recentReads = new ArrayDeque<>();
    private static final int MAX_RECENT_READS = 4;
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
    private static final int SHIZUKU_REQUEST=340;
    private final Shizuku.OnRequestPermissionResultListener shizukuPermission=(code,result)->{
        if(code!=SHIZUKU_REQUEST)return;
        handler.post(()->{
            if(result==android.content.pm.PackageManager.PERMISSION_GRANTED) {
                toast("Shizuku autorizado.");
            } else toast("Shizuku nao autorizado. O modo atual foi mantido.");
            if(!isFinishing())displayPage();
        });
    };
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
            recordRecentScan(visibleCode==null || visibleCode.isEmpty()?"Somente caracteres invisíveis":visibleCode,lastSymbology);
            refreshHomeScannerUi();
        }
    };
    private boolean isActiveAppScreen(){return !isFinishing() && !isDestroyed();}
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(serviceState!=null){
                boolean shizuku=prefs.getBoolean(ShizukuScanMode.PREF,false);
                boolean active=MlogFocusAccessibilityService.current()!=null;
                boolean ready=shizukuAuthorized();
                serviceState.setText(!shizuku?"Ative Bipe XU em Ajustes":
                    ready && active?"✓ Scanner pronto":
                    !ready?"⚠ Ative o Shizuku":"⚠ Ative a acessibilidade");
                serviceState.setTextColor(( !shizuku || !ready || !active )?0xffd97706:
                    active?0xff228b58:muted);
            }
            if(prefs.getBoolean(ShizukuScanMode.PREF,false))
                ShizukuHealthMonitor.refresh(MainActivity.this);
            updateShizukuUiStatus();
            refreshSetupWarning();
            refreshHomeScannerUi();
            if(page==2 && lastStatus!=null){
                lastStatus.setText(prefs.getString("last_status","Nenhum diagnostico ainda."));
            }
            handler.postDelayed(this,1000);
        }
    };

    @Override public void onCreate(Bundle saved){
        prefs=getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);
        BridgePreferences.enableOnUpgrade(prefs);
        if(!prefs.getBoolean("auto_bips_optin_v344",false)){
            prefs.edit().putBoolean("auto_bips_optin_v344",true)
                .putBoolean(ScanAssistState.BATCH,false)
                .putBoolean(ScanAssistState.ARMED,false).apply();
        }
        // v3.4.3: legacy Zebra modes removed from the UI. Preserve Shizuku;
        // migrate old Zebra-only selections to Bipe Normal safely.
        if(!prefs.getBoolean("migrated_remove_zebra_v343",false)){
            boolean oldZebra=prefs.getBoolean(ZebraImeMode.PREF,false) ||
                prefs.getBoolean(ZebraProfile0Mode.PREF,false);
            boolean shizukuSelected=prefs.getBoolean(ShizukuScanMode.PREF,false);
            SharedPreferences.Editor migration=prefs.edit()
                .putBoolean("migrated_remove_zebra_v343",true)
                .putBoolean(ZebraImeMode.PREF,false)
                .putBoolean(ZebraProfile0Mode.PREF,false);
            if(oldZebra && !shizukuSelected){
                migration.putBoolean(BridgeEnterPolicy.PREFERENCE,true)
                    .putBoolean(NativeScannerController.PREF,true)
                    .putBoolean("enabled",true);
            }
            migration.apply();
            if(oldZebra && !shizukuSelected)NativeScannerController.configure(this);
        }
        if(!ScanAssistState.AUTO_BIPS_ENABLED){
            prefs.edit().putBoolean(ScanAssistState.ARMED,false)
                .putBoolean(ScanAssistState.BATCH,false).apply();
        }
        // v3.5.9: interface usa apenas Shizuku; codigo do Bipe Normal permanece
        // para compatibilidade, mas nao e selecionavel pela interface.
        if(!prefs.getBoolean("shizuku_only_v359",false)){
            prefs.edit().putBoolean("shizuku_only_v359",true)
                .putBoolean(ShizukuScanMode.PREF,true)
                .putBoolean(BridgeEnterPolicy.PREFERENCE,false)
                .putBoolean("guided_taps",false)
                .putBoolean("layout_fallback",false).apply();
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
        Shizuku.addRequestPermissionResultListener(shizukuPermission);
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

        left.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        ImageView name=new ImageView(this);
        name.setImageResource(darkNow()?R.drawable.brand_name_dark:R.drawable.brand_name_light);
        name.setScaleType(ImageView.ScaleType.FIT_CENTER);
        name.setContentDescription("MLOG Bridge");
        LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(dp(120),dp(52));nl.leftMargin=dp(8);
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
        barcodeFieldsStatus=null;serviceState=null;shizukuSettingsStatus=null;scannerConnectionStatus=null;recentReadsList=null;setupWarningCard=null;setupWarningLabel=null;setupAccessibilityStep=null;setupKeyboardEnabledStep=null;setupKeyboardSelectedStep=null;setupFloatingStep=null;
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
        if(prefs.getBoolean(ShizukuScanMode.PREF,false)) {
            if(!accessibilityReady())return "• Ative a acessibilidade do Bridge para receber o scanner";
            return shizukuAuthorized()?"":"• Inicie e autorize o Shizuku";
        }
        if(ZebraProfile0Mode.isSelected(prefs.getBoolean(ZebraImeMode.PREF,false),
                prefs.getBoolean(ZebraProfile0Mode.PREF,false)))return "";
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
        setupFloatingStep=setupStep("2. Botão flutuante",this::openOverlaySettings);
        refreshSetupWarning();
    }
    private void refreshSetupWarning(){
        if(setupWarningCard==null || setupWarningLabel==null)return;
        boolean access=accessibilityReady();
        boolean floating=floatingReady();
        updateSetupStep(setupAccessibilityStep,access,"Acessibilidade");
        updateSetupStep(setupFloatingStep,floating,"Botão flutuante");
        boolean complete=access && floating;
        setupWarningCard.setVisibility(complete?View.GONE:View.VISIBLE);
        setupWarningLabel.setText("Somente as duas permissões essenciais.");
    }

    private void home(){
        showSetupWarning();
        title("Teste de leitura",null);

        LinearLayout statusCard=card();
        LinearLayout statusRow=row();
        TextView dot=label("●",16,0xff22c55e,true);
        statusRow.addView(dot);
        TextView statusTitle=label("Leitor conectado",16,ink,true);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,-2,1);
        slp.leftMargin=dp(8);
        statusRow.addView(statusTitle,slp);
        TextView model=label(Build.MODEL==null?"Coletor":Build.MODEL,13,muted,true);
        statusRow.addView(model);
        statusCard.addView(statusRow);
        scannerConnectionStatus=label("Verificando leitura...",12,muted,false);
        LinearLayout.LayoutParams csp=new LinearLayout.LayoutParams(-1,-2);
        csp.topMargin=dp(6);
        statusCard.addView(scannerConnectionStatus,csp);

        LinearLayout testCard=card();
        testCard.setPadding(dp(22),dp(24),dp(22),dp(24));
        TextView aim=label("⌖",28,ink,false);
        aim.setGravity(Gravity.CENTER);
        testCard.addView(aim,new LinearLayout.LayoutParams(-1,-2));
        TextView tester=label("Testar leitura",20,ink,true);
        tester.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);
        tlp.topMargin=dp(4);
        testCard.addView(tester,tlp);
        TextView hint=label("Aponte e bipe um código",13,muted,false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp=new LinearLayout.LayoutParams(-1,-2);
        hlp.topMargin=dp(4);
        testCard.addView(hint,hlp);
        preview=label(lastSample==null?"Aguardando...":BarcodeCleaner.clean(lastSample),22,ink,true);
        preview.setGravity(Gravity.CENTER);
        preview.setTextIsSelectable(true);
        preview.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(-1,-2);
        plp.topMargin=dp(18);
        testCard.addView(preview,plp);
        lastStatus=label(scanCount==0?"Nenhuma leitura ainda.":"Leituras: "+scanCount,12,muted,false);
        lastStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-1,-2);
        llp.topMargin=dp(8);
        testCard.addView(lastStatus,llp);
        serviceState=label("Verificando...",12,muted,false);
        serviceState.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams svlp=new LinearLayout.LayoutParams(-1,-2);
        svlp.topMargin=dp(6);
        testCard.addView(serviceState,svlp);

        LinearLayout actions=row();
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,-2);
        alp.topMargin=dp(16);
        testCard.addView(actions,alp);
        TextView openMlogBtn=action("Abrir MLOG",true,this::openMlog);
        TextView toolsBtn=action("Ferramentas",false,this::openFloatingTools);
        LinearLayout.LayoutParams ap1=new LinearLayout.LayoutParams(0,-2,1);
        LinearLayout.LayoutParams ap2=new LinearLayout.LayoutParams(0,-2,1);
        ap2.leftMargin=dp(10);
        actions.addView(openMlogBtn,ap1);
        actions.addView(toolsBtn,ap2);

        LinearLayout recentCard=card();
        recentCard.addView(label("Últimas leituras",16,ink,true));
        recentReadsList=column();
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);
        rlp.topMargin=dp(12);
        recentCard.addView(recentReadsList,rlp);

        refreshHomeScannerUi();
    }
    private void recordRecentScan(String code,String format){
        String stamp=new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        recentReads.addFirst(new ScanRecord(code,format==null?BarcodeFormatLabel.UNKNOWN:format,stamp));
        while(recentReads.size()>MAX_RECENT_READS)recentReads.removeLast();
    }

    private void refreshHomeScannerUi(){
        if(preview!=null){
            String visible=lastSample==null?"Aguardando...":BarcodeCleaner.clean(lastSample);
            preview.setText(visible==null || visible.isEmpty()?"Somente caracteres invisíveis":visible);
        }
        if(lastStatus!=null){
            if(scanCount==0) lastStatus.setText("Nenhuma leitura ainda.");
            else {
                String type=(lastSymbology==null || BarcodeFormatLabel.UNKNOWN.equals(lastSymbology))?"Leitura recebida":lastSymbology;
                lastStatus.setText(type+" · "+scanCount+(scanCount==1?" leitura":" leituras"));
            }
        }
        if(scannerConnectionStatus!=null){
            boolean ready=prefs!=null && prefs.getBoolean(ShizukuScanMode.PREF,false) && accessibilityReady();
            scannerConnectionStatus.setText(ready?"Pronto para captar novos bipes":"Verifique Shizuku e acessibilidade");
            scannerConnectionStatus.setTextColor(ready?0xff22c55e:0xffd97706);
        }
        if(recentReadsList!=null){
            recentReadsList.removeAllViews();
            if(recentReads.isEmpty()){
                TextView empty=label("Nenhuma leitura ainda.",13,muted,false);
                recentReadsList.addView(empty);
            } else {
                int i=0;
                for(ScanRecord item:recentReads){
                    if(i>0){
                        View divider=new View(this);
                        divider.setBackgroundColor(line);
                        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(1));
                        dlp.topMargin=dp(10);
                        dlp.bottomMargin=dp(10);
                        recentReadsList.addView(divider,dlp);
                    }
                    LinearLayout lineItem=column();
                    LinearLayout head=row();
                    TextView code=label(item.code,18,ink,true);
                    code.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
                    head.addView(code,new LinearLayout.LayoutParams(0,-2,1));
                    head.addView(label(item.time,12,muted,false));
                    lineItem.addView(head);
                    TextView fmt=label(item.format==null?BarcodeFormatLabel.UNKNOWN:item.format,12,0xff22c55e,false);
                    LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,-2);
                    flp.topMargin=dp(4);
                    lineItem.addView(fmt,flp);
                    recentReadsList.addView(lineItem);
                    i++;
                }
            }
        }
    }

    /** Os tres caminhos sao mutuamente exclusivos:
     * (A) Antigo = broadcast + acessibilidade; (B) Zebra Profile0 = CMX keyboard;
     * (C) Zebra IME = broadcast + teclado Bridge envia KeyEvents ao editor MLOG.
     * IMPORTANTE: (C) nao injeta teclas globais e exige InputConnection focada.
     */
    private boolean shizukuAuthorized(){
        return ShizukuHealthMonitor.ready();
    }
    private void openShizuku(){
        if(!ShizukuHealthMonitor.openShizuku(this))
            toast("Shizuku não encontrado. Instale o aplicativo Shizuku.");
    }
    private void updateShizukuUiStatus(){
        if(shizukuSettingsStatus==null)return;
        boolean ready=shizukuAuthorized();
        boolean accessible=accessibilityReady();
        shizukuSettingsStatus.setText(ready && accessible?
            "✓ Pronto":
            !ready?"⚠ Inicie o Shizuku":
            "⚠ Ative a acessibilidade");
        shizukuSettingsStatus.setTextColor(ready && accessible?0xff228b58:0xffd97706);
    }
    private void selectShizukuMode(){
        if(!accessibilityReady()){
            new AlertDialog.Builder(this).setTitle("Acessibilidade necessaria")
                .setMessage("O Bridge recebe os broadcasts do coletor pelo servico de acessibilidade. " +
                    "Neste modo ele NAO clica, abre teclado nem digita no MLOG. Habilite o servico.")
                .setPositiveButton("Abrir configuracoes",(d,w)->openAccessibilitySettings())
                .setNegativeButton("Cancelar",null).show();
            return;
        }
        if(!shizukuAuthorized()){
            try {
                if(!Shizuku.pingBinder()){
                    openShizuku();toast("No Shizuku, toque em Iniciar e volte ao Bridge.");return;
                }
                if(Shizuku.shouldShowRequestPermissionRationale()){
                    toast("Permissao negada. Autorize o Bridge dentro do Shizuku.");return;
                }
                Shizuku.requestPermission(SHIZUKU_REQUEST);
            }catch(RuntimeException e){toast("Nao foi possivel acessar o Shizuku.");}
            return;
        }
        if(!NativeScannerController.configure(this)){
            toast("Falha ao configurar broadcast exclusivo do CMX; modo preservado.");return;
        }
        prefs.edit().putBoolean(ShizukuScanMode.PREF,true)
            .putBoolean(ZebraImeMode.PREF,false)
            .putBoolean(ZebraProfile0Mode.PREF,false)
            .putBoolean(NativeScannerController.PREF,true)
            .putBoolean(DirectScanPolicy.PREF,true)
            .putBoolean(BridgeEnterPolicy.PREFERENCE,false)
            .putBoolean(ZebraImeMode.AUTO_FOCUS_PREF,false)
            .putBoolean("trigger_prefocus",false)
            .putBoolean(NativeScannerController.LONG_HOLD_PREF,false)
            .putBoolean("guided_taps",false)
            .putBoolean("layout_fallback",false)
            .putBoolean("enabled",true).apply();
        BridgeScanInputMethodService.resetForModeChange();
        MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
        if(svc!=null){svc.refreshEventThrottle();svc.prepareShizuku();}
        displayPage();
        ShizukuHealthMonitor.refresh(this);
        ensureShizukuNotificationPermission();
        toast("Bipe Shizuku ativo. Teste uma leitura no MLOG.");
    }
    private void selectReadMode(){
        if(!NativeScannerController.isInstalled(this)){
            toast("Servico nativo do leitor nao encontrado; modo preservado.");return;
        }
        if(!NativeScannerController.configure(this)){
            toast("Nao foi possivel solicitar a saida por broadcast.");return;
        }
        prefs.edit().putBoolean(ShizukuScanMode.PREF,false)
            .putBoolean(ZebraImeMode.PREF,false)
            .putBoolean(ZebraProfile0Mode.PREF,false)
            .putBoolean(NativeScannerController.PREF,true)
            .putBoolean(DirectScanPolicy.PREF,true)
            .putBoolean(BridgeEnterPolicy.PREFERENCE,true)
            .putBoolean("enabled",true)
            .putBoolean("legacy_options_expanded_v327",true).apply();
        BridgeScanInputMethodService.resetForModeChange();
        MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
        if(svc!=null)svc.refreshEventThrottle();
        displayPage();
        ShizukuHealthMonitor.refresh(this); // removes a pending Shizuku alert
        toast("Bipe Normal selecionado; modo broadcast solicitado.");
    }
    private void settings(){
        title("Ajustes",null);
        LinearLayout mode=card();
        mode.addView(label("Bipe XU · Shizuku",18,ink,true));
        shizukuSettingsStatus=label("Verificando...",13,muted,true);
        mode.addView(shizukuSettingsStatus);
        updateShizukuUiStatus();
        button(mode,"Ativar / reconectar Shizuku",true,this::selectShizukuMode);
        button(mode,"Acessibilidade",false,this::openAccessibilitySettings);
        check(mode,"Recuperar leitura (Volume)","camera_auto_recovery_v348",true);
        button(mode,"Reconfigurar scanner",false,()->
            toast(NativeScannerController.configure(this)?"Scanner configurado.":"Falha no scanner."));
        LinearLayout appearance=card();
        appearance.addView(label("Aparência",17,ink,true));
        LinearLayout themes=row();
        for(String[] opt:new String[][]{{"auto","Sistema"},{"dark","Escuro"},{"light","Claro"}}){
            TextView choice=action(opt[1],opt[0].equals(themeChoice),()->chooseTheme(opt[0]));
            LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(0,-2,1);
            c.leftMargin=dp(4);themes.addView(choice,c);
        }
        space(appearance,10);appearance.addView(themes);
        LinearLayout automatic=card();
        automatic.addView(label("Bipes automáticos",17,ink,true));
        boolean enabled=ScanAssistState.isBatch(this);
        button(automatic,enabled?"Desativar":"Ativar com senha",enabled,()->{
            if(ScanAssistState.isBatch(this)){
                ScanAssistState.disable(this);displayPage();toast("Bipes automáticos desativados.");
            } else requireAutoBipsPassword();
        });
    }
    private void requireAutoBipsPassword(){
        final EditText password=new EditText(this);
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int padding=dp(18); password.setPadding(padding,padding,padding,padding);
        new AlertDialog.Builder(this)
            .setTitle("Liberar bipes automáticos")
            .setMessage("Função experimental: confira as quantidades lançadas no MLOG.")
            .setView(password)
            .setPositiveButton("Ativar",(dialog,which)->{
                if(AutoBipsAccess.valid(password.getText().toString())){
                    ScanAssistState.prefs(this).edit()
                        .putBoolean(ScanAssistState.BATCH,true)
                        .putBoolean(ScanAssistState.ARMED,false).apply();
                    displayPage();
                    toast("Bipes automáticos liberados. Configure no botão flutuante.");
                }else toast("Senha incorreta. Recurso permanece desativado.");
            })
            .setNegativeButton("Cancelar",null).show();
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
        catch(Exception ignored){installedVersion="?";}
        title("Sobre · v"+installedVersion,null);
        LinearLayout updates=card();updates.addView(label("Atualizações",17,ink,true));
        check(updates,"Buscar automaticamente",BridgeUpdater.PREF_ENABLED,true);
        check(updates,"Instalar quando inativo",BridgeUpdater.PREF_INSTALL,true);
        githubUpdateStatus=label(prefs.getString(BridgeUpdater.LAST_STATUS,"Aguardando verificação."),13,muted,false);
        space(updates,9);updates.addView(githubUpdateStatus);
        button(updates,"Buscar versões",true,this::checkGitHubUpdate);
        button(updates,"Instalar APK baixado",false,this::installCachedShizuku);
        LinearLayout control=card();control.addView(label("Permissões",17,ink,true));
        button(control,"Acessibilidade",false,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(control,"Informações do app",false,()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:"+getPackageName()))));
    }
    private void setUpdateStatus(String text){
        if(githubUpdateStatus!=null) githubUpdateStatus.setText(text);
    }
    private void requireDebugPassword(Runnable action){
        if(DebugUpdateAccess.allowed()){action.run();return;}
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int padding=dp(18);
        field.setPadding(padding,padding,padding,padding);
        new AlertDialog.Builder(this)
            .setTitle("Autorizar pre-release")
            .setMessage("Somente instalacao manual. Senha nao armazenada.")
            .setView(field)
            .setPositiveButton("Autorizar",(d,w)->{
                boolean ok=DebugUpdateAccess.unlock(field.getText().toString());
                field.getText().clear();
                if(ok)action.run();
                else {setUpdateStatus("Senha incorreta.");toast("Senha incorreta.");}
            })
            .setNegativeButton("Cancelar",null).show();
    }

    private void installCachedShizuku(){
        try {
            GitHubReleaseUpdater.Update update=GitHubReleaseUpdater.cachedVerified(this);
            if(update==null){setUpdateStatus("Nao ha APK novo no armazenamento.");return;}
            if(update.debug && !DebugUpdateAccess.allowed()){
                requireDebugPassword(this::installCachedShizuku);return;
            }
        } catch(Exception ex) {setUpdateStatus("Falha ao validar APK: "+ex.getMessage());return;}
        installCachedShizukuAuthorized();
    }

    private void installCachedShizukuAuthorized(){

        if(!ShizukuApkInstaller.ready()){
            setUpdateStatus("Shizuku indisponivel. Voce pode instalar pelo Android.");
            new AlertDialog.Builder(this)
                .setTitle("Shizuku indisponivel")
                .setMessage("O Shizuku nao esta ativo ou autorizado. Deseja usar o instalador normal do Android?")
                .setPositiveButton("Instalar pelo Android",(d,w)->installCachedViaAndroid())
                .setNegativeButton("Cancelar",null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Instalar pelo Shizuku")
            .setMessage("A instalacao pode reiniciar o Bridge. Feche a atividade de Separacao antes de continuar.")
            .setPositiveButton("Instalar",(d,w)->{
                setUpdateStatus("Validando APK e preparando sessao de instalacao...");
                ShizukuApkInstaller.installAsync(this,true,(ok,msg)->{
                    setUpdateStatus(msg);
                    if(!ok && !isFinishing() && !isDestroyed()){
                        new AlertDialog.Builder(this)
                            .setTitle("Instalacao Shizuku interrompida")
                            .setMessage(msg+"\n\nDeseja tentar pelo instalador normal do Android? A instalacao exigira sua confirmacao.")
                            .setPositiveButton("Instalar pelo Android",(dialog,which)->installCachedViaAndroid())
                            .setNegativeButton("Depois",null).show();
                    }
                });
            })
            .setNegativeButton("Cancelar",null).show();
    }
    private void checkGitHubUpdate(){
        if(githubChecking){toast("Busca em andamento.");return;}
        githubChecking=true;
        setUpdateStatus("Consultando versoes...");
        GitHubReleaseUpdater.listForManualSelection(new GitHubReleaseUpdater.ReleasesCallback(){
            @Override public void error(String message){
                githubChecking=false;setUpdateStatus("Falha: "+message);
            }
            @Override public void loaded(java.util.List<GitHubReleaseUpdater.ReleaseInfo> items){
                githubChecking=false;
                if(items.isEmpty()){setUpdateStatus("Nenhuma Release publicada.");return;}
                long current=0L;
                try {
                    android.content.pm.PackageInfo pkg=getPackageManager().getPackageInfo(getPackageName(),0);
                    current=Build.VERSION.SDK_INT>=28?pkg.getLongVersionCode():pkg.versionCode;
                } catch(Exception ignored) { }
                final long installed=current;
                String[] labels=new String[items.size()];
                for(int i=0;i<items.size();i++){
                    GitHubReleaseUpdater.ReleaseInfo item=items.get(i);
                    labels[i]=item.tag+(item.prerelease?"  ·  TESTE 🔒":"  ·  ESTÁVEL")
                        +(item.versionCode<=installed?"  ·  instalada/anterior":"");
                }
                setUpdateStatus(items.size()+" versoes encontradas.");
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Versões disponíveis")
                    .setItems(labels,(dialog,index)->{
                        GitHubReleaseUpdater.ReleaseInfo choice=items.get(index);
                        if(choice.versionCode<=installed){
                            setUpdateStatus("Versao instalada ou anterior; downgrade bloqueado.");return;
                        }
                        if(choice.prerelease)requireDebugPassword(()->downloadManual(choice));
                        else downloadManual(choice);
                    })
                    .setNegativeButton("Fechar",null).show();
            }
        });
    }

    private void downloadManual(GitHubReleaseUpdater.ReleaseInfo choice){
        if(githubChecking){toast("Download em andamento.");return;}
        if(choice.prerelease && !DebugUpdateAccess.allowed()){
            requireDebugPassword(()->downloadManual(choice));return;
        }
        githubChecking=true;
        GitHubReleaseUpdater.downloadSelected(choice,new GitHubReleaseUpdater.Callback(){
            @Override public void status(String message){setUpdateStatus(message);}
            @Override public void upToDate(String message){githubChecking=false;setUpdateStatus(message);}
            @Override public void error(String message){githubChecking=false;setUpdateStatus("Falha: "+message);}
            @Override public void ready(File file,String tag){
                githubChecking=false;
                setUpdateStatus(tag+(choice.prerelease?" (teste)":" (estavel)")+" pronto.");
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Instalar "+tag+(choice.prerelease?" (TESTE)":""))
                    .setMessage("APK assinado e verificado.")
                    .setPositiveButton("Shizuku",(d,w)->installCachedShizuku())
                    .setNeutralButton("Android",(d,w)->installVerifiedApk(file))
                    .setNegativeButton("Depois",null).show();
            }
        },getApplicationContext());
    }

    private void installCachedViaAndroid(){
        try {
            GitHubReleaseUpdater.Update checked=GitHubReleaseUpdater.cachedVerified(this);
            if(checked==null){setUpdateStatus("Nao ha APK atualizado e validado em cache.");return;}
            if(checked.debug && !DebugUpdateAccess.allowed()){
                requireDebugPassword(this::installCachedViaAndroid);return;
            }
            installVerifiedApk(checked.apk);
        } catch(Exception ex) {setUpdateStatus("Falha ao verificar APK para Android: "+ex.getMessage());}
    }
    private void installVerifiedApk(File apk){
        try {
            GitHubReleaseUpdater.Update verified=GitHubReleaseUpdater.cachedVerified(this);
            if(verified==null || apk==null || !verified.apk.equals(apk)) {
                setUpdateStatus("Instalacao cancelada: APK nao verificado no cache.");return;
            }
            if(verified.debug && !DebugUpdateAccess.allowed()){
                requireDebugPassword(()->installVerifiedApk(apk));return;
            }
        } catch(Exception ex) {setUpdateStatus("APK invalido: "+ex.getMessage());return;}
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
            if(BridgeUpdater.PREF_ENABLED.equals(key) || BridgeUpdater.PREF_INSTALL.equals(key)){
                BridgeUpdater.schedule(this);
                if(on && BridgeUpdater.PREF_ENABLED.equals(key))BridgeUpdater.checkSoon(this);
            }
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
    private void ensureShizukuNotificationPermission(){
        // Android 13+: request only once, only in the Shizuku scan mode.
        if(Build.VERSION.SDK_INT>=33 && prefs.getBoolean(ShizukuScanMode.PREF,false)
            && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                !=android.content.pm.PackageManager.PERMISSION_GRANTED
            && !prefs.getBoolean("shizuku_notice_permission_asked_v346",false)){
            prefs.edit().putBoolean("shizuku_notice_permission_asked_v346",true).apply();
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},346);
        }
    }

    @Override protected void onResume(){
        super.onResume();
        if(githubPendingInstall!=null && (Build.VERSION.SDK_INT<26 || getPackageManager().canRequestPackageInstalls())){
            File pending=githubPendingInstall; githubPendingInstall=null;
            handler.post(() -> installVerifiedApk(pending));
        }
        BridgeUpdater.schedule(this);
        // Reaplicar ao abrir o Bridge, pois o servico CMX pode reiniciar
        // com a saida padrao apos atualizacao/reboot do coletor.
        if(prefs.getBoolean(ShizukuScanMode.PREF,false))
            NativeScannerController.configure(this);
        else if(ZebraProfile0Mode.isSelected(prefs.getBoolean(ZebraImeMode.PREF,false),
                prefs.getBoolean(ZebraProfile0Mode.PREF,false)))
            NativeScannerController.configureProfile0Keyboard(this);
        BridgeUpdater.checkSoon(this);
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
        ShizukuHealthMonitor.refresh(this);
        ensureShizukuNotificationPermission();
        refreshSetupWarning();
    }
    @Override protected void onDestroy(){
        Shizuku.removeRequestPermissionResultListener(shizukuPermission);
        super.onDestroy();
    }
    @Override protected void onPause(){
        handler.removeCallbacks(ticker);
        if(receiving)try{unregisterReceiver(scannerTest);}catch(RuntimeException ignored){}
        receiving=false;
        super.onPause();
    }
}
