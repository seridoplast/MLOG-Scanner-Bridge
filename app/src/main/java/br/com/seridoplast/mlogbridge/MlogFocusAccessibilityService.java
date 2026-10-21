package br.com.seridoplast.mlogbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MLOG-only accessibility bridge. Neither an input method nor a key mapper.
 * Barcode broadcasts -> find explicit code field OR click labelled keyboard
 * button -> write via ACTION_SET_TEXT. No gestures or clipboard. Never logs codes.
 */
public class MlogFocusAccessibilityService extends AccessibilityService {
    private static volatile MlogFocusAccessibilityService instance;
    static MlogFocusAccessibilityService current(){return instance;}
    private final Handler main=new Handler(Looper.getMainLooper());
    private BroadcastReceiver scanReceiver;
    private boolean registered=false, keyboardSuppressed=false;
    private long lastStatusAt;
    private String pending=null;
    private long pendingUntil;
    private int attemptSerial=0;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        instance=this;
        AccessibilityServiceInfo info=getServiceInfo();
        if(info!=null){
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        scanReceiver=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent intent){
                String barcode=ScannerProtocol.barcode(intent);
                if(!ScanCoordinator.isActive(MlogFocusAccessibilityService.this) ||
                        !ScanCoordinator.accept(barcode))return;
                receiveScan(barcode);
            }
        };
        IntentFilter filter=new IntentFilter();
        for(String action:ScannerProtocol.ACTIONS) filter.addAction(action);
        try{
            if(Build.VERSION.SDK_INT>=33)
                registerReceiver(scanReceiver,filter,Context.RECEIVER_EXPORTED);
            else registerReceiver(scanReceiver,filter);
            registered=true;
            status("v0.5 ativo: scanner por broadcast, Gboard preservado.");
        }catch(RuntimeException e){status("Falha ao registrar eventos do CMX.");}
    }

    private void status(String message){ ScanCoordinator.status(this,message); }
    private SharedPreferences prefs(){return getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);}
    private void suppressKeyboard(){
        if(!prefs().getBoolean("hide_keyboard_on_scan",true))return;
        try{keyboardSuppressed=getSoftKeyboardController().setShowMode(SHOW_MODE_HIDDEN);}
        catch(RuntimeException e){status("Android recusou ocultar teclado virtual.");}
    }
    private void restoreKeyboard(){
        if(!keyboardSuppressed)return;
        try{getSoftKeyboardController().setShowMode(SHOW_MODE_AUTO);}catch(RuntimeException ignored){}
        keyboardSuppressed=false;
    }
    private void delayedRestore(int serial){
        main.postDelayed(()->{if(serial==attemptSerial)restoreKeyboard();},1600);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null)return;
        CharSequence pkg=event.getPackageName();
        if(pkg==null || !ScanCoordinator.TARGET.contentEquals(pkg)){
            // Do not prevent normal typing in any other app.
            if(pending==null)restoreKeyboard();
            return;
        }
        if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.getEventType()==AccessibilityEvent.TYPE_VIEW_FOCUSED){
            AccessibilityNodeInfo src=event.getSource();
            if(src!=null){
                String label=tag(src);
                if(blockedLabel(label) && (label.contains("quantidade") || label.contains("peso"))){
                    clearPending();restoreKeyboard();
                }
            }
        }
        long now=SystemClock.elapsedRealtime();
        if(now-lastStatusAt>1400 && pending==null){
            lastStatusAt=now;
            AccessibilityNodeInfo root=mlogWindow();
            if(root!=null){
                ViewInfo info=inspect(root);
                status("Tela="+info.screen+"; campos="+info.editors.size()+
                       "; teclado="+(info.keyboard!=null?"identificado":"nao identificado")+
                       "; botoes="+info.buttons+". Aguardando scanner.");
            }
        }
    }

    private AccessibilityNodeInfo mlogWindow(){
        try{
            for(AccessibilityWindowInfo w:getWindows()){
                if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
                AccessibilityNodeInfo root=w.getRoot();
                if(root!=null && ScanCoordinator.TARGET.contentEquals(root.getPackageName()) &&
                    (w.isActive() || w.isFocused()))return root;
            }
            AccessibilityNodeInfo root=getRootInActiveWindow();
            if(root!=null && ScanCoordinator.TARGET.contentEquals(root.getPackageName()))return root;
        }catch(RuntimeException ignored){}
        return null;
    }

    private void receiveScan(String code){
        AccessibilityNodeInfo root=mlogWindow();
        if(root==null){status("Ignorado: MLOG nao esta em primeiro plano.");return;}
        ViewInfo info=inspect(root);
        if(info.screen.equals("desconhecida") || info.login){
            status("Ignorado: tela nao autorizada para bipagem.");return;
        }
        // An already open editor must identify itself as a barcode field.
        AccessibilityNodeInfo verified=findSafeEditor(info,false);
        if(verified!=null){
            suppressKeyboard();
            int serial=++attemptSerial;
            setBarcode(verified,code);
            delayedRestore(serial);
            return;
        }
        AccessibilityNodeInfo keyboard=info.keyboard;
        if(keyboard==null && prefs().getBoolean("layout_fallback",false))
            keyboard=relativeKeyboard(info);
        if(keyboard==null){
            status("Nao achei botao de teclado com seguranca. Abra o diagnostico.");
            return;
        }
        // Hold data only in memory for a short timeout, never persist scanned code.
        clearPending();
        pending=code;
        pendingUntil=SystemClock.elapsedRealtime()+1850;
        int serial=++attemptSerial;
        suppressKeyboard();
        boolean clicked=keyboard.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if(!clicked){clearPending();restoreKeyboard();status("Botao identificado, mas Flutter recusou clique.");return;}
        status("Botao de codigo acionado; procurando editor sem teclado virtual.");
        for(int wait:new int[]{100,250,450,750,1150,1600})
            main.postDelayed(()->attemptFill(serial),wait);
        delayedRestore(serial);
    }

    private void attemptFill(int serial){
        if(serial!=attemptSerial || pending==null)return;
        if(SystemClock.elapsedRealtime()>pendingUntil){
            clearPending();status("Tempo esgotado: editor nao publicado pela tela Flutter.");return;
        }
        AccessibilityNodeInfo root=mlogWindow();
        if(root==null)return;
        ViewInfo info=inspect(root);
        if(info.screen.equals("desconhecida") || info.login)return;
        AccessibilityNodeInfo editor=findSafeEditor(info,true);
        if(editor==null)return;
        String value=pending;
        clearPending();
        setBarcode(editor,value);
    }

    private void setBarcode(AccessibilityNodeInfo editor,String value){
        if(editor==null || !editor.isEditable() || editor.isPassword() || !editor.isEnabled()){
            status("Campo nao editavel: nenhuma leitura enviada.");return;
        }
        boolean canSet=false;
        for(AccessibilityNodeInfo.AccessibilityAction a:editor.getActionList())
            if(a.getId()==AccessibilityNodeInfo.ACTION_SET_TEXT)canSet=true;
        if(!canSet){status("Flutter nao expoe ACTION_SET_TEXT; campo nao alterado.");return;}
        Bundle args=new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
        boolean done;
        try{done=editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);}
        catch(RuntimeException e){done=false;}
        if(!done){status("MLOG recusou preencher o campo via acessibilidade.");return;}
        status("Codigo enviado ao campo editavel do MLOG (sem salvar conteudo).");
        if(prefs().getBoolean("send_enter",false)){
            // ACTION_IME_ENTER is supported only when the target widget exposes it.
            boolean supported=false;
            for(AccessibilityNodeInfo.AccessibilityAction a:editor.getActionList())
                if(a.getId()==AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())supported=true;
            if(supported && editor.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId()))
                status("Codigo enviado; confirmacao IME solicitada.");
            else status("Codigo enviado; MLOG nao expos acao de confirmar.");
        }
    }
    private void clearPending(){pending=null;pendingUntil=0;}
    private static String tag(AccessibilityNodeInfo n){
        return clean(n.getContentDescription())+" "+clean(n.getHintText())+" "+
               clean(n.getViewIdResourceName())+" "+clean(n.getText());
    }
    private static String clean(CharSequence c){return c==null?"":c.toString().toLowerCase(Locale.ROOT);}
    private static boolean has(String s,String... tokens){for(String t:tokens)if(s.contains(t))return true;return false;}
    private static boolean blockedLabel(String label){
        return has(label,"quantidade","peso","volume","senha","password","valor", "iniciar");
    }
    static final class ViewInfo {
        String screen="desconhecida";
        boolean login;
        int buttons;
        AccessibilityNodeInfo keyboard;
        final List<AccessibilityNodeInfo> editors=new ArrayList<>();
        final List<AccessibilityNodeInfo> controls=new ArrayList<>();
    }
    private ViewInfo inspect(AccessibilityNodeInfo root){
        ViewInfo info=new ViewInfo();
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        traverse(root,nodes,0);
        for(AccessibilityNodeInfo node:nodes){
            if(!node.isVisibleToUser())continue;
            String label=tag(node);
            // Only these named workflows may receive codes.
            if(has(label,"bipar atividades de separa", "bipar atividade de separa")) info.screen="atividades";
            else if(has(label,"separação","separacao") && info.screen.equals("desconhecida")) info.screen="separacao";
            else if(has(label,"nota fiscal","conferência de nota","conferencia de nota",
                    "bipar nota","bipagem de nota","bipar atividades" ) && info.screen.equals("desconhecida")) info.screen="notas";
            if(has(label,"senha", "autenticacao", "autenticação", "login"))info.login=true;
            if(node.isEditable() && !node.isPassword())info.editors.add(node);
            if(node.isClickable() && node.isEnabled()){
                info.buttons++;
                info.controls.add(node);
                if(!blockedLabel(label) && has(label,"teclado","keyboard","inserir código",
                    "digitar codigo","digitar código","entrada manual", "ler codigo","ler código")){
                    if(info.keyboard==null)info.keyboard=node;
                    else if(!samePosition(info.keyboard,node))info.keyboard=null; // ambiguous
                }
            }
        }
        if(info.login){ info.keyboard=null; info.screen="desconhecida"; }
        return info;
    }
    private void traverse(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> out,int depth){
        if(root==null || depth>45 || out.size()>500)return;
        out.add(root);
        for(int i=0;i<root.getChildCount();i++)traverse(root.getChild(i),out,depth+1);
    }
    private static boolean samePosition(AccessibilityNodeInfo a, AccessibilityNodeInfo b){
        Rect x=new Rect(),y=new Rect();a.getBoundsInScreen(x);b.getBoundsInScreen(y);
        return Math.abs(x.centerX()-y.centerX())<15 && Math.abs(x.centerY()-y.centerY())<15;
    }
    private AccessibilityNodeInfo findSafeEditor(ViewInfo info,boolean openedByUs){
        List<AccessibilityNodeInfo> valid=new ArrayList<>();
        for(AccessibilityNodeInfo n:info.editors){
            if(!n.isEnabled() || !n.isVisibleToUser())continue;
            String label=tag(n);
            if(blockedLabel(label))continue;
            boolean clearlyCode=has(label,"codigo","código","barcode","cód. barras",
                    "code", "digite o codigo","barras");
            // If user has explicitly opened the code dialog through a verified
            // button, accept an unlabelled editor only if it is the UNIQUE edit.
            if(clearlyCode || openedByUs)valid.add(n);
        }
        return valid.size()==1?valid.get(0):null;
    }
    private AccessibilityNodeInfo relativeKeyboard(ViewInfo info){
        // Opt-in fallback: narrow, order-based heuristic only for known scanner
        // screens. Requires two aligned clickable icons with plausible sizes.
        if(info.screen.equals("desconhecida") || info.login)return null;
        final float density=getResources().getDisplayMetrics().density;
        final int w=getResources().getDisplayMetrics().widthPixels;
        final int h=getResources().getDisplayMetrics().heightPixels;
        class C {AccessibilityNodeInfo n;Rect r;C(AccessibilityNodeInfo n,Rect r){this.n=n;this.r=r;}}
        List<C> candidates=new ArrayList<>();
        for(AccessibilityNodeInfo n:info.controls){
            Rect r=new Rect();n.getBoundsInScreen(r);
            String t=tag(n);
            if(blockedLabel(t) || has(t,"camera","câmera","pular","encerrar","fechar","cancelar"))continue;
            if(r.centerY()<h*.48f || r.centerY()>h*.95f ||
               r.width()<46*density || r.width()>w*.26f ||
               r.height()<42*density || r.height()>105*density)continue;
            boolean duplicate=false;
            for(C c:candidates)if(Math.abs(c.r.centerX()-r.centerX())<20*density &&
                    Math.abs(c.r.centerY()-r.centerY())<20*density)duplicate=true;
            if(!duplicate)candidates.add(new C(n,r));
        }
        if(candidates.size()!=2)return null;
        C a=candidates.get(0),b=candidates.get(1);
        if(Math.abs(a.r.centerY()-b.r.centerY())>18*density)return null;
        if(a.r.centerX()>b.r.centerX()){C tmp=a;a=b;b=tmp;}
        if((b.r.centerX()-a.r.centerX())<w*.10f ||
           (b.r.centerX()-a.r.centerX())>w*.48f)return null;
        return b.n;
    }
    @Override public void onInterrupt(){clearPending();restoreKeyboard();status("Acessibilidade interrompida.");}
    @Override public void onDestroy(){
        if(registered)try{unregisterReceiver(scanReceiver);}catch(RuntimeException ignored){}
        main.removeCallbacksAndMessages(null);
        clearPending();restoreKeyboard();
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
