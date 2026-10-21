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
 * do MLOG. Mantenha o Gboard visivel; foca campo e confirma com Enter apos preencher. Nao armazena codigos nem usa clipboard.
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
    private long lastEnterAt;
    private String lastEnterCode="";
    private String pendingCode;
    private long pendingDeadline;
    private int serial;
    private long lastDiagnostic;
    private boolean gestureInProgress;
    private long lastScanAt;
    private long lastAutoActionAt;
    // Preparacao ANTES da leitura: teclado do CMX pode enviar direto como teclas.
    private String preparedScreen="";
    private long preparedAt;
    private boolean preparing;
    private boolean prearmFallbackUsed;
    private long lastPrepareCheck;
    private final Runnable prearmRunnable=()->prepareReadingField();

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
            // Versoes anteriores podiam deixar o teclado oculto pelo controlador.
            restoreKeyboard();
            status("v0.9: Gboard visivel, foco por tela e confirmacao assistida.");
            main.postDelayed(prearmRunnable,600);
        }catch(RuntimeException e){status("Falha ao registrar o broadcast do scanner.");}
    }

    private SharedPreferences prefs(){return getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);}
    private void status(String s){ScanCoordinator.status(this,s);}
    // v0.9: teclado Gboard SEMPRE liberado. Nunca use SHOW_MODE_HIDDEN.
    private void restoreKeyboard(){
        try{getSoftKeyboardController().setShowMode(SHOW_MODE_AUTO);}
        catch(RuntimeException ignored){}

    }
    private void scheduleRestore(int id){ /* Nada a restaurar: Gboard sempre disponivel. */ }
    private void cancelPending(){pendingCode=null;pendingDeadline=0;gestureInProgress=false;}

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null)return;
        CharSequence pkg=event.getPackageName();
        if(pkg==null || !ScanCoordinator.TARGET.contentEquals(pkg)){
            // Eventos do Gboard nao indicam saida do MLOG: ainda pode estar
            // aberta a entrada de leitura por baixo do teclado virtual.
            if(pendingCode==null && mlogRoot()==null){
                preparedAt=0;preparedScreen="";
                prearmFallbackUsed=false;
            }
            return;
        }
        if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED ||
           event.getEventType()==AccessibilityEvent.TYPE_VIEW_FOCUSED){
            AccessibilityNodeInfo source=event.getSource();
            if(source!=null){
                String label=tag(source);
                if(blockedLabel(label)){
                    cancelPending();
                }
            }
        }
        long time=SystemClock.elapsedRealtime();
        if(pendingCode==null && preparedAt>0 &&
            event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            AccessibilityNodeInfo visible=mlogRoot();
            if(visible!=null){
                ScreenInfo info=inspect(visible);
                if("desconhecida".equals(info.screen) && info.editors.isEmpty()){
                    preparedAt=0;preparedScreen="";prearmFallbackUsed=false;
                }
            }
        }
        if(prefs().getBoolean("prearm_field",true) &&
           prefs().getBoolean("enabled",false) && pendingCode==null &&
           time-lastPrepareCheck>430){
            lastPrepareCheck=time;
            main.removeCallbacks(prearmRunnable);
            main.postDelayed(prearmRunnable,210);
        }
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
        if("desconhecida".equals(i.screen) && !i.login && !i.dialog &&
           i.editors.size()==1 && SystemClock.elapsedRealtime()-preparedAt<9000 &&
           ("atividades".equals(preparedScreen) || "consulta".equals(preparedScreen) ||
            "conferencia_venda".equals(preparedScreen) || "separacao".equals(preparedScreen) ||
            "pedidos".equals(preparedScreen) || "notas".equals(preparedScreen))){
            // Modal sem titulo do Flutter: somente editor unico do MLOG e
            // somente apos preparacao de uma tela reconhecida.
            i.screen=preparedScreen;
        }
        if(i.login || i.dialog || "desconhecida".equals(i.screen)){
            status("Leitura pausada: tela desconhecida ou aviso aberto.");return;
        }
        // Se o campo ja foi aberto/preparado, nao pressionar o icone novamente.
        AccessibilityNodeInfo ready=safeEditor(i,true);
        if(ready!=null){
            int id=++serial;
            focusAndSet(ready,code);
            scheduleRestore(id);
            return;
        }
        // Se acabamos de tocar um ícone guiado, não acioná-lo de novo no bipe:
        // em telas Flutter o segundo toque pode FECHAR o campo aberto.
        if(prearmFallbackUsed && i.screen.equals(preparedScreen) &&
           SystemClock.elapsedRealtime()-preparedAt<2500){
            startPending(code,i.screen);
            status("Campo ja acionado; aguardando editor sem segundo clique.");
            retryFill(serial,i.screen);
            return;
        }

        // Na tela de atividades, o botao de teclado e o portao de entrada.
        // PRIMEIRO abrimos o campo, depois preenchemos, sem ocultar o Gboard.
        if("atividades".equals(i.screen)){
            startPending(code,"atividades");
            AccessibilityNodeInfo button=activityKeyboard(i);
            if(button!=null && focusOrClick(button)){
                status("Atividades: botao de teclado acionado; aguardando editor.");
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
        if("conferencia_venda".equals(i.screen) || "consulta".equals(i.screen)){
            AccessibilityNodeInfo editor=safeEditor(i,true);
            if(editor!=null){
                int id=++serial;
                focusAndSet(editor,code);
                scheduleRestore(id);
                return;
            }
            startPending(code,i.screen);
            // Não ocultar IME antes do campo existir. O Flutter pode nao abrir.

            if(i.searchField!=null){
                AccessibilityNodeInfo target=clickable(i.searchField);
                boolean ok=focusOrClick(target);
                if(ok){status("Busca de conferencia selecionada; aguardando campo editavel.");
                    retryFill(serial,i.screen);return;}
            }
            if(prefs().getBoolean("guided_taps",true) &&
                (i.hasConferenceTitle || i.hasConsultation)){
                tapKnownSearchField(serial,i.screen);
            }else{
                cancelPending();
                status("Busca: campo nao acessivel. Ative toque assistido ou envie diagnostico.");
            }
            return;
        }

        AccessibilityNodeInfo editor=safeEditor(i,false);
        if(editor!=null){
            int id=++serial;
            focusAndSet(editor,code);
            scheduleRestore(id);
            return;
        }

        // Separacao, pedidos e notas: a entrada de codigo pode aparecer somente
        // apos o clique no icone do teclado. Quantidade NUNCA e elegivel.
        startPending(code,i.screen);
        // Ocultacao so acontece DEPOIS de o campo estar aberto.
        AccessibilityNodeInfo button=findKeyboardTarget(i);
        if(button!=null && focusOrClick(button)){
            status("Icone de teclado acionado. Aguardando campo com Gboard.");
            retryFill(serial,i.screen);
            return;
        }
        if("separacao".equals(i.screen) &&
                prefs().getBoolean("layout_fallback",true)){
            AccessibilityNodeInfo n=relativeKeyboard(i);
            if(n!=null && focusOrClick(n)){
                status("Botao de leitura de produto acionado por layout relativo.");
                retryFill(serial,i.screen);return;
            }
        }
        if("separacao".equals(i.screen) && prefs().getBoolean("guided_taps",true)
                && i.hasSeparationControls){
            startPendingIfNeeded(code,i.screen);
            tapKnownSeparationKeyboard(serial,i);return;
        }
        cancelPending();
        status("Icone de teclado nao identificado. Nenhuma acao arriscada executada.");
    }

    /** Prepara a entrada antes do gatilho, sem alterar o CMX.
     *  Limita toques por tela/periodo para evitar loop. */
    private void startPendingIfNeeded(String code,String screen){
        if(pendingCode==null)startPending(code,screen);
    }
    private void prepareReadingField(){
        if(preparing || pendingCode!=null || !prefs().getBoolean("prearm_field",true) ||
           !prefs().getBoolean("enabled",false))return;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo info=inspect(root);
        if(info.login || info.dialog || "desconhecida".equals(info.screen))return;
        String screen=info.screen;
        if(!screen.equals(preparedScreen)){
            preparedScreen=screen;
            preparedAt=0;
            prearmFallbackUsed=false;
        }
        // Nao repetir cliques na mesma tela apenas por passagem de tempo.
        // Flutter pode alternar abrir/fechar o modal se tocarmos de novo.
        // Liberar nova tentativa quando uma leitura conclui ou a tela muda.
        if(preparedAt>0)return;
        if(!("atividades".equals(screen) || "consulta".equals(screen) ||
             "conferencia_venda".equals(screen) || "separacao".equals(screen) ||
             "pedidos".equals(screen) || "notas".equals(screen)))return;
        AccessibilityNodeInfo editor=safeEditor(info,false);
        if(editor!=null){
            preparedAt=SystemClock.elapsedRealtime();
            preparedScreen=screen;
            try{
                if(!editor.isFocused()) editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                // Campo ja existe: manter o Gboard normal.

            }catch(RuntimeException ignored){}
            status("Campo de leitura preparado: "+screen);
            return;
        }
        // Só abrir por gesto nas telas cuja posição do ícone foi identificada.
        if(!prefs().getBoolean("guided_taps",true))return;
        if("atividades".equals(screen) && info.hasActivitiesTitle){
            preparedAt=SystemClock.elapsedRealtime();
            AccessibilityNodeInfo button=activityKeyboard(info);
            if(button!=null && focusOrClick(button)){
                status("Atividades: botao de entrada aberto antes do bipe.");
                main.postDelayed(()->postPrepareCheck("atividades"),320);
            }else{
                tapPrepare(screen,0.375f,0.888f);
            }
        }else if("consulta".equals(screen) && info.hasConsultation){
            preparedAt=SystemClock.elapsedRealtime();
            AccessibilityNodeInfo field=info.searchField;
            if(field!=null && focusOrClick(clickable(field))){
                status("Consulta: campo superior selecionado.");
                main.postDelayed(()->postPrepareCheck("consulta"),320);
            }else tapPrepare(screen,0.34f,0.090f);
        }else if("conferencia_venda".equals(screen) && info.hasConferenceTitle){
            preparedAt=SystemClock.elapsedRealtime();
            if(info.searchField!=null && focusOrClick(clickable(info.searchField)))
                status("Conferencia de venda: campo selecionado.");
            else tapPrepare(screen,0.34f,0.145f);
        }else if("separacao".equals(screen) || "pedidos".equals(screen) || "notas".equals(screen)){
            AccessibilityNodeInfo n=findKeyboardTarget(info);
            if(n!=null){preparedAt=SystemClock.elapsedRealtime();focusOrClick(n);}
            else if("separacao".equals(screen) && info.hasSeparationControls){
                preparedAt=SystemClock.elapsedRealtime();
                tapPrepare(screen,info.hasQuantity?0.47f:0.60f,0.603f);
            }
        }
    }

    private void postPrepareCheck(String screen){
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo info=inspect(root);
        if(!screen.equals(info.screen) && !"desconhecida".equals(info.screen))return;
        if(safeEditor(info,true)!=null){
            status("Campo pronto para leitura na tela: "+screen);
            // Nao ocultar teclado nem tocar novamente se ja existe editor.

        }else{
            // Se ACTION_CLICK foi aceito mas o campo nao abriu, testar
            // UMA vez a posicao homologada. Nunca encadear toques infinitos.
            if(screen.equals(info.screen) && !prearmFallbackUsed &&
               prefs().getBoolean("guided_taps",true)){
                if("atividades".equals(screen) && info.hasActivitiesTitle){
                    tapPrepare(screen,0.375f,0.888f);return;
                }
                if("separacao".equals(screen) && info.hasSeparationControls){
                    tapPrepare(screen,info.hasQuantity?0.47f:0.60f,0.603f);return;
                }
                if("consulta".equals(screen) && info.hasConsultation){
                    tapPrepare(screen,0.34f,0.090f);return;
                }
            }
            status("Campo Flutter nao exposto; pode exigir entrada nativa do CMX.");
        }
    }

    /** Gesto limitado a uma tela identificada, uma vez por visita; sem inserir texto. */
    private void tapPrepare(String screen,float nx,float ny){
        if(prearmFallbackUsed)return;
        prearmFallbackUsed=true;
        Point size=realScreenSize();
        if(!supportedAspect(size)){
            status("Tela nao homologada para toque automatico; verifique diagnóstico.");return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null || !screen.equals(inspect(root).screen))return;
        Path path=new Path();path.moveTo(size.x*nx,size.y*ny);
        GestureDescription.Builder builder=new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path,0,75));
        lastAutoActionAt=SystemClock.elapsedRealtime();
        try{
            if(!dispatchGesture(builder.build(),new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription g){
                    status("Botao de leitura tocado: "+screen+". Aguardando campo.");
                    main.postDelayed(()->postPrepareCheck(screen),320);
                }
                @Override public void onCancelled(GestureDescription g){
                    status("Android cancelou o toque de preparacao.");
                }
            },main))status("Android nao autorizou toque de preparacao.");
        }catch(RuntimeException e){status("Toque de preparacao indisponivel.");}
    }

    private void startPending(String code,String type){
        // O codigo so permanece na memoria ate concluir a tentativa.
        pendingCode=code;
        pendingDeadline=SystemClock.elapsedRealtime()+4700;
        ++serial;
    }
    private void retryFill(int id,String intendedScreen){
        for(int delay:new int[]{100,200,350,550,800,1100,1550,2050,2700,3400,4200,4700}){
            main.postDelayed(()->tryPending(id,intendedScreen),delay);
        }
        scheduleRestore(id);
    }
    private void tryPending(int id,String intendedScreen){
        if(id!=serial || pendingCode==null)return;
        if(SystemClock.elapsedRealtime()>pendingDeadline){
            cancelPending();
            status("O campo do Flutter nao apareceu na acessibilidade. Sem codigo enviado.");
            return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo i=inspect(root);
        if(i.login || i.dialog){
            cancelPending();
            status("Aviso/modal detectado: leitura cancelada para evitar erro.");return;
        }
        if(!intendedScreen.equals(i.screen)){
            // Modais Flutter podem abrir uma janela própria sem o título da
            // página anterior. Aceitamos SOMENTE um editor único, na janela
            // do MLOG, após nosso clique na busca ou no botão de atividades.
            if(!"desconhecida".equals(i.screen) || i.editors.size()!=1 ||
                !("atividades".equals(intendedScreen) ||
                  "conferencia_venda".equals(intendedScreen) ||
                  "consulta".equals(intendedScreen) || "separacao".equals(intendedScreen) ||
                  "pedidos".equals(intendedScreen) || "notas".equals(intendedScreen)))return;
            i.screen=intendedScreen;
        }
        AccessibilityNodeInfo editor=safeEditor(i,true);
        if(editor==null)return;
        String code=pendingCode;
        cancelPending();
        // O teclado deve permanecer disponivel; ele pode ser necessario ao Flutter.
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
            if(code.equals(existing.toString().trim())){
                status("CMX ja preencheu o codigo; aguardando confirmacao nativa.");
                if(prefs().getBoolean("send_enter",true))
                    main.postDelayed(()->confirmEnteredCode(code),300);
            }else status("Campo ocupado: nao sobrescrevi texto existente.");
            return;
        }
        try{
            // O Flutter por vezes abre o teclado so com clique (foco isolado nao basta).
            if(!editor.isFocused()){
                if(editor.isClickable()) editor.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            }
            if(!supports(editor,AccessibilityNodeInfo.ACTION_SET_TEXT)){
                status("Flutter nao expos ACTION_SET_TEXT; codigo nao enviado.");return;
            }
            Bundle data=new Bundle();
            data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,code);
            boolean done=editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data);
            if(!done){status("MLOG recusou preencher o campo de leitura.");return;}
            status("Leitura inserida no campo do MLOG; codigo nao foi registrado.");
            main.postDelayed(()->{
                preparedAt=0;
                prearmFallbackUsed=false;
            },1800);
            if(prefs().getBoolean("send_enter",true)){
                // O CMX pode mandar Enter nativamente, porém ANTES de SET_TEXT.
                // Aguardar para dar chance ao MLOG processar Enter sem duplicar.
                final String expected=code;
                main.postDelayed(()->confirmEnteredCode(expected),260);
            }

        }catch(RuntimeException e){status("Falha de acessibilidade ao inserir o codigo.");}
    }
    /** Confirma UMA vez e somente se o mesmo codigo continua no editor do MLOG.
     * Se o CMX confirmou sozinho e o campo foi limpo/fechado, nao duplica Enter.
     * Nao simula botoes de Iniciar/Quantidade/Cancelar. */
    private void confirmEnteredCode(String expected){
        if(!ScanCoordinator.isActive(this) || mlogRoot()==null)return;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo info=inspect(root);
        if(info.login || info.dialog)return;
        AccessibilityNodeInfo editor=safeEditor(info,true);
        if(editor==null){
            // Em modais Flutter o titulo pode sumir; unico campo editavel e focado.
            if(info.editors.size()==1 && info.editors.get(0).isFocused() &&
                !blockedLabel(tag(info.editors.get(0))))editor=info.editors.get(0);
        }
        if(editor==null || !editor.isVisibleToUser() || editor.isPassword()){
            status("Campo mudou apos leitura: Enter nativo possivelmente processado.");return;
        }
        CharSequence current=editor.getText();
        if(current==null || !expected.equals(current.toString().trim())){
            status("Campo ja mudou: Enter adicional dispensado.");return;
        }
        long now=SystemClock.elapsedRealtime();
        if(expected.equals(lastEnterCode) && now-lastEnterAt<1100)return;
        lastEnterAt=now;lastEnterCode=expected;
        int action=AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId();
        try{
            if(editor.performAction(action)){
                status("Codigo preenchido e Enter do MLOG acionado.");return;
            }
        }catch(RuntimeException ignored){}
        // Fallback seguro: tecla Enter/Ir/Buscar do teclado Android visivel.
        if(pressVisibleImeEnter()){
            status("Codigo preenchido e Enter do teclado acionado.");return;
        }
        status("Codigo preenchido, mas MLOG/Gboard nao disponibilizaram Enter. Envie diagnostico.");
    }
    private boolean pressVisibleImeEnter(){
        try{
            for(AccessibilityWindowInfo window:getWindows()){
                if(window.getType()!=AccessibilityWindowInfo.TYPE_INPUT_METHOD)continue;
                AccessibilityNodeInfo root=window.getRoot();
                if(root==null)continue;
                Rect wr=new Rect();window.getBoundsInScreen(wr);
                List<AccessibilityNodeInfo> nodes=new ArrayList<>();visit(root,nodes,0);
                AccessibilityNodeInfo chosen=null;
                for(AccessibilityNodeInfo n:nodes){
                    String label=tag(n).trim();
                    if(!has(label,"enter","retorno","pesquisar","buscar","concluido","concluir","done","ir para"))continue;
                    if(has(label,"cancelar","limpar","apagar","excluir","voltar"))continue;
                    Rect nr=new Rect();n.getBoundsInScreen(nr);
                    if(nr.isEmpty() || nr.centerX() < wr.left+wr.width()*.70f ||
                       nr.centerY() < wr.top+wr.height()*.50f || !n.isVisibleToUser())continue;
                    AccessibilityNodeInfo clickableNode=clickable(n);
                    if(clickableNode!=null && clickableNode.isClickable()){
                        Rect targetBounds=new Rect();clickableNode.getBoundsInScreen(targetBounds);
                        if(targetBounds.width()>wr.width()*.34f ||
                           targetBounds.height()>wr.height()*.42f ||
                           targetBounds.centerX() < wr.left+wr.width()*.70f)continue;
                        if(chosen!=null && !sameBounds(chosen,clickableNode))return false;
                        chosen=clickableNode;
                    }
                }
                if(chosen!=null && chosen.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
            }
        }catch(RuntimeException ignored){}
        return false;
    }
    private static boolean supports(AccessibilityNodeInfo n,int action){
        for(AccessibilityNodeInfo.AccessibilityAction a:n.getActionList())
            if(a.getId()==action)return true;
        return false;
    }
    private static boolean focusOrClick(AccessibilityNodeInfo n){
        if(n==null || !n.isEnabled() || !n.isVisibleToUser())return false;
        try{
            if(n.isEditable()){
                if(n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
                if(n.isFocusable() && n.performAction(AccessibilityNodeInfo.ACTION_FOCUS))return true;
            }
            if(n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
            if(n.isFocusable() && n.performAction(AccessibilityNodeInfo.ACTION_FOCUS))return true;
        }catch(RuntimeException ignored){}
        return false;
    }

    /** Preferir rotulo acessivel; se nao houver, par de botoes de camera/teclado.
     * Nao usar clique em quantidade, iniciar ou acao sem identificacao suficiente. */
    private AccessibilityNodeInfo findKeyboardTarget(ScreenInfo info){
        if(info.keyboard!=null){
            AccessibilityNodeInfo target=clickable(info.keyboard);
            if(target!=null && !blockedLabel(tag(target))){
                Rect bounds=new Rect();target.getBoundsInScreen(bounds);
                Point screen=realScreenSize();
                if(bounds.width()<screen.x*.30f && bounds.height()<screen.y*.18f)
                    return target;
            }
        }
        if("atividades".equals(info.screen))return activityKeyboard(info);
        if("separacao".equals(info.screen)){
            AccessibilityNodeInfo target=relativeKeyboard(info);
            if(target!=null)return target;
        }
        // Alguns widgets Flutter sao anunciados apenas como 'camera' ou 'foto'.
        // Encontrar camera e imediatamente ao lado UM unico controle candidato.
        if(!("pedidos".equals(info.screen) || "notas".equals(info.screen) ||
             "separacao".equals(info.screen)))return null;
        AccessibilityNodeInfo camera=null;
        for(AccessibilityNodeInfo n:info.clicks){
            if(has(tag(n),"camera","câmera","fotografar")){
                if(camera!=null && !sameBounds(camera,n))return null;
                camera=n;
            }
        }
        if(camera==null)return null;
        Rect cr=new Rect();camera.getBoundsInScreen(cr);
        AccessibilityNodeInfo candidate=null;
        for(AccessibilityNodeInfo n:info.clicks){
            if(n==camera || blockedLabel(tag(n)))continue;
            Rect r=new Rect();n.getBoundsInScreen(r);
            if(r.centerX()<=cr.centerX()+cr.width()/2 ||
                r.centerX()>cr.centerX()+cr.width()*3.2f ||
                Math.abs(r.centerY()-cr.centerY())>Math.max(cr.height(),r.height())*.50f ||
                r.width()<cr.width()*.45f)continue;
            if(candidate!=null && !sameBounds(candidate,n))return null;
            candidate=n;
        }
        return candidate;
    }
    private void tapKnownSeparationKeyboard(int id,ScreenInfo info){
        Point size=realScreenSize();
        if(!supportedAspect(size) || !info.hasSeparationControls){
            cancelPending();status("Tela de pedido nao homologada para toque guiado.");return;
        }
        // Fotos 720x1440: teclado entre camera e quantidade OU camera e margem.
        // Fallback SOMENTE com textos Pular Item + Encerrar Item reconhecidos.
        tapAt(id,size.x*(info.hasQuantity?0.47f:0.60f),size.y*.603f,"separacao");
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

    private void tapKnownSearchField(int id,String screen){
        // Captura da tela enviada pelo usuario: entrada a ~35% da largura e
        // ~14,4% da altura. Nunca aplicar esse gesto fora da tela nomeada.
        Point size=realScreenSize();
        if(!supportedAspect(size)){
            cancelPending();status("Resolucao nao homologada para toque assistido; envie diagnostico.");return;
        }
        boolean consultation="consulta".equals(screen);
        status("Busca: tentando focar o campo superior por posicao relativa.");
        tapAt(id,size.x*0.34f,
             size.y*(consultation?0.090f:0.145f),screen);
    }
    private void tapKnownActivitiesKeyboard(int id){
        // Captura 720x1440 e video do coletor: botao direito do par camera /
        // teclado junto a borda inferior; x~37.5%, y~88.8% da tela fisica.
        // Executa SOMENTE se o titulo de atividades estiver acessivel.
        Point size=realScreenSize();
        if(!supportedAspect(size)){
            cancelPending();status("Resolucao nao homologada para toque assistido; envie diagnostico.");return;
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
            ("conferencia_venda".equals(screen) && !before.hasConferenceTitle) ||
            ("consulta".equals(screen) && !before.hasConsultation)){
            cancelPending();status("Tela mudou: gesto cancelado.");return;
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
        boolean login,hasActivitiesTitle,hasConferenceTitle,hasConsultation,dialog,noActivities,hasStart,hasQuantity,hasSeparationControls,hasOrderTitle;
        AccessibilityNodeInfo keyboard,searchField;
        final List<AccessibilityNodeInfo> editors=new ArrayList<>();
        final List<AccessibilityNodeInfo> clicks=new ArrayList<>();
    }
    private ScreenInfo inspect(AccessibilityNodeInfo root){
        ScreenInfo v=new ScreenInfo();
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        visit(root,nodes,0);
        boolean separacao=false,notas=false,pedidos=false,consultationHint=false,consultationTitle=false;
        boolean hasPular=false,hasEncerrar=false;
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser())continue;
            String t=tag(n);
            if(has(t,"bipar atividades de separacao","bipar atividade de separacao","bipar atividades"))v.hasActivitiesTitle=true;
            if(has(t,"nenhuma atividade registrada"))v.noActivities=true;
            if(has(t,"iniciar"))v.hasStart=true;
            if(has(t,"busca da conferencia de venda"))v.hasConferenceTitle=true;
            if(has(t,"bipe ou digite enderec", "bipe algum produto", "local de estoque"))consultationHint=true;
            if(has(t,"consulta"))consultationTitle=true;
            if(has(t,"bipe ou digite enderec"))v.searchField=n;
            if(has(t,"separacao","separar"))separacao=true;
            if(has(t,"pular item"))hasPular=true;
            if(has(t,"encerrar item"))hasEncerrar=true;
            if(has(t,"quantidade:"))v.hasQuantity=true;
            if(has(t,"bipar pedido","bipar pedidos","pedido de separacao","bipagem de pedidos"))pedidos=true;
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
        v.hasConsultation=consultationHint && consultationTitle;
        v.hasSeparationControls=hasPular && hasEncerrar;
        if(v.hasConferenceTitle)v.screen="conferencia_venda";
        else if(v.hasConsultation)v.screen="consulta";
        else if(v.hasActivitiesTitle || (v.noActivities && v.hasStart)){
            v.screen="atividades";
            v.hasActivitiesTitle=true;
        }
        else if(pedidos)v.screen="pedidos";
        else if(separacao || v.hasSeparationControls)v.screen="separacao";
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
            boolean named=has(t,"codigo", "cod.", "barcode", "barras", "atividade", "bipe", "enderec", "produto");
            // Em busca de conferencia ha uma unica caixa de busca no topo.
            // Em atividades a entrada foi aberta pelo icone reconhecido.
            boolean expected=explicitlyOpened && ("atividades".equals(info.screen) ||
                "conferencia_venda".equals(info.screen) || "consulta".equals(info.screen) ||
                "notas".equals(info.screen) || "pedidos".equals(info.screen) ||
                "separacao".equals(info.screen));
            if(named || expected)candidates.add(e);
        }
        return candidates.size()==1 ? candidates.get(0) : null;
    }
    private AccessibilityNodeInfo relativeKeyboard(ScreenInfo info){
        // Heuristica de separacao para botoes lado a lado. Recomenda diagnostico se falhar.
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
        cancelPending();
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
