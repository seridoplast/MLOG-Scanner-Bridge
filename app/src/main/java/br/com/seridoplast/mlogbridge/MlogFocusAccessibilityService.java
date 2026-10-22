package br.com.seridoplast.mlogbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v2.4 FOCO APENAS.
 * CMX envia caracteres e Enter via emulacao de teclado; este servico NUNCA
 * insere codigo, envia Enter, recebe broadcast nem mantem fila de leituras.
 * A unica automacao e selecionar/abrir o input nativo do MLOG quando o usuario
 * pressiona o gatilho fisico. Devolve false para preservar o evento ao CMX.
 */
public final class MlogFocusAccessibilityService extends AccessibilityService {
    private static volatile MlogFocusAccessibilityService instance;
    static MlogFocusAccessibilityService current(){return instance;}
    private final Handler main=new Handler(Looper.getMainLooper());
    private long lastTrigger;
    private long lastUnrecognized;
    private long openingUntil;
    private int openingEpoch;
    private String openingScreen="";
    private boolean configuredTriggers;
    private boolean clickInProgress;
    private long lastTrace;

    private SharedPreferences prefs(){return getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);}
    private void trace(String stage){
        Log.i("MLOGBridge","elapsed="+SystemClock.elapsedRealtime()+" stage="+stage);
    }
    private void status(String message){ScanCoordinator.status(this,message);}

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        instance=this;
        if(!prefs().getBoolean("focus_only_v24_migrated",false)){
            prefs().edit().putBoolean("trigger_prefocus",true)
                 .putBoolean("focus_only_v24_migrated",true).apply();
        }
        refreshEventThrottle();
        status("v2.4: somente foco. CMX responsavel por codigo e Enter.");
    }
    void refreshEventThrottle(){
        AccessibilityServiceInfo info=getServiceInfo();
        if(info==null)return;
        boolean enabled=prefs().getBoolean("trigger_prefocus",true);
        if(enabled)info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        else info.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        // Acessibilidade recebe eventos, mas nao precisa inspecionar telas
        // durante toda a animacao do MLOG quando nao houver gatilho ativo.
        info.notificationTimeout=35;
        try{setServiceInfo(info);configuredTriggers=enabled;}
        catch(RuntimeException ex){trace("SET_SERVICE_INFO_FAILED");}
    }

    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event==null || event.getAction()!=KeyEvent.ACTION_DOWN ||
           event.getRepeatCount()!=0 || !ScanCoordinator.isActive(this) ||
           !prefs().getBoolean("trigger_prefocus",true))return false;
        final int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_ENTER || code==KeyEvent.KEYCODE_NUMPAD_ENTER ||
           code==KeyEvent.KEYCODE_TAB || code==KeyEvent.KEYCODE_DEL ||
           code==KeyEvent.KEYCODE_BACK || code==KeyEvent.KEYCODE_UNKNOWN)return false;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return false;
        ScreenInfo screen=inspect(root);
        if(screen.login || screen.dialog || screen.screen.equals("desconhecida"))return false;
        boolean known=isTrigger(code) || code==prefs().getInt("learned_trigger_key",-1);
        if(!known){
            if(prefs().getBoolean("learn_trigger_armed",false) &&
               !event.isPrintingKey() && !KeyEvent.isModifierKey(code)){
                prefs().edit().putInt("learned_trigger_key",code)
                        .putBoolean("learn_trigger_armed",false).apply();
                trace("PHYSICAL_TRIGGER_LEARNED_"+code);
                status("Botao aprendido: "+code+". Bipe novamente.");
            }else if(SystemClock.elapsedRealtime()-lastUnrecognized>1200){
                lastUnrecognized=SystemClock.elapsedRealtime();
                trace("PHYSICAL_KEY_OTHER_"+code);
            }
            return false;
        }
        long now=SystemClock.elapsedRealtime();
        if(now-lastTrigger<100)return false; // key-repeat/firmware bounce; nao filtra codigos
        lastTrigger=now;
        trace("TRIGGER_SEEN_"+code);
        if(clickInProgress)return false;
        if(isValidFocusedEditor(screen)){
            trace("FOCUS_ALREADY_READY");
            return false;
        }
        // Nunca tocar no botao nativo se o editor ja esta em abertura.
        if(now<openingUntil && openingScreen.equals(screen.screen)){
            trace("FOCUS_OPENING_WAIT");
            return false;
        }
        if(screen.screen.equals("consulta") || screen.screen.equals("conferencia_venda")){
            AccessibilityNodeInfo search=findSearchEditor(screen);
            if(search!=null && focusOrClick(search)){
                trace("SEARCH_FOCUS_REQUESTED");
                awaitEditor(screen.screen);
            }else trace("SEARCH_FIELD_NOT_ACCESSIBLE");
            return false;
        }
        // Mesmo com IME visivel, um editor de quantidade pode estar ativo.
        // Aceitar clique SOMENTE no botao semantico reconhecido; jamais gesto
        // por coordenada enquanto houver teclado do Android na tela.
        AccessibilityNodeInfo button=findKeyboardButton(screen);
        if(button!=null && focusOrClick(button)){
            trace("MLOG_KEYBOARD_CLICKED");
            awaitEditor(screen.screen);
        }else if(prefs().getBoolean("guided_taps",true) &&
                 safePositionFallback(screen)){
            // Flutter ocasionalmente expoe o icone sem semantica acessivel.
            // Um UNICO toque, apenas nos layouts conhecidos e com IME fechado.
            awaitEditor(screen.screen);
        }else{
            trace("MLOG_KEYBOARD_NOT_FOUND");
            status("Botao do teclado nao encontrado nesta tela.");
        }
        return false; // CMX deve receber o gatilho normalmente
    }

    private void awaitEditor(String screen){
        openingScreen=screen;
        openingUntil=SystemClock.elapsedRealtime()+420;
        final int id=++openingEpoch;
        // Observacao passiva: nao reenviar toques ou Enter.
        main.postDelayed(()->probeEditor(id,screen),55);
    }
    private void probeEditor(int id,String screen){
        if(id!=openingEpoch)return;
        if(SystemClock.elapsedRealtime()>openingUntil){
            openingUntil=0;openingScreen="";
            trace("FOCUS_PROBE_EXPIRED");
            return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo now=inspect(root);
        if(isValidFocusedEditor(now)){
            openingUntil=0;
            openingScreen="";
            trace("FOCUS_READY");
            return;
        }
        main.postDelayed(()->probeEditor(id,screen),65);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        // Sem scans, sem inspecao de fila, sem processamento por evento.
        if(event==null || openingUntil==0 ||
           SystemClock.elapsedRealtime()>openingUntil)return;
        if(event.getEventType()!=AccessibilityEvent.TYPE_VIEW_FOCUSED &&
           event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)return;
        // Timer em awaitEditor observa o foco; nao repetir clique aqui.
    }
    private boolean isValidFocusedEditor(ScreenInfo info){
        if(info.login || info.dialog)return false;
        for(AccessibilityNodeInfo e:info.editors){
            if(e.isFocused() && e.isEditable() && e.isEnabled() &&
               !e.isPassword() && !blocked(tag(e))){
                if(info.screen.equals("separacao") && info.hasQuantity &&
                   !has(tag(e),"codigo","bipe","leitura","barcode") &&
                   !openingScreen.equals(info.screen))continue;
                return true;
            }
        }
        return false;
    }
    private AccessibilityNodeInfo findSearchEditor(ScreenInfo info){
        for(AccessibilityNodeInfo n:info.editors){
            if(n.isEditable() && !n.isPassword() && !blocked(tag(n)))return n;
        }
        if(info.searchField!=null)return info.searchField;
        return null;
    }
    private AccessibilityNodeInfo findKeyboardButton(ScreenInfo info){
        if(info.keyboard!=null){
            Rect r=new Rect();info.keyboard.getBoundsInScreen(r);
            Point p=screenSize();
            if(r.width()>0 && r.width()<p.x*.32f && r.height()<p.y*.15f &&
               !blocked(tag(info.keyboard)))return info.keyboard;
        }
        // O par camera / teclado aparece em separacao, pedido e atividades.
        AccessibilityNodeInfo camera=null;
        for(AccessibilityNodeInfo n:info.clicks){
            if(has(tag(n),"camera","fotografar")){
                if(camera!=null && !sameBounds(camera,n))return null;
                camera=n;
            }
        }
        if(camera==null)return null;
        Rect c=new Rect();camera.getBoundsInScreen(c);
        AccessibilityNodeInfo result=null;
        for(AccessibilityNodeInfo n:info.clicks){
            if(n==camera || blocked(tag(n)))continue;
            Rect r=new Rect();n.getBoundsInScreen(r);
            if(r.centerX()<=c.centerX()+c.width()/2 ||
               r.centerX()>c.centerX()+c.width()*3.3f ||
               Math.abs(r.centerY()-c.centerY())>Math.max(c.height(),r.height())*.52f ||
               r.width()<c.width()*.42f)continue;
            if(result!=null && !sameBounds(result,n))return null;
            result=n;
        }
        return result;
    }
    private boolean safePositionFallback(ScreenInfo info){
        if(isImeVisible() || clickInProgress)return false;
        Point size=screenSize();
        float ratio=(float)size.x/size.y;
        if(ratio<.46f || ratio>.54f)return false;
        float x,y;
        if(info.screen.equals("separacao") && info.hasSeparationControls){
            x=info.hasQuantity?.44f:.60f;y=.603f;
        }else if(info.screen.equals("atividades") && info.hasActivitiesTitle){
            x=.375f;y=.888f;
        }else return false;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null || inspect(root).dialog)return false;
        trace("GUIDED_NATIVE_BUTTON_TAP");
        Path path=new Path();path.moveTo(size.x*x,size.y*y);
        GestureDescription.Builder g=new GestureDescription.Builder();
        g.addStroke(new GestureDescription.StrokeDescription(path,0,42));
        clickInProgress=true;
        try{
            boolean sent=dispatchGesture(g.build(),new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription d){clickInProgress=false;}
                @Override public void onCancelled(GestureDescription d){clickInProgress=false;}
            },main);
            if(!sent)clickInProgress=false;
            return sent;
        }catch(RuntimeException ignored){clickInProgress=false;return false;}
    }
    private AccessibilityNodeInfo mlogRoot(){
        try{
            AccessibilityNodeInfo active=getRootInActiveWindow();
            if(active!=null && ScanCoordinator.TARGET.contentEquals(active.getPackageName()))return active;
            AccessibilityNodeInfo candidate=null;
            boolean otherActive=false;
            for(AccessibilityWindowInfo w:getWindows()){
                if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
                AccessibilityNodeInfo root=w.getRoot();
                if(root==null)continue;
                if(ScanCoordinator.TARGET.contentEquals(root.getPackageName()))candidate=root;
                else if(w.isActive() || w.isFocused())otherActive=true;
            }
            return otherActive?null:candidate;
        }catch(RuntimeException ignored){return null;}
    }
    private boolean isImeVisible(){
        try{
            for(AccessibilityWindowInfo w:getWindows())
                if(w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD)return true;
        }catch(RuntimeException ignored){}
        return false;
    }
    private Point screenSize(){
        Point p=new Point(getResources().getDisplayMetrics().widthPixels,
                          getResources().getDisplayMetrics().heightPixels);
        try{
            WindowManager wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
            if(wm!=null){Display d=wm.getDefaultDisplay();if(d!=null)d.getRealSize(p);}
        }catch(RuntimeException ignored){}
        return p;
    }
    private static boolean focusOrClick(AccessibilityNodeInfo n){
        if(n==null || !n.isEnabled() || !n.isVisibleToUser())return false;
        try{
            if(n.isEditable() && n.isFocusable() &&
               n.performAction(AccessibilityNodeInfo.ACTION_FOCUS))return true;
            if(n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
            if(n.isFocusable() && n.performAction(AccessibilityNodeInfo.ACTION_FOCUS))return true;
        }catch(RuntimeException ignored){}
        return false;
    }
    private static boolean isTrigger(int k){
        return (k>=KeyEvent.KEYCODE_F1 && k<=KeyEvent.KEYCODE_F12) ||
               (k>=KeyEvent.KEYCODE_BUTTON_1 && k<=KeyEvent.KEYCODE_BUTTON_16) ||
               k==KeyEvent.KEYCODE_BUTTON_L1 || k==KeyEvent.KEYCODE_BUTTON_R1 ||
               k==KeyEvent.KEYCODE_CAMERA || k==KeyEvent.KEYCODE_FOCUS ||
               (k>=KeyEvent.KEYCODE_PROG_RED && k<=KeyEvent.KEYCODE_PROG_BLUE);
    }
    private static String norm(CharSequence value){
        if(value==null)return "";
        return Normalizer.normalize(value.toString(),Normalizer.Form.NFD)
             .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).trim();
    }
    private static String tag(AccessibilityNodeInfo n){
        if(n==null)return "";
        return norm(n.getContentDescription())+" "+norm(n.getHintText())+" "+
               norm(n.getViewIdResourceName())+" "+norm(n.getText());
    }
    private static boolean has(String text,String... values){
        for(String s:values)if(text.contains(s))return true;
        return false;
    }
    private static boolean blocked(String value){
        return has(value,"quantidade","peso","volume","senha","password",
                  "fracionado","iniciar","cancelar");
    }
    private static boolean sameBounds(AccessibilityNodeInfo a,AccessibilityNodeInfo b){
        Rect ar=new Rect(),br=new Rect();a.getBoundsInScreen(ar);b.getBoundsInScreen(br);
        return Math.abs(ar.centerX()-br.centerX())<18 &&
               Math.abs(ar.centerY()-br.centerY())<18;
    }
    private static final class ScreenInfo{
        String screen="desconhecida";
        boolean login,dialog,hasQuantity,hasActivitiesTitle,hasSeparationControls;
        AccessibilityNodeInfo keyboard,searchField;
        final List<AccessibilityNodeInfo> editors=new ArrayList<>();
        final List<AccessibilityNodeInfo> clicks=new ArrayList<>();
    }
    private ScreenInfo inspect(AccessibilityNodeInfo root){
        ScreenInfo result=new ScreenInfo();
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        visit(root,nodes,0);
        boolean separated=false,orders=false,invoices=false,consult=false,conference=false;
        boolean pular=false,encerrar=false,emptyActivities=false;
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser())continue;
            String t=tag(n);
            if(has(t,"bipar atividades de separacao","bipar atividade de separacao","bipar atividades"))result.hasActivitiesTitle=true;
            if(has(t,"nenhuma atividade registrada"))emptyActivities=true;
            if(has(t,"busca da conferencia de venda"))conference=true;
            if(has(t,"bipe ou digite enderec","local de estoque"))consult=true;
            if(has(t,"pular item"))pular=true;
            if(has(t,"encerrar item"))encerrar=true;
            if(has(t,"separacao","separar"))separated=true;
            if(has(t,"quantidade:"))result.hasQuantity=true;
            if(has(t,"bipar pedido","bipar pedidos","bipagem de pedidos"))orders=true;
            if(has(t,"bipar nota","nota fiscal","bipagem de nota"))invoices=true;
            if(has(t,"login","autenticacao","senha de acesso"))result.login=true;
            if(has(t,"atencao!","confirmar cancelamento","atividade cancelada"))result.dialog=true;
            if(n.isEditable() && !n.isPassword())result.editors.add(n);
            if(has(t,"bipe ou digite","digite o codigo da atividade"))result.searchField=n;
            if(n.isClickable() && n.isEnabled())result.clicks.add(n);
            if(!blocked(t) && has(t,"teclado","keyboard","entrada manual","digitar codigo","ler codigo")){
                AccessibilityNodeInfo button=clickable(n);
                if(result.keyboard==null)result.keyboard=button;
                else if(!sameBounds(result.keyboard,button))result.keyboard=null;
            }
        }
        result.hasSeparationControls=pular && encerrar;
        if(conference)result.screen="conferencia_venda";
        else if(consult)result.screen="consulta";
        else if(result.hasActivitiesTitle || emptyActivities)result.screen="atividades";
        else if(orders)result.screen="pedidos";
        else if(separated || result.hasSeparationControls)result.screen="separacao";
        else if(invoices)result.screen="notas";
        if(result.login)result.screen="desconhecida";
        return result;
    }
    private static AccessibilityNodeInfo clickable(AccessibilityNodeInfo n){
        AccessibilityNodeInfo cur=n;
        for(int i=0;i<4 && cur!=null;i++,cur=cur.getParent())
            if(cur.isClickable() || cur.isFocusable())return cur;
        return n;
    }
    private static void visit(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out,int depth){
        if(n==null || depth>35 || out.size()>440)return;
        out.add(n);
        for(int i=0;i<n.getChildCount();i++)visit(n.getChild(i),out,depth+1);
    }
    @Override public void onInterrupt(){openingUntil=0;openingEpoch++;status("Serviço interrompido.");}
    @Override public void onDestroy(){
        main.removeCallbacksAndMessages(null);
        openingEpoch++;openingUntil=0;
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
