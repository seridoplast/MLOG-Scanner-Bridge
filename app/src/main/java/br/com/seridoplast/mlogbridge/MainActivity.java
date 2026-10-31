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
    private int primary, primarySoft, surfaceAlt, success, amber, heroBase;
    private LinearLayout root, content, footer;
    private TextView preview, lastStatus, barcodeFormatStatus, barcodeArtifactStatus, barcodeFieldsStatus, serviceState, shizukuSettingsStatus, scannerConnectionStatus;
    private TextView scannerConnectionTitle, scannerConnectionDot, scanHint, historyCount;
    private LinearLayout recentReadsList;
    private String lastRenderedRead="";
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
                    if(!recentReads.isEmpty()){
                        ScanRecord first=recentReads.removeFirst();
                        recentReads.addFirst(new ScanRecord(first.code,maybeType,first.time));
                        lastRenderedRead="";
                    }
                    refreshHomeScannerUi();
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
                serviceState.setText(!shizuku?"Ative BipXuco em Ajustes":
                    ready && active?"✓ BipXuco pronto":
                    !ready?"⚠ Ative o Shizuku":"⚠ Ative a acessibilidade");
                serviceState.setTextColor(( !shizuku || !ready || !active )?0xffd97706:
                    active?0xff228b58:muted);
            }
            if(prefs.getBoolean(ShizukuScanMode.PREF,false))
                ShizukuHealthMonitor.refresh(MainActivity.this);
            updateShizukuUiStatus();
            refreshSetupWarning();
            refreshScannerStatus();
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
        bg=dark?0xff0c1422:0xfff3f6fc;
        card=dark?0xff172337:0xffffffff;
        line=dark?0xff29394f:0xffe4ebf5;
        ink=dark?0xfff4f7fd:0xff17233a;
        muted=dark?0xffa1b0c6:0xff75839a;
        inverse=0xffffffff;
        inkSurface=ink;
        primary=dark?0xff6b9aff:0xff2864e8;
        primarySoft=dark?0xff243c67:0xffe9f0ff;
        surfaceAlt=dark?0xff1e2c42:0xfff5f8fd;
        success=dark?0xff4bd79c:0xff149463;
        amber=dark?0xffffc56a:0xffbc761a;
        heroBase=dark?0xff132a58:0xff18346c;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);
        return d;
    }
    private GradientDrawable gradient(int top,int bottom,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});
        d.setCornerRadius(dp(radius));return d;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setIncludeFontPadding(true);return t;
    }
    private ImageView icon(int res,int tint,int size){
        ImageView view=new ImageView(this);view.setImageResource(res);
        view.setColorFilter(tint,android.graphics.PorterDuff.Mode.SRC_IN);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));
        return view;
    }
    private TextView capsule(String value,int color,int back){
        TextView v=label(value,11,color,true);
        v.setGravity(Gravity.CENTER);v.setPadding(dp(11),dp(6),dp(11),dp(6));
        v.setBackground(shape(back,0,24));return v;
    }
    private void space(LinearLayout dest,int px){View v=new View(this);dest.addView(v,new LinearLayout.LayoutParams(1,dp(px)));}
    private LinearLayout card(){
        LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));
        c.setBackground(shape(card,line,22));c.setElevation(dp(2));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.bottomMargin=dp(14);content.addView(c,cp);return c;
    }
    private void subtitle(LinearLayout c,String value){
        TextView t=label(value,12,muted,false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);c.addView(t,lp);
    }
    private TextView action(String name,boolean filled,Runnable callback){
        TextView b=label(name,14,filled?0xffffffff:primary,true);
        b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(14),dp(12),dp(14));
        b.setBackground(filled?gradient(0xff3a7cfa,0xff2457d0,15):shape(primarySoft,0,15));
        b.setFocusable(false);b.setFocusableInTouchMode(false);
        b.setOnClickListener(w->callback.run());b.setClickable(true);
        return b;
    }
    private void button(LinearLayout dest,String text,boolean filled,Runnable callback){
        TextView b=action(text,filled,callback);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(10);
        dest.addView(b,params);
    }
    private void displayPage(){
        root=column();root.setBackgroundColor(bg);setContentView(root);
        LinearLayout head=row();head.setPadding(dp(18),dp(13),dp(18),dp(13));
        LinearLayout left=row();
        ImageView logo=new ImageView(this);
        logo.setImageResource(darkNow()?R.drawable.brand_home_dark:R.drawable.brand_home_light);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        left.addView(logo,new LinearLayout.LayoutParams(dp(43),dp(43)));
        LinearLayout branding=column();
        ImageView name=new ImageView(this);
        name.setImageResource(darkNow()?R.drawable.brand_name_dark:R.drawable.brand_name_light);
        name.setScaleType(ImageView.ScaleType.FIT_START);
        name.setContentDescription("Seridóplast MLOG Bridge");
        branding.addView(name,new LinearLayout.LayoutParams(dp(124),dp(29)));
        branding.addView(label("SCANNER BRIDGE",9,muted,true));
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2);blp.leftMargin=dp(8);
        left.addView(branding,blp);
        head.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=capsule("v3.6.2",primary,primarySoft);
        LinearLayout.LayoutParams vlp=new LinearLayout.LayoutParams(-2,-2);vlp.rightMargin=dp(8);
        head.addView(version,vlp);
        TextView moon=label(darkNow()?"☀":"☾",23,ink,false);
        moon.setGravity(Gravity.CENTER);moon.setBackground(shape(card,line,13));
        moon.setContentDescription("Alternar tema com toque na tela");
        moon.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_UP){
                long now=android.os.SystemClock.elapsedRealtime();
                if(now-lastThemeTapAt>650){lastThemeTapAt=now;nextTheme();}
                return true;
            }
            return true;
        });
        head.addView(moon,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(head);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setClipToPadding(false);
        sc.setVerticalScrollBarEnabled(false);
        content=column();content.setPadding(dp(17),dp(8),dp(17),dp(11));sc.addView(content);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        footer=row();footer.setPadding(dp(12),dp(9),dp(12),dp(12));
        footer.setBackground(shape(card,0,22));footer.setElevation(dp(10));
        String[] names={"Início","Ajustes","Diagnóstico","Sobre"};
        int[] icons={R.drawable.ui_scan,R.drawable.ui_sliders,R.drawable.ui_pulse,R.drawable.ui_info};
        for(int i=0;i<names.length;i++){
            final int target=i;
            LinearLayout tab=column();tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(3),dp(10),dp(3),dp(9));
            boolean selected=page==i;
            tab.setBackground(shape(selected?primarySoft:card,0,15));
            ImageView tabIcon=icon(icons[i],selected?primary:muted,21);
            tab.addView(tabIcon);
            TextView text=label(names[i],10,selected?primary:muted,selected);
            text.setGravity(Gravity.CENTER);text.setSingleLine(true);
            LinearLayout.LayoutParams tip=new LinearLayout.LayoutParams(-1,-2);tip.topMargin=dp(4);
            tab.addView(text,tip);
            tab.setClickable(true);tab.setFocusable(false);
            tab.setContentDescription("Abrir "+names[i]);
            tab.setOnClickListener(v->show(target));
            LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,-2,1);
            if(i>0)tlp.leftMargin=dp(4);
            footer.addView(tab,tlp);
        }
        root.addView(footer);
        githubUpdateStatus=null;preview=null;lastStatus=null;barcodeFormatStatus=null;barcodeArtifactStatus=null;
        barcodeFieldsStatus=null;serviceState=null;shizukuSettingsStatus=null;scannerConnectionStatus=null;
        scannerConnectionTitle=null;scannerConnectionDot=null;scanHint=null;historyCount=null;
        recentReadsList=null;lastRenderedRead="";setupWarningCard=null;setupWarningLabel=null;
        setupAccessibilityStep=null;setupKeyboardEnabledStep=null;setupKeyboardSelectedStep=null;setupFloatingStep=null;
        if(page==0)home();else if(page==1)settings();else if(page==2)diagnostic();else about();
    }
    private void show(int p){page=p;displayPage();}
    private void title(String s,String explanation){
        TextView eyebrow=label("MLOG  /  "+(page==0?"SCANNER":page==1?"PREFERÊNCIAS":page==2?"SUPORTE":"SISTEMA"),10,primary,true);
        eyebrow.setLetterSpacing(0.1f);content.addView(eyebrow);
        TextView t=label(s,27,ink,true);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.topMargin=dp(6);
        content.addView(t,tlp);
        if(explanation!=null && !explanation.isEmpty()){
            TextView e=label(explanation,12,muted,false);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.topMargin=dp(5);lp.bottomMargin=dp(15);content.addView(e,lp);
        }else space(content,16);
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
        TextView t=label(title,13,ink,true);
        t.setPadding(dp(12),dp(11),dp(12),dp(11));
        t.setBackground(shape(surfaceAlt,line,12));
        t.setOnClickListener(v->open.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(7);setupWarningCard.addView(t,lp);return t;
    }
    private void updateSetupStep(TextView t,boolean ready,String name){
        if(t==null)return;
        t.setText((ready?"✓  ":"○  ")+name+(ready?"  ·  Ativado":"  ·  Configurar"));
        t.setTextColor(ready?success:ink);
        t.setBackground(shape(ready?primarySoft:surfaceAlt,ready?0:line,12));
        t.setContentDescription(name+(ready?", concluído":", pendente"));
    }
    private void showSetupWarning(){
        setupWarningCard=card();
        setupWarningCard.setBackground(shape(card,0,20));
        LinearLayout heading=row();
        heading.addView(icon(R.drawable.ui_alert,amber,22));
        TextView headline=label("Configuração pendente",15,ink,true);
        LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,-2);h.leftMargin=dp(9);
        heading.addView(headline,h);setupWarningCard.addView(heading);
        setupWarningLabel=label("Conclua os passos para usar o leitor.",12,muted,false);
        space(setupWarningCard,5);setupWarningCard.addView(setupWarningLabel);
        setupAccessibilityStep=setupStep("Acessibilidade",this::openAccessibilitySettings);
        setupFloatingStep=setupStep("Botão flutuante",this::openOverlaySettings);
        refreshSetupWarning();
    }
    private void refreshSetupWarning(){
        if(setupWarningCard==null || setupWarningLabel==null)return;
        boolean access=accessibilityReady();boolean floating=floatingReady();
        updateSetupStep(setupAccessibilityStep,access,"Acessibilidade");
        updateSetupStep(setupFloatingStep,floating,"Botão flutuante");
        setupWarningCard.setVisibility(access && floating?View.GONE:View.VISIBLE);
    }

    private void home(){
        title("Central de leitura",null);
        showSetupWarning();
        LinearLayout topCard=card();
        LinearLayout top=row();
        scannerConnectionDot=label("●",16,success,true);
        top.addView(scannerConnectionDot);
        LinearLayout topText=column();
        scannerConnectionTitle=label("Leitor detectado",16,ink,true);
        topText.addView(scannerConnectionTitle);
        scannerConnectionStatus=label("Verificando...",12,muted,false);
        LinearLayout.LayoutParams ssp=new LinearLayout.LayoutParams(-1,-2);ssp.topMargin=dp(3);
        topText.addView(scannerConnectionStatus,ssp);
        LinearLayout.LayoutParams ltp=new LinearLayout.LayoutParams(0,-2,1);ltp.leftMargin=dp(10);
        top.addView(topText,ltp);
        TextView model=capsule(Build.MODEL==null?"CMX":Build.MODEL,primary,primarySoft);
        model.setMaxWidth(dp(95));model.setSingleLine(true);
        model.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(model);
        topCard.addView(top);

        LinearLayout testCard=column();
        testCard.setPadding(dp(20),dp(26),dp(20),dp(20));
        testCard.setBackground(gradient(0xff17376b,0xff101e40,26));testCard.setElevation(dp(5));
        LinearLayout.LayoutParams tc=new LinearLayout.LayoutParams(-1,-2);tc.bottomMargin=dp(15);
        content.addView(testCard,tc);
        LinearLayout iconBar=row();iconBar.setGravity(Gravity.CENTER);
        LinearLayout circle=row();circle.setGravity(Gravity.CENTER);
        circle.setBackground(shape(0xff294c85,0,18));
        circle.addView(icon(R.drawable.ui_scan,0xffcfe2ff,31));
        iconBar.addView(circle,new LinearLayout.LayoutParams(dp(66),dp(66)));
        testCard.addView(iconBar);
        TextView heroTitle=label("Testar leitura",21,0xffffffff,true);
        heroTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(-1,-2);htp.topMargin=dp(12);
        testCard.addView(heroTitle,htp);
        scanHint=label("Aponte e bipe um código",13,0xffbdd0ef,false);
        scanHint.setGravity(Gravity.CENTER);space(testCard,4);testCard.addView(scanHint);
        LinearLayout codePlate=column();
        codePlate.setPadding(dp(13),dp(14),dp(13),dp(14));
        codePlate.setBackground(shape(0xff233d69,0xff315282,16));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(20);
        testCard.addView(codePlate,cp);
        preview=label("Aguardando...",20,0xffffffff,true);
        preview.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        preview.setGravity(Gravity.CENTER);preview.setTextIsSelectable(true);
        codePlate.addView(preview);
        lastStatus=label("Nenhuma leitura ainda",12,0xffbdd0ef,false);
        lastStatus.setGravity(Gravity.CENTER);space(codePlate,5);codePlate.addView(lastStatus);
        serviceState=label("Verificando BipXuco...",12,0xffb9d1fc,false);
        serviceState.setGravity(Gravity.CENTER);space(testCard,12);testCard.addView(serviceState);
        LinearLayout actions=row();actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,-2);alp.topMargin=dp(17);
        testCard.addView(actions,alp);
        TextView open=action("Abrir MLOG",true,this::openMlog);
        open.setBackground(shape(0xffffffff,0,14));open.setTextColor(0xff1a4692);
        TextView floating=action("Ferramentas",false,this::openFloatingTools);
        floating.setBackground(shape(0xff284b84,0,14));floating.setTextColor(0xffffffff);
        LinearLayout.LayoutParams oa=new LinearLayout.LayoutParams(0,-2,1);
        LinearLayout.LayoutParams fa=new LinearLayout.LayoutParams(0,-2,1);fa.leftMargin=dp(8);
        actions.addView(open,oa);actions.addView(floating,fa);

        LinearLayout recentCard=card();
        LinearLayout recentHead=row();
        recentHead.addView(icon(R.drawable.ui_history,primary,20));
        TextView recentTitle=label("Últimas leituras",17,ink,true);
        LinearLayout.LayoutParams rtp=new LinearLayout.LayoutParams(0,-2,1);rtp.leftMargin=dp(9);
        recentHead.addView(recentTitle,rtp);
        historyCount=capsule("0/4",primary,primarySoft);
        recentHead.addView(historyCount);
        recentCard.addView(recentHead);
        recentReadsList=column();
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);rlp.topMargin=dp(15);
        recentCard.addView(recentReadsList,rlp);
        refreshHomeScannerUi();refreshScannerStatus();
    }
    private void recordRecentScan(String code,String format){
        String stamp=new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        recentReads.addFirst(new ScanRecord(code,format==null?BarcodeFormatLabel.UNKNOWN:format,stamp));
        while(recentReads.size()>MAX_RECENT_READS)recentReads.removeLast();
    }

    private void refreshScannerStatus(){
        if(scannerConnectionStatus==null && scannerConnectionTitle==null)return;
        boolean available=NativeScannerController.isInstalled(this);
        boolean ready=available && prefs.getBoolean(ShizukuScanMode.PREF,false)
            && shizukuAuthorized() && accessibilityReady();
        if(scannerConnectionTitle!=null)scannerConnectionTitle.setText(available?"Leitor disponível":"Leitor não detectado");
        if(scannerConnectionDot!=null)scannerConnectionDot.setTextColor(ready?success:amber);
        if(scannerConnectionStatus!=null){
            scannerConnectionStatus.setText(ready?"BipXuco pronto para testar":
                !available?"Serviço CMX não localizado":"Confira Shizuku e acessibilidade");
            scannerConnectionStatus.setTextColor(ready?success:amber);
        }
    }
    private void refreshHomeScannerUi(){
        if(page!=0)return;
        if(preview!=null){
            String visible=lastSample==null?"Aguardando...":BarcodeCleaner.clean(lastSample);
            String result=visible==null || visible.isEmpty()?"Somente caracteres invisíveis":visible;
            if(!result.contentEquals(preview.getText()))preview.setText(result);
        }
        if(lastStatus!=null){
            String result=scanCount==0?"Nenhuma leitura ainda":
                (lastSymbology==null || BarcodeFormatLabel.UNKNOWN.equals(lastSymbology)?"Leitura recebida":lastSymbology)+
                "  ·  "+scanCount+(scanCount==1?" leitura":" leituras");
            if(!result.contentEquals(lastStatus.getText()))lastStatus.setText(result);
        }
        if(scanHint!=null)scanHint.setText(scanCount==0?"Aponte e bipe um código":"Leitura recebida com sucesso");
        if(historyCount!=null)historyCount.setText(recentReads.size()+"/4");
        if(recentReadsList==null)return;
        // Recria apenas quando muda a leitura, nao a cada tick do relogio.
        String signature=scanCount+":"+recentReads.size();
        if(signature.equals(lastRenderedRead))return;
        lastRenderedRead=signature;
        recentReadsList.removeAllViews();
        if(recentReads.isEmpty()){
            TextView empty=label("Os últimos quatro bipes aparecerão aqui.",13,muted,false);
            empty.setPadding(dp(3),dp(12),dp(3),dp(12));recentReadsList.addView(empty);
            return;
        }
        int i=0;
        for(ScanRecord item:recentReads){
            if(i>0){
                View divider=new View(this);divider.setBackgroundColor(line);
                LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(1));
                dlp.topMargin=dp(10);dlp.bottomMargin=dp(10);
                recentReadsList.addView(divider,dlp);
            }
            LinearLayout itemRow=row();
            TextView index=label(String.format(Locale.getDefault(),"%02d",i+1),11,primary,true);
            index.setGravity(Gravity.CENTER);
            index.setBackground(shape(primarySoft,0,11));
            itemRow.addView(index,new LinearLayout.LayoutParams(dp(35),dp(35)));
            LinearLayout details=column();
            TextView code=label(item.code,15,ink,true);
            code.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
            code.setSingleLine(true);code.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(code);
            TextView fmt=label(item.format==null?BarcodeFormatLabel.UNKNOWN:item.format,11,success,false);
            LinearLayout.LayoutParams fpl=new LinearLayout.LayoutParams(-1,-2);fpl.topMargin=dp(3);
            details.addView(fmt,fpl);
            LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(0,-2,1);dp1.leftMargin=dp(10);
            itemRow.addView(details,dp1);
            itemRow.addView(label(item.time,11,muted,false));
            itemRow.setContentDescription("Leitura "+item.code+", "+item.format+", "+item.time);
            itemRow.setOnLongClickListener(v->{
                android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(cm!=null){cm.setPrimaryClip(android.content.ClipData.newPlainText("Código de barras",item.code));toast("Código copiado.");}
                return true;
            });
            recentReadsList.addView(itemRow);
            i++;
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
        shizukuSettingsStatus.setTextColor(ready && accessible?success:amber);
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
        toast("BipXuco ativado. Teste uma leitura no MLOG.");
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
        title("Ajustes","Personalize e mantenha o coletor pronto.");
        LinearLayout mode=card();
        LinearLayout lineHead=row();
        lineHead.addView(icon(R.drawable.ui_scan,primary,24));
        LinearLayout modeName=column();
        modeName.addView(label("BipXuco",18,ink,true));
        modeName.addView(label("Leitura via Shizuku",12,muted,false));
        LinearLayout.LayoutParams mn=new LinearLayout.LayoutParams(0,-2,1);mn.leftMargin=dp(10);
        lineHead.addView(modeName,mn);mode.addView(lineHead);
        shizukuSettingsStatus=label("Verificando...",13,muted,true);
        LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(10);
        mode.addView(shizukuSettingsStatus,st);updateShizukuUiStatus();
        button(mode,"Ativar / reconectar",true,this::selectShizukuMode);
        button(mode,"Acessibilidade",false,this::openAccessibilitySettings);
        space(mode,7);
        check(mode,"Recuperação por Volume","camera_auto_recovery_v348",true);
        button(mode,"Reconfigurar leitor",false,()->toast(NativeScannerController.configure(this)?
            "Leitor reconfigurado.":"Falha ao configurar o leitor."));

        LinearLayout tools=card();
        tools.addView(label("Botão flutuante",17,ink,true));
        subtitle(tools,"Acesso rápido às ferramentas no MLOG.");
        button(tools,"Abrir ferramentas",true,this::openFloatingTools);
        button(tools,"Desligar botão flutuante",false,()->{
            stopService(new Intent(this,FloatingToolsService.class));
            prefs.edit().putBoolean("floating_auto_start_v302",false)
                .putBoolean(ScanAssistState.CLEAN,false)
                .putBoolean(ScanAssistState.BATCH,false)
                .putBoolean(ScanAssistState.ARMED,false).apply();
            toast("Botão flutuante desligado.");
        });

        LinearLayout appearance=card();appearance.addView(label("Aparência",17,ink,true));
        LinearLayout themes=row();
        for(String[] opt:new String[][]{{"auto","Sistema"},{"dark","Escuro"},{"light","Claro"}}){
            TextView choice=action(opt[1],opt[0].equals(themeChoice),()->chooseTheme(opt[0]));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            if(themes.getChildCount()>0)lp.leftMargin=dp(7);
            themes.addView(choice,lp);
        }
        space(appearance,12);appearance.addView(themes);
        LinearLayout automatic=card();automatic.addView(label("Bipes automáticos",17,ink,true));
        subtitle(automatic,"Recurso opcional protegido por senha.");
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
        title("Diagnóstico","Consulte o estado da última operação.");
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
        title("Versão "+installedVersion,"Atualizações e permissões do Bridge.");
        LinearLayout updates=card();updates.addView(label("Atualizações",17,ink,true));
        subtitle(updates,"Busca a cada ~10 min, quando o Android permitir.");
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
