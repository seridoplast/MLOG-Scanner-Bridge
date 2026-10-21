package br.com.seridoplast.mlogbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leitura sem IME proprio: recebe broadcast do motor CMX e usa apenas a janela
 * do MLOG. Em buscas com entrada visivel foca o editor; em atividades aciona o
 * controle de teclado e aguarda o editor. Nao armazena codigos nem usa clipboard.
 *
 * Um gesto opcional e limitado a dois layouts identificados por titulo. O
 * posicionamento e relativo a tela do coletor, nao e uma garantia do Flutter.
 */
public final class MlogFocusAccessibilityService extends AccessibilityService {
    private static volatile MlogFocusAccessibilityService instance;
    static MlogFocusAccessibilityService current(){return instance;}

    private final Handler main=new Handler(Looper.getMainLooper());
    private BroadcastReceiver receiver;
    private boolean receiverRegistered;
    private boolean keyboardSuppressed;
    private String pendingCode;
    private long pendingDeadline;
    private int serial;
    private long lastDiagnostic;
    private boolean gestureInProgress;
    private long lastScanAt;
    private long lastAutoActionAt;
    private boolean keyboardWatchActive;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        instance=this;
        AccessibilityServiceInfo si=getServiceInfo();
        if(si!=null){
            si.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                      AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(si);
        }
        receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context context,Intent intent){
                String code=ScannerProtocol.barcode(intent);
                if(!ScanCoordinator.isActive(MlogFocusAccessibilityService.this) ||
                        !ScanCoordinator.accept(code))return;
                onBarcode(code);
            }
        };
        IntentFilter filter=new IntentFilter();
        for(String action:ScannerProtocol.ACTIONS)filter.addAction(action);
        try{
            if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
            else registerReceiver(receiver,filter);
            receiverRegistered=true;
            status("v0.7 pronto para leitura.");
        }catch(RuntimeException e){status("Falha ao registrar o broadcast do scanner.");}
    }

    private SharedPreferences prefs(){return getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);}
    private void status(String s){ScanCoordinator.status(this,s);}
    private void hideKeyboard(){
        if(!prefs().getBoolean("hide_keyboard_on_scan",true))return;
        try{
            lastScanAt=SystemClock.elapsedRealtime();
            keyboardSuppressed=getSoftKeyboardController().setShowMode(SHOW_MODE_HIDDEN);
            monitorKeyboard();
        }catch(RuntimeException e){status("O Android nao aceitou ocultar o teclado.");}
    }
    private void restoreKeyboard(){
        if(!keyboardSuppressed)return;
        try{getSoftKeyboardController().setShowMode(SHOW_MODE_AUTO);}
        catch(RuntimeException ignored){}
        keyboardSuppressed=false;
    }
    private void monitorKeyboard(){
        if(keyboardWatchActive || !keyboardSuppressed)return;
        keyboardWatchActive=true;
        main.postDelayed(new Runnable(){
            @Override public void run(){
                if(!keyboardSuppressed){keyboardWatchActive=false;return;}
                // Sem travar a entrada manual em outros aplicativos.
                if(mlogRoot()==null || SystemClock.elapsedRealtime()-lastScanAt>14000){
                    restoreKeyboard();keyboardWatchActive=false;return;
                }
                main.postDelayed(this,700);
            }
        },700);
    }
    private void scheduleRestore(int id){
        // O teclado fica oculto durante a sequencia de leituras. Um monitor
        // restaura a entrada normal ao sair do MLOG ou apos 14 s de inatividade.
        monitorKeyboard();
    }
    private void cancelPending(){pendingCode=null;pendingDeadline=0;gestureInProgress=false;}

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null)return;
        CharSequence pkg=event.getPackageName();
        if(pkg==null || !ScanCoordinator.TARGET.contentEquals(pkg)){
            // O usuario saiu do MLOG ou abriu o seletor de teclado: restaurar.
            if(pendingCode==null)restoreKeyboard();
            return;
        }
        if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED ||
           event.getEventType()==AccessibilityEvent.TYPE_VIEW_FOCUSED){
            AccessibilityNodeInfo source=event.getSource();
            if(source!=null){
                String label=tag(source);
                if(blockedLabel(label)){
                    cancelPending();restoreKeyboard();
                }else if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED &&
                         keyboardSuppressed &&
                         SystemClock.elapsedRealtime()-lastAutoActionAt>850 &&
                         (source.isEditable() || has(label,"teclado","keyboard","digitar"))){
                    // Um clique humano no campo/teclado volta a permitir digitar.
                    restoreKeyboard();
                }
            }
        }
        long time=SystemClock.elapsedRealtime();
        if(pendingCode==null && time-lastDiagnostic>1750 && time-lastAutoActionAt>30000){
            lastDiagnostic=time;
            AccessibilityNodeInfo root=mlogRoot();
            if(root!=null){
                ScreenInfo i=inspect(root);
                status("Tela: "+i.screen+" | editaveis: "+i.editors.size()+
                    " | icone teclado: "+(i.keyboard!=null?"sim":"nao")+
                    " | busca: "+(i.searchField!=null?"visivel":"nao detectada")+
                    ". Aguarda bipe.");
            }
        }
    }

    private AccessibilityNodeInfo mlogRoot(){
        try{
            AccessibilityNodeInfo active=getRootInActiveWindow();
            if(active!=null && ScanCoordinator.TARGET.contentEquals(active.getPackageName()))return active;
            // Quando o Gboard abre, a janela MLOG deixa de ser a ativa.
            // Ainda assim ela permanece visivel sob a janela TYPE_INPUT_METHOD.
            // Nao usar MLOG caso OUTRO aplicativo normal tenha tomado a frente.
            AccessibilityNodeInfo candidate=null;
            boolean otherApplicationActive=false;
            for(AccessibilityWindowInfo window:getWindows()){
                if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
                AccessibilityNodeInfo root=window.getRoot();
                if(root==null)continue;
                if(ScanCoordinator.TARGET.contentEquals(root.getPackageName())){
                    candidate=root;
                }else if(window.isActive() || window.isFocused()){
                    otherApplicationActive=true;
                }
            }
            return otherApplicationActive?null:candidate;
        }catch(RuntimeException ignored){}
        return null;
    }

    private void onBarcode(String code){
        lastAutoActionAt=SystemClock.elapsedRealtime();
        if(pendingCode!=null){status("Leitura pendente: aguarde o campo de codigo.");return;}
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null){status("Ignorado: MLOG nao esta na tela ativa.");return;}
        ScreenInfo i=inspect(root);
        if(i.login || i.dialog || "desconhecida".equals(i.screen)){
            status("Leitura pausada: tela desconhecida ou aviso aberto.");return;
        }

        // Na tela de atividades, o botao de teclado e o portao de entrada.
        // PRIMEIRO abrimos o campo; esconder o IME antes do clique impedia
        // alguns modais Flutter de disponibilizar o editor para acessibilidade.
        if("atividades".equals(i.screen)){
            startPending(code,"atividades");
            AccessibilityNodeInfo button=activityKeyboard(i);
            if(button!=null && focusOrClick(button)){
                status("Atividades: botao de leitura acionado; aguardando campo.");
                retryFill(serial,"atividades");return;
            }
            if(i.hasActivitiesTitle && prefs().getBoolean("guided_taps",true)){
                tapKnownActivitiesKeyboard(serial);return;
            }
            cancelPending();
            status("Atividades: icone nao exposto; ative toque guiado no Bridge.");
            return;
        }

        // A busca de conferencia possui uma caixa visivel na parte superior.
        // Portanto NAO se deve clicar no botao de teclado/teclar por cima.
        if("conferencia_venda".equals(i.screen)){
            AccessibilityNodeInfo editor=safeEditor(i,true);
            if(editor!=null){
                hideKeyboard();
                int id=++serial;
                focusAndSet(editor,code);
                scheduleRestore(id);
                return;
            }
            startPending(code,"conferencia_venda");
            hideKeyboard();
            if(i.searchField!=null){
                AccessibilityNodeInfo target=clickable(i.searchField);
                boolean ok=focusOrClick(target);
                if(ok){status("Busca de conferencia selecionada; aguardando campo editavel.");
                    retryFill(serial,"conferencia_venda");return;}
            }
            if(prefs().getBoolean("guided_taps",true) && i.hasConferenceTitle){
                // Somente no layout identificado com o titulo da busca.
                tapKnownSearchField(serial);
            }else{
                cancelPending();restoreKeyboard();
                status("Busca: campo nao acessivel. Ative toque assistido ou envie diagnostico.");
            }
            return;
        }

        AccessibilityNodeInfo editor=safeEditor(i,false);
        if(editor!=null){
            hideKeyboard();
            int id=++serial;
            focusAndSet(editor,code);
            scheduleRestore(id);
            return;
        }

        // Separacao e atividades: a entrada de codigo pode aparecer somente
        // apos o clique no icone do teclado. Quantidade NUNCA e elegivel.
        startPending(code,i.screen);
        // Ocultacao so acontece DEPOIS de o campo estar aberto.
        if(i.keyboard!=null && focusOrClick(clickable(i.keyboard))){
            status("Icone de codigo acionado. Aguardando editor sem abrir IME.");
            retryFill(serial,i.screen);
            return;
        }
        if("separacao".equals(i.screen) &&
                prefs().getBoolean("layout_fallback",false)){
            AccessibilityNodeInfo n=relativeKeyboard(i);
            if(n!=null && focusOrClick(n)){
                status("Botao de leitura de produto acionado por layout relativo.");
                retryFill(serial,i.screen);return;
            }
        }
        cancelPending();restoreKeyboard();
        status("Icone nao identificado. Nao toquei em Quantidade nem em outro botao.");
    }

    private void startPending(String code,String type){
        // O codigo so permanece na memoria ate concluir a tentativa.
        pendingCode=code;
        pendingDeadline=SystemClock.elapsedRealtime()+3500;
        ++serial;
    }
    private void retryFill(int id,String intendedScreen){
        for(int delay:new int[]{100,230,400,650,950,1400,2000,2650,3200,3500}){
            main.postDelayed(()->tryPending(id,intendedScreen),delay);
        }
        scheduleRestore(id);
    }
    private void tryPending(int id,String intendedScreen){
        if(id!=serial || pendingCode==null)return;
        if(SystemClock.elapsedRealtime()>pendingDeadline){
            cancelPending();restoreKeyboard();
            status("O campo do Flutter nao apareceu na acessibilidade. Sem codigo enviado.");
            return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo i=inspect(root);
        if(i.login || i.dialog){
            cancelPending();restoreKeyboard();
            status("Aviso/modal detectado: leitura cancelada para evitar erro.");return;
        }
        if(!intendedScreen.equals(i.screen)){
            // Modais Flutter podem abrir uma janela própria sem o título da
            // página anterior. Aceitamos SOMENTE um editor único, na janela
            // do MLOG, após nosso clique na busca ou no botão de atividades.
            if(!"desconhecida".equals(i.screen) || i.editors.size()!=1 ||
                !("atividades".equals(intendedScreen) ||
                  "conferencia_venda".equals(intendedScreen)))return;
            i.screen=intendedScreen;
        }
        AccessibilityNodeInfo editor=safeEditor(i,true);
        if(editor==null)return;
        String code=pendingCode;
        cancelPending();
        // So suprimir teclado apos o editor estar disponivel; evita o
        // comportamento observado no video (teclado nao inicia o campo).
        hideKeyboard();
        focusAndSet(editor,code);
    }

    private void focusAndSet(AccessibilityNodeInfo editor,String code){
        if(editor==null || !editor.isEditable() || !editor.isEnabled() || editor.isPassword()){
            status("Campo bloqueado: leitura nao enviada.");return;
        }
        if(blockedLabel(tag(editor))){status("Campo de quantidade/peso ignorado.");return;}
        // Alguns CMX continuam enviando a leitura como teclas (wedge) E broadcast.
        // Se o MLOG ja recebeu algo, nao sobrescrever nem duplicar o codigo.
        CharSequence existing=editor.getText();
        if(existing!=null && existing.length()>0){
            if(code.equals(existing.toString().trim()))
                status("O MLOG ja recebeu esta leitura pelo scanner nativo.");
            else status("Campo ocupado: nao sobrescrevi texto existente.");
            return;
        }
        try{
            // ACTION_FOCUS nao exige tocar na tela; SHOW_MODE_HIDDEN ja foi aplicado.
            if(!editor.isFocused()) editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if(!supports(editor,AccessibilityNodeInfo.ACTION_SET_TEXT)){
                status("Flutter nao expos ACTION_SET_TEXT; codigo nao enviado.");return;
            }
            Bundle data=new Bundle();
            data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,code);
            boolean done=editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data);
            if(!done){status("MLOG recusou preencher o campo de leitura.");return;}
            status("Leitura inserida no campo do MLOG; codigo nao foi registrado.");
            if(prefs().getBoolean("send_enter",false)){
                int action=AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId();
                if(supports(editor,action) && editor.performAction(action))
                    status("Codigo inserido e Enter solicitado.");
                else status("Codigo inserido. MLOG nao expos Enter automatico.");
            }
        }catch(RuntimeException e){status("Falha de acessibilidade ao inserir o codigo.");}
    }
    private static boolean supports(AccessibilityNodeInfo n,int action){
        for(AccessibilityNodeInfo.AccessibilityAction a:n.getActionList())
            if(a.getId()==action)return true;
        return false;
    }
    private static boolean focusOrClick(AccessibilityNodeInfo n){
        if(n==null || !n.isEnabled() || !n.isVisibleToUser())return false;
        try{
            if(n.isEditable() && n.isFocusable() && n.performAction(AccessibilityNodeInfo.ACTION_FOCUS))return true;
            if(n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
            if(n.isFocusable() && n.performAction(AccessibilityNodeInfo.ACTION_FOCUS))return true;
        }catch(RuntimeException ignored){}
        return false;
    }

    private AccessibilityNodeInfo activityKeyboard(ScreenInfo info){
        // Em Flutter um "keyboard" sem descricao pode ser um container inteiro.
        // Aceitamos apenas um alvo na regiao inferior esquerda, onde o video
        // confirmou o SEGUNDO botao, ao lado da camera. Nunca usar "Iniciar".
        Point p=realScreenSize();
        AccessibilityNodeInfo n=info.keyboard;
        if(n==null)return null;
        Rect b=new Rect();n.getBoundsInScreen(b);
        float x=(float)b.centerX()/p.x, y=(float)b.centerY()/p.y;
        if(x<0.29f || x>0.52f || y<0.79f || y>0.96f)return null;
        return n;
    }

    private void tapKnownSearchField(int id){
        // Captura da tela enviada pelo usuario: entrada a ~35% da largura e
        // ~14,4% da altura. Nunca aplicar esse gesto fora da tela nomeada.
        Point size=realScreenSize();
        if(!supportedAspect(size)){
            cancelPending();restoreKeyboard();status("Resolucao nao homologada para toque assistido; envie diagnostico.");return;
        }
        status("Busca: tentando focar a caixa superior por posicao relativa.");
        tapAt(id,size.x*0.34f,size.y*0.145f,"conferencia_venda");
    }
    private void tapKnownActivitiesKeyboard(int id){
        // Captura 720x1440 e video do coletor: botao direito do par camera /
        // teclado junto a borda inferior; x~37.5%, y~88.8% da tela fisica.
        // Executa SOMENTE se o titulo de atividades estiver acessivel.
        Point size=realScreenSize();
        if(!supportedAspect(size)){
            cancelPending();restoreKeyboard();status("Resolucao nao homologada para toque assistido; envie diagnostico.");return;
        }
        status("Atividades: tentando abrir entrada pelo icone de teclado inferior.");
        tapAt(id,size.x*0.375f,size.y*0.888f,"atividades");
    }
    private Point realScreenSize(){
        Point size=new Point(getResources().getDisplayMetrics().widthPixels,
                             getResources().getDisplayMetrics().heightPixels);
        try{
            WindowManager wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
            if(wm!=null){Display d=wm.getDefaultDisplay();if(d!=null)d.getRealSize(size);}
        }catch(RuntimeException ignored){}
        return size;
    }
    private static boolean supportedAspect(Point p){
        return p.x>0 && p.y>0 && ((double)p.x/p.y)>0.46 &&
               ((double)p.x/p.y)<0.54;
    }
    private void tapAt(int id,float x,float y,String screen){
        if(gestureInProgress || pendingCode==null || id!=serial)return;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo before=inspect(root);
        if(!screen.equals(before.screen) || before.login ||
            ("atividades".equals(screen) && !before.hasActivitiesTitle) ||
            ("conferencia_venda".equals(screen) && !before.hasConferenceTitle)){
            cancelPending();restoreKeyboard();status("Tela mudou: gesto cancelado.");return;
        }
        gestureInProgress=true;
        lastAutoActionAt=SystemClock.elapsedRealtime();
        Path path=new Path();path.moveTo(x,y);
        GestureDescription.Builder b=new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(path,0,75));
        boolean dispatched;
        try{
            dispatched=dispatchGesture(b.build(),new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription gesture){
                    gestureInProgress=false;
                    retryFill(id,screen);
                }
                @Override public void onCancelled(GestureDescription gesture){
                    gestureInProgress=false;cancelPending();restoreKeyboard();
                    status("Toque assistido cancelado pelo Android.");
                }
            },main);
        }catch(RuntimeException e){dispatched=false;}
        if(!dispatched){gestureInProgress=false;cancelPending();restoreKeyboard();
            status("Android nao permitiu toque assistido.");}
    }

    private static String norm(CharSequence cs){
        if(cs==null)return "";
        String s=Normalizer.normalize(cs.toString(),Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }
    private static String tag(AccessibilityNodeInfo n){
        return norm(n.getContentDescription())+" "+norm(n.getHintText())+" "+
               norm(n.getViewIdResourceName())+" "+norm(n.getText());
    }
    private static boolean has(String text,String... parts){
        for(String s:parts)if(text.contains(s))return true;
        return false;
    }
    private static boolean blockedLabel(String s){
        return has(s,"quantidade", "peso", "volume", "senha", "password", "valor", "iniciar", "fracionado");
    }
    private static final class ScreenInfo{
        String screen="desconhecida";
        boolean login,hasActivitiesTitle,hasConferenceTitle,dialog,noActivities,hasStart;
        AccessibilityNodeInfo keyboard,searchField;
        final List<AccessibilityNodeInfo> editors=new ArrayList<>();
        final List<AccessibilityNodeInfo> clicks=new ArrayList<>();
    }
    private ScreenInfo inspect(AccessibilityNodeInfo root){
        ScreenInfo v=new ScreenInfo();
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        visit(root,nodes,0);
        boolean separacao=false,notas=false;
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser())continue;
            String t=tag(n);
            if(has(t,"bipar atividades de separacao","bipar atividade de separacao","bipar atividades"))v.hasActivitiesTitle=true;
            if(has(t,"nenhuma atividade registrada"))v.noActivities=true;
            if(has(t,"iniciar"))v.hasStart=true;
            if(has(t,"busca da conferencia de venda"))v.hasConferenceTitle=true;
            if(has(t,"separacao","separar"))separacao=true;
            if(has(t,"bipar nota","nota fiscal","conferencia de nota","bipagem de nota"))notas=true;
            if(has(t,"login","autenticacao","senha de acesso"))v.login=true;
            if(has(t,"atencao!","atividade cancelada","confirmar cancelamento"))v.dialog=true;
            if(n.isEditable() && !n.isPassword())v.editors.add(n);
            if(has(t,"bipe ou digite o codigo da atividade","digite o codigo da atividade"))v.searchField=n;
            if(n.isClickable() && n.isEnabled())v.clicks.add(n);
            if(!blockedLabel(t) && has(t,"teclado","keyboard","entrada manual","digitar codigo","inserir codigo","ler codigo")){
                AccessibilityNodeInfo candidate=clickable(n);
                if(candidate!=null){
                    if(v.keyboard==null)v.keyboard=candidate;
                    else if(!sameBounds(v.keyboard,candidate))v.keyboard=null;
                }
            }
        }
        if(v.hasConferenceTitle)v.screen="conferencia_venda";
        else if(v.hasActivitiesTitle || (v.noActivities && v.hasStart)){
            v.screen="atividades";
            v.hasActivitiesTitle=true;
        }
        else if(separacao)v.screen="separacao";
        else if(notas)v.screen="notas";
        if(v.login)v.screen="desconhecida";
        return v;
    }
    private void visit(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> dest,int depth){
        if(n==null || depth>40 || dest.size()>550)return;
        dest.add(n);
        for(int i=0;i<n.getChildCount();i++)visit(n.getChild(i),dest,depth+1);
    }
    private static AccessibilityNodeInfo clickable(AccessibilityNodeInfo n){
        if(n==null)return null;
        AccessibilityNodeInfo cursor=n;
        for(int j=0;j<4 && cursor!=null;j++,cursor=cursor.getParent()){
            if(cursor.isEditable() || cursor.isClickable() || cursor.isFocusable())return cursor;
        }
        return n;
    }
    private static boolean sameBounds(AccessibilityNodeInfo a,AccessibilityNodeInfo b){
        Rect r=new Rect(),s=new Rect();a.getBoundsInScreen(r);b.getBoundsInScreen(s);
        return Math.abs(r.centerX()-s.centerX())<18 && Math.abs(r.centerY()-s.centerY())<18;
    }
    private AccessibilityNodeInfo safeEditor(ScreenInfo info,boolean explicitlyOpened){
        List<AccessibilityNodeInfo> candidates=new ArrayList<>();
        for(AccessibilityNodeInfo e:info.editors){
            if(!e.isEnabled() || !e.isVisibleToUser() || e.isPassword())continue;
            String t=tag(e);
            if(blockedLabel(t))continue;
            boolean named=has(t,"codigo", "cod.", "barcode", "barras", "atividade", "bipe");
            // Em busca de conferencia ha uma unica caixa de busca no topo.
            // Em atividades a entrada foi aberta pelo icone reconhecido.
            boolean expected=explicitlyOpened && ("atividades".equals(info.screen) ||
                "conferencia_venda".equals(info.screen) || "notas".equals(info.screen));
            if(named || expected)candidates.add(e);
        }
        return candidates.size()==1 ? candidates.get(0) : null;
    }
    private AccessibilityNodeInfo relativeKeyboard(ScreenInfo info){
        // Preserva a heuristica de separacao da v0.5, desativada por padrao.
        if(!"separacao".equals(info.screen))return null;
        float density=getResources().getDisplayMetrics().density;
        int w=getResources().getDisplayMetrics().widthPixels;
        int h=getResources().getDisplayMetrics().heightPixels;
        class Entry{AccessibilityNodeInfo node;Rect rect;Entry(AccessibilityNodeInfo a,Rect b){node=a;rect=b;}}
        List<Entry> hits=new ArrayList<>();
        for(AccessibilityNodeInfo n:info.clicks){
            Rect r=new Rect();n.getBoundsInScreen(r);
            String t=tag(n);
            if(blockedLabel(t) || has(t,"camera","pular","encerrar","fechar","cancelar"))continue;
            if(r.centerY()<h*.50f || r.centerY()>h*.95f ||
               r.width()<40*density || r.width()>w*.27f ||
               r.height()<40*density || r.height()>108*density)continue;
            boolean duplicate=false;
            for(Entry candidate:hits){
                if(Math.abs(candidate.rect.centerX()-r.centerX())<18*density &&
                   Math.abs(candidate.rect.centerY()-r.centerY())<18*density)duplicate=true;
            }
            if(!duplicate)hits.add(new Entry(n,r));
        }
        if(hits.size()!=2)return null;
        Entry a=hits.get(0),b=hits.get(1);
        if(a.rect.centerX()>b.rect.centerX()){Entry x=a;a=b;b=x;}
        if(Math.abs(a.rect.centerY()-b.rect.centerY())>18*density)return null;
        return b.node;
    }
    @Override public void onInterrupt(){cancelPending();restoreKeyboard();status("Acessibilidade interrompida.");}
    @Override public void onDestroy(){
        if(receiverRegistered)try{unregisterReceiver(receiver);}catch(RuntimeException ignored){}
        main.removeCallbacksAndMessages(null);
        cancelPending();restoreKeyboard();
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
