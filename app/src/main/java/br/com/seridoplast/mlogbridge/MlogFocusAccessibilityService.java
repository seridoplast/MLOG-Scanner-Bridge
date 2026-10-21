package br.com.seridoplast.mlogbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.InputMethod;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.inputmethod.EditorInfo;
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
import android.util.Log;
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
    // Confirmar uma vez POR LEITURA, nunca bloquear pelo valor do codigo.
    private long enterAttempt;
    // Cada leitura possui a sua propria confirmacao. O CMX continua sendo a
    // fonte principal do Enter; o Bridge apenas recupera se ele se perdeu.
    private String lastEnteredByBridge="";
    private String lastEnteredScreen="";
    private String pendingCode;
    private long pendingDeadline;
    private int serial;
    // Uma leitura em processamento, com fila limitada para bipagens seguidas.
    // A fila vive apenas em memoria, sem persistir codigos.
    // Sem limite TOTAL de leituras durante a sessao. Limite apenas de
    // leituras SIMULTANEAMENTE pendentes para evitar OOM em erro prolongado.
    private final ScanQueue scanQueue=new ScanQueue(128);
    private long totalFinished;
    private int workGeneration;
    private long lastEventProbe;
    private long lastEnterEventProbe;
    private long currentScanStartedAt;
    private boolean awaitingEnterConfirmation;
    private boolean configuredFastEvents;
    private String pendingScreen="";
    private long lastDiagnostic;
    private boolean gestureInProgress;
    private long lastScanAt;
    private long lastAutoActionAt;
    private long navigationGuardUntil;
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
                      AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                      AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR;
            configuredFastEvents=fastImeMode();
            si.notificationTimeout=configuredFastEvents?50:180;
            setServiceInfo(si);
        }
        receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context context,Intent intent){
                String code=ScannerProtocol.barcode(intent);
                if(!ScanCoordinator.isActive(MlogFocusAccessibilityService.this))return;
                // Logar apenas a ORIGEM do broadcast, nunca o codigo lido.
                final String action=intent==null?null:intent.getAction();
                final String source=ScannerProtocol.ACTION_CMX.equals(action)?"CMX":
                        ScannerProtocol.ACTION_HIK.equals(action)?"HIK":
                        ScannerProtocol.ACTION_GENERIC.equals(action)?"GENERIC":"UNKNOWN";
                if(!ScanCoordinator.accept(code,fastImeMode())){
                    if(code!=null && !code.isEmpty()) trace("BROADCAST_SUPPRESSED_"+source);
                    return;
                }
                trace("BROADCAST_ACCEPTED_"+source);
                enqueueBarcode(code);
            }
        };
        IntentFilter filter=new IntentFilter();
        for(String action:ScannerProtocol.ACTIONS)filter.addAction(action);
        try{
            if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
            else registerReceiver(receiver,filter);
            receiverRegistered=true;
            // Nunca preparar/clicar na tela sem receber uma leitura.
            prefs().edit().putBoolean("prearm_field",false).apply();
            // Versoes anteriores podiam deixar o teclado oculto pelo controlador.
            restoreKeyboard();
            status("v2.1: modo rapido com protecao contra leitura duplicada; CMX somente leitura.");
        }catch(RuntimeException e){status("Falha ao registrar o broadcast do scanner.");}
    }

    private SharedPreferences prefs(){return getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);}
    /** So acelera quando o operador optou pelo teclado proprio E pelo modo rapido. */
    private boolean fastImeMode(){
        return prefs().getBoolean("experimental_bridge_ime",false) &&
               prefs().getBoolean("fast_ime_mode",false);
    }
    /** O usuario pode alternar sem reiniciar a acessibilidade. */
    void refreshEventThrottle(){
        boolean fast=fastImeMode();
        if(fast==configuredFastEvents)return;
        AccessibilityServiceInfo info=getServiceInfo();
        if(info==null)return;
        info.notificationTimeout=fast?50:180;
        try{
            setServiceInfo(info);
            configuredFastEvents=fast;
        }catch(RuntimeException ex){trace("EVENT_THROTTLE_UNAVAILABLE");}
    }
    private void status(String s){ScanCoordinator.status(this,s);}
    // v0.9: teclado Gboard SEMPRE liberado. Nunca use SHOW_MODE_HIDDEN.
    private void restoreKeyboard(){
        try{getSoftKeyboardController().setShowMode(SHOW_MODE_AUTO);}
        catch(RuntimeException ignored){}

    }
    private void scheduleRestore(int id){ /* Nada a restaurar: Gboard sempre disponivel. */ }
    private void cancelPending(){pendingCode=null;pendingDeadline=0;pendingScreen="";gestureInProgress=false;}

    private void trace(String stage){
        // Nenhum codigo de barras, dado de usuario ou texto de campo no log.
        Log.i("MLOGBridge","elapsed="+SystemClock.elapsedRealtime()+
                " seq="+workGeneration+" stage="+stage);
    }

    /** Nao descartar o proximo gatilho enquanto o editor ainda abre. */
    private void enqueueBarcode(String code){
        if(!ScanCoordinator.isActive(this))return;
        refreshEventThrottle();
        if(mlogRoot()==null){status("Leitura ignorada: MLOG nao esta ativo.");return;}
        long now=SystemClock.elapsedRealtime();
        if(now<navigationGuardUntil){
            trace("SCAN_DURING_NAVIGATION_IGNORED");
            status("Janela de informacoes/cancelamento: leitura nao executada.");
            return;
        }
        AccessibilityNodeInfo currentRoot=mlogRoot();
        if(currentRoot==null || isBlockingOverlay(currentRoot)){
            trace("SCAN_BLOCKED_BY_OVERLAY");
            status("Feche informacoes/aviso antes da proxima leitura.");
            return;
        }
        boolean idle=scanQueue.isIdle();
        if(!scanQueue.offer(code)){
            status("Excesso de leituras simultaneas: confirme a tela antes de prosseguir.");
            trace("QUEUE_FULL");return;
        }
        trace(idle?"SCAN_RECEIVED":"SCAN_QUEUED");
        if(idle)startQueuedScan();
        else status("Leitura recebida durante a anterior; aguardando na fila.");
    }
    private void startQueuedScan(){
        String code=scanQueue.active();
        if(code==null)return;
        final int epoch=++workGeneration;
        currentScanStartedAt=SystemClock.elapsedRealtime();
        trace("SCAN_START");
        onBarcode(code);
        // Fail-safe: nunca manter a fila bloqueada indefinidamente.
        main.postDelayed(()->{
            if(workGeneration!=epoch || scanQueue.isIdle())return;
            trace("SCAN_TIMEOUT");
            status("Leitura nao concluida no prazo; confira MLOG antes de repetir.");
            finishQueuedScan("timeout");
        },5400);
    }
    private void finishQueuedScan(String reason){
        if(scanQueue.isIdle())return;
        trace("SCAN_FINISH_"+reason);
        awaitingEnterConfirmation=false;
        directEnterSent=false;
        trace("SCAN_ELAPSED_MS_"+(SystemClock.elapsedRealtime()-currentScanStartedAt));
        totalFinished++;
        // Contador acumulado sem armazenar historico/codigo no disco.
        if(totalFinished%50==0)trace("SCANS_COMPLETED_BATCH_50");
        cancelPending();
        preparedAt=0;
        preparedScreen="";
        prearmFallbackUsed=false;
        ++serial; // cancelar tentativas de foco da leitura anterior
        ++enterAttempt; // cancelar callbacks de Enter da leitura anterior
        boolean more=scanQueue.complete();
        ++workGeneration; // invalida timeout antigo
        if(more){
            // Breve intervalo para o Flutter recompor a arvore apos Enter.
            // O Flutter precisa reconstruir a arvore. Aguardar so um frame
            // curto; cada nova leitura voltara a verificar campo/foco.
            main.postDelayed(this::startQueuedScan,fastImeMode()?25:65);
        }
    }


    /** Cancela operacoes em andamento quando o operador abre detalhes,
     *  cancelamento ou um dialogo. Nunca retoma gestos pendentes sozinho. */
    private void suspendForNavigation(String reason){
        boolean busy=!scanQueue.isIdle() || pendingCode!=null;
        navigationGuardUntil=SystemClock.elapsedRealtime()+350;
        if(!busy)return;
        trace("NAVIGATION_CANCEL_"+reason);
        ++serial;
        ++enterAttempt;
        ++workGeneration;
        awaitingEnterConfirmation=false;
        directEnterSent=false;
        cancelPending();
        scanQueue.clear();
        preparedAt=0;
        preparedScreen="";
        prearmFallbackUsed=false;
        status("Operacao cancelada ao abrir informacoes/aviso. Novo bipe somente apos fechar.");
    }

    /** Flutter pode desenhar dialogs no mesmo TYPE_APPLICATION. Essa guarda
     *  complementa o titulo detectado em inspect(), sem bloquear a pesquisa
     *  normal que possui a secao 'Informacoes Gerais' recolhida. */
    private boolean isBlockingOverlay(AccessibilityNodeInfo root){
        if(root==null)return true;
        ScreenInfo info=inspect(root);
        if(info.dialog || info.login)return true;
        CharSequence rootClass=root.getClassName();
        if(rootClass!=null &&
           (has(norm(rootClass),"dialog","popupwindow")))return true;
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        visit(root,nodes,0);
        boolean modalTitle=false,close=false;
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser())continue;
            String name=tag(n).trim();
            if(has(name,"informacoes do item", "informacoes do produto",
                    "detalhes do item", "detalhes do produto",
                    "cancelar separacao", "confirmar cancelamento",
                    "deseja cancelar", "cancelar atividade"))modalTitle=true;
            if(has(name,"fechar", "dispensar", "fechar janela", "voltar a separacao"))close=true;
        }
        return modalTitle && close;
    }

    private boolean isNavigationClick(AccessibilityNodeInfo source){
        String label=tag(source).trim();
        if(has(label,"informacoes", "informacao", "detalhes", "cancelar",
                "voltar", "fechar", "dismiss", "cancelamento"))return true;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return false;
        ScreenInfo i=inspect(root);
        if(!"separacao".equals(i.screen) || !i.hasSeparationControls)return false;
        Rect r=new Rect();source.getBoundsInScreen(r);
        Point p=realScreenSize();
        if(r.isEmpty() || r.width()>p.x*.25f)return false;
        float cx=(float)r.centerX()/p.x,cy=(float)r.centerY()/p.y;
        // Icone circular (i) na parte superior direita do cartao do item.
        return cx>.77f && cx<.97f && cy>.15f && cy<.42f;
    }

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
        if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED){
            AccessibilityNodeInfo source=event.getSource();
            if(source!=null && isNavigationClick(source)){
                suspendForNavigation("user_click");
                return;
            }
        }
        // Verificacao custosa da arvore somente durante uma leitura ativa.
        // Sem leitura, a proxima tentativa ja verifica a tela antes de agir.
        if((event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType()==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) &&
           (!scanQueue.isIdle() || pendingCode!=null)){
            AccessibilityNodeInfo visible=mlogRoot();
            if(visible!=null && isBlockingOverlay(visible)){
                suspendForNavigation("overlay");
                return;
            }
        }
        if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_FOCUSED){
            AccessibilityNodeInfo source=event.getSource();
            if(source!=null && blockedLabel(tag(source)) &&
               pendingCode!=null){
                // Quantidade ou outro campo nao deve receber codigo.
                suspendForNavigation("other_field");
                return;
            }
        }
        long time=SystemClock.elapsedRealtime();
        // Quando Flutter finalmente expuser o EditText, reagir ao evento.
        // Diminui espera da primeira leitura sem disparar outro clique.
        if(pendingCode!=null && !pendingScreen.isEmpty() && time-lastEventProbe>75){
            lastEventProbe=time;
            final int token=serial;
            final String screen=pendingScreen;
            main.postDelayed(()->tryPending(token,screen),65);
        }
        // Depois do Enter, o Flutter pode limpar o campo em um evento anterior
        // ao proximo timer. Verificar rapidamente para liberar a fila, sem
        // enviar outro Enter. A confirmacao so acontece via estado validado.
        if(awaitingEnterConfirmation && directEnterSent && pendingCode==null &&
            !scanQueue.isIdle() &&
            (event.getEventType()==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
             event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
             event.getEventType()==AccessibilityEvent.TYPE_VIEW_FOCUSED)){
            if(time-lastEnterEventProbe>35){
                lastEnterEventProbe=time;
                final long attempt=enterAttempt;
                final String expected=lastEnteredByBridge;
                if(!expected.isEmpty())
                    main.postDelayed(()->confirmEnteredCode(expected,attempt),18);
            }
        }
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
        // v1.4: nenhum clique por simples mudanca de tela. Cada gesto do MLOG
        // deve estar vinculado a uma leitura efetivamente recebida do CMX.
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
        if(pendingCode!=null){status("Leitura anterior aguardando campo; mantendo fila.");return;}
        // Nao cancelar a confirmacao anterior simplesmente porque chegou
        // outro broadcast. A confirmacao nova sera identificada no queueEnter.
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null){status("Ignorado: MLOG nao esta na tela ativa.");finishQueuedScan("fora_do_mlog");return;}
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
        if(i.login || i.dialog || isBlockingOverlay(root) ||
           "desconhecida".equals(i.screen)){
            status("Leitura pausada: tela desconhecida ou aviso aberto.");finishQueuedScan("tela_bloqueada");return;
        }
        // Diferenciar um editor QUALQUER do campo efetivamente aberto pelo
        // botao de teclado do MLOG. Antes, um editor desfocado bloqueava o
        // clique no botao e somente fazia o Gboard aparecer.
        AccessibilityNodeInfo ready=safeEditor(i,true);
        if(ready!=null && nativeEntryReady(i,ready)){
            if(("consulta".equals(i.screen) || "conferencia_venda".equals(i.screen))
                    && !ready.isFocused()){
                // Consulta apos resultado: Flutter deixa o campo visivel, mas
                // sem foco. Focar, esperar a transicao e so entao preencher.
                startPending(code,i.screen);
                focusOrClick(ready);
                retryFill(serial,i.screen);
                return;
            }
            ++serial;
            focusAndSet(ready,code);
            return;
        }
        // Quando ja acionamos o botao MLOG nesta tela, evitar um segundo
        // clique que poderia alternar o modal ou atingir a tecla zero.
        if(preparedAt>0 && i.screen.equals(preparedScreen) &&
           SystemClock.elapsedRealtime()-preparedAt<1100){
            startPending(code,i.screen);
            status("Campo ja acionado; aguardando editor sem segundo clique.");
            retryFill(serial,i.screen);
            return;
        }

        // Na tela de atividades, o botao de teclado e o portao de entrada.
        // PRIMEIRO abrimos o campo, depois preenchemos, sem ocultar o Gboard.
        if("atividades".equals(i.screen)){
            startPending(code,"atividades");
            // Um teclado numerico ja aberto pode ter 0 no local do icone.
            // Neste caso so tentar preencher o editor; jamais tocar posicao.
            if(isImeVisible()){
                retryFill(serial,"atividades");
                status("Atividades: entrada ja aberta; aguardando campo editavel.");return;
            }
            AccessibilityNodeInfo button=activityKeyboard(i);
            if(button!=null && focusOrClick(button)){
                status("Atividades: botao de teclado acionado; aguardando editor.");
                retryFill(serial,"atividades");return;
            }
            if(i.hasActivitiesTitle && prefs().getBoolean("guided_taps",true)){
                tapKnownActivitiesKeyboard(serial);return;
            }
            finishQueuedScan("icone_atividade_ausente");
            status("Atividades: icone nao exposto; ative toque guiado no Bridge.");
            return;
        }

        // A busca de conferencia possui uma caixa visivel na parte superior.
        // Portanto NAO se deve clicar no botao de teclado/teclar por cima.
        if("conferencia_venda".equals(i.screen) || "consulta".equals(i.screen)){
            AccessibilityNodeInfo editor=safeEditor(i,true);
            if(editor!=null && editor.isFocused()){
                ++serial;
                focusAndSet(editor,code);
                return;
            }
            startPending(code,i.screen);
            if(editor!=null){
                // Novo bipe = reabrir/selecionar busca, sem depender do toque
                // manual apos o resultado da consulta anterior.
                focusOrClick(editor);
                retryFill(serial,i.screen);
                return;
            }
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
                finishQueuedScan("busca_nao_acessivel");
                status("Busca: campo nao acessivel. Ative toque assistido ou envie diagnostico.");
            }
            return;
        }

        AccessibilityNodeInfo editor=safeEditor(i,false);
        if(editor!=null && nativeEntryReady(i,editor)){
            int id=++serial;
            focusAndSet(editor,code);
            scheduleRestore(id);
            return;
        }

        // Separacao, pedidos e notas: acionar o botao DO MLOG primeiro,
        // jamais simplesmente mandar mostrar teclado do sistema.
        startPending(code,i.screen);
        // Esta e a tela do print do usuario: o botao nativo de teclado e
        // clicado fisicamente (dispatchGesture), NAO usando SHOW_SOFT_INPUT.
        // A acao direta evita que ACTION_FOCUS abra apenas o Gboard.
        // Primeiro o botao semantico do MLOG; isso evita acertar Quantidade
        // caso o layout mude. Coordenadas ficam como ultimo recurso.
        AccessibilityNodeInfo button=findKeyboardTarget(i);
        if("separacao".equals(i.screen) && button!=null &&
            focusOrClick(button)){
            markNativeButton(i.screen,"botao identificado no MLOG");
            retryFill(serial,i.screen);
            scheduleNativeFallback(serial,i.screen);
            return;
        }
        if("separacao".equals(i.screen) && i.hasSeparationControls &&
           prefs().getBoolean("guided_taps",true) && supportedAspect(realScreenSize())){
            status("Separacao: toque no icone proprio do MLOG (layout conhecido).");
            tapKnownSeparationKeyboard(serial,i);
            return;
        }
        if(button!=null && focusOrClick(button)){
            markNativeButton(i.screen,"acessibilidade");
            retryFill(serial,i.screen);
            scheduleNativeFallback(serial,i.screen);
            return;
        }
        if("separacao".equals(i.screen) &&
                prefs().getBoolean("layout_fallback",true)){
            AccessibilityNodeInfo n=relativeKeyboard(i);
            if(n!=null && focusOrClick(n)){
                markNativeButton(i.screen,"par camera/teclado");
                retryFill(serial,i.screen);
                scheduleNativeFallback(serial,i.screen);return;
            }
        }
        if("separacao".equals(i.screen) && prefs().getBoolean("guided_taps",true)
                && i.hasSeparationControls){
            startPendingIfNeeded(code,i.screen);
            tapKnownSeparationKeyboard(serial,i);return;
        }
        finishQueuedScan("icone_ausente");
        status("Icone de teclado nao identificado. Nenhuma acao arriscada executada.");
    }

    /** Prepara a entrada antes do gatilho, sem alterar o CMX.
     *  Limita toques por tela/periodo para evitar loop. */
    private void startPendingIfNeeded(String code,String screen){
        if(pendingCode==null)startPending(code,screen);
    }
    // O operador deve iniciar cada ciclo pelo gatilho. Desativado inclusive
    // se preferencias antigas tinham prearm_field=true.
    private void prepareReadingField(){
        // Intencionalmente vazio: nenhuma abertura de teclado ao navegar.
    }

    private void postPrepareCheck(String screen){
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo info=inspect(root);
        if(!screen.equals(info.screen) && !"desconhecida".equals(info.screen))return;
        AccessibilityNodeInfo ready=safeEditor(info,true);
        if(ready!=null && nativeEntryReady(info,ready)){
            status("Campo MLOG pronto para leitura: "+screen);
            // Nao ocultar teclado nem tocar novamente se ja existe editor.

        }else{
            // Se ACTION_CLICK foi aceito mas o campo nao abriu, testar
            // UMA vez a posicao homologada. Nunca encadear toques infinitos.
            if(screen.equals(info.screen) && !prearmFallbackUsed &&
               !isImeVisible() && prefs().getBoolean("guided_taps",true)){
                if("separacao".equals(screen) && info.hasSeparationControls){
                    tapPrepare(screen,info.hasQuantity?0.44f:0.60f,0.603f);return;
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
        if(isImeVisible()){
            status("IME aberto: nenhum novo toque na tela para evitar digitar 0.");return;
        }
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
        pendingDeadline=SystemClock.elapsedRealtime()+3400;
        pendingScreen=type;
        ++serial;
        trace("WAIT_EDITOR_"+type);
    }
    private void retryFill(int id,String intendedScreen){
        for(int delay:new int[]{90,170,280,430,610,850,1150,1500,2000,2700,3100}){
            main.postDelayed(()->tryPending(id,intendedScreen),delay);
        }
        scheduleRestore(id);
    }
    private void tryPending(int id,String intendedScreen){
        if(id!=serial || pendingCode==null)return;
        if(SystemClock.elapsedRealtime()>pendingDeadline){
            finishQueuedScan("editor_nao_apareceu");
            status("O campo do Flutter nao apareceu na acessibilidade. Sem codigo enviado.");
            return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo i=inspect(root);
        if(i.login || i.dialog || isBlockingOverlay(root)){
            suspendForNavigation("editor_modal");
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
        if(editor==null || !nativeEntryReady(i,editor))return;
        if(("consulta".equals(intendedScreen) || "conferencia_venda".equals(intendedScreen))
                && !editor.isFocused()){
            focusOrClick(editor);
            return; // proxima tentativa agendada aguarda foco Flutter
        }
        String code=pendingCode;
        // Nao descartar o codigo antes de ACTION_SET_TEXT ter sido aceito.
        // Na v1.4 o Flutter podia expor EditText e so depois registrar
        // ACTION_SET_TEXT; a leitura era perdida nesse intervalo.
        focusAndSet(editor,code);
    }

    private long lastEditorRetryLog;
    /** Preserva UMA leitura que aguarda o Flutter disponibilizar ACTION_SET_TEXT.
     * Sem clipboard, sem digitacao sintetica privilegiada e sem configurar CMX. */
    private void deferEditorAction(String code,String stage){
        if(scanQueue.isIdle())return;
        long now=SystemClock.elapsedRealtime();
        if(pendingCode==null){
            AccessibilityNodeInfo root=mlogRoot();
            ScreenInfo info=root==null?null:inspect(root);
            if(info==null || info.login || info.dialog || "desconhecida".equals(info.screen)){
                trace("EDITOR_LOST_BEFORE_RETRY");
                finishQueuedScan("editor_indisponivel");return;
            }
            startPending(code,info.screen);
            retryFill(serial,info.screen);
        }
        if(now-lastEditorRetryLog>400){
            lastEditorRetryLog=now;
            trace(stage);
        }
    }

    private void focusAndSet(AccessibilityNodeInfo editor,String code){
        if(editor==null || !editor.isEditable() || !editor.isEnabled() || editor.isPassword()){
            status("Campo bloqueado: leitura nao enviada.");finishQueuedScan("editor_bloqueado");return;
        }
        if(blockedLabel(tag(editor))){status("Campo de quantidade/peso ignorado.");finishQueuedScan("quantidade_protegida");return;}
        // Alguns CMX continuam enviando a leitura como teclas (wedge) E broadcast.
        // Se o MLOG ja recebeu algo, nao sobrescrever nem duplicar o codigo.
        CharSequence existing=editor.getText();
        if(existing!=null && existing.length()>0){
            if(code.equals(existing.toString().trim())){
                cancelPending();
                status("Codigo ja no campo: confirmacao rapida desta leitura.");
                queueEnter(code,fastImeMode()?EnterReadinessPolicy.FAST_INITIAL_DELAY_MS:
                    EnterReadinessPolicy.INITIAL_DELAY_MS);
                return;
            }
            // Em Consulta/Conferencia, a caixa de busca conserva o ultimo codigo.
            // Uma NOVA bipagem pode substitui-lo, somente nessa tela reconhecida.
            AccessibilityNodeInfo root=mlogRoot();
            ScreenInfo info=root==null?null:inspect(root);
            boolean newSearch=info!=null &&
                ("consulta".equals(info.screen) || "conferencia_venda".equals(info.screen));
            boolean previouslyInserted=info!=null &&
                ("atividades".equals(info.screen) || "separacao".equals(info.screen)) &&
                existing.toString().trim().equals(lastEnteredByBridge) &&
                info.screen.equals(lastEnteredScreen);
            if(!newSearch && !previouslyInserted){
                status("Campo ja preenchido manualmente: nao sobrescrevi.");finishQueuedScan("campo_ocupado");return;
            }
            // A proxima ACTION_SET_TEXT substitui o codigo anterior.
        }
        try{
            // Foco e disponibilidade da acao podem mudar em frames distintos
            // no Flutter. Aguardar em vez de perder a primeira leitura.
            if(!editor.isFocused()){
                focusOrClick(editor);
                deferEditorAction(code,"WAIT_EDITOR_FOCUS");
                return;
            }
            Bundle data=new Bundle();
            data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,code);
            // Alguns widgets anunciam EditText antes da ACTION_SET_TEXT.
            // A tentativa direta e segura para este editor ja verificado;
            // caso o Android recuse, conservar a leitura e aguardar um evento.
            boolean advertised=supports(editor,AccessibilityNodeInfo.ACTION_SET_TEXT);
            boolean done=editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data);
            if(!done){
                deferEditorAction(code,advertised?"SET_TEXT_RETRY":"ACTION_SET_TEXT_PENDING");
                return;
            }
            AccessibilityNodeInfo currentRoot=mlogRoot();
            ScreenInfo currentInfo=currentRoot==null?null:inspect(currentRoot);
            lastEnteredByBridge=code;
            lastEnteredScreen=currentInfo==null?"":currentInfo.screen;
            cancelPending();
            trace("SET_TEXT_OK");
            status("Leitura inserida: aguardando confirmacao.");
            // O estado preparado sera limpo ao terminar ESTA leitura;
            // nao usar timer que possa apagar o estado da leitura seguinte.
            queueEnter(code,fastImeMode()?EnterReadinessPolicy.FAST_INITIAL_DELAY_MS:
                    EnterReadinessPolicy.INITIAL_DELAY_MS);

        }catch(RuntimeException e){status("Falha de acessibilidade ao inserir o codigo.");finishQueuedScan("erro_set_text");}
    }
    // Cada bipe cria UMA confirmacao. O CMX pode enviar Enter antes do Flutter
    // abrir seu campo; por isso verificamos brevemente se o valor ainda esta
    // no editor antes de concluir que o Enter nativo foi recebido.
    private long confirmStartedAt;
    private boolean imeEnterRequested;
    private boolean imeCheckRequested;
    private boolean verifyingImeClick;
    private long imeGestureAt;
    private long lastImeWaitLogAt;
    // API 33+: controla o Enter pelo InputConnection sem clicar no Gboard.
    private boolean directEnterSent;
    private boolean customImeSent;
    private long directEnterSentAt;
    private long lastConnectionLogAt;

    private void queueEnter(String expected,long delay){
        final long id=++enterAttempt;
        awaitingEnterConfirmation=true;
        confirmStartedAt=SystemClock.elapsedRealtime();
        imeEnterRequested=false;
        imeCheckRequested=false;
        verifyingImeClick=false;
        imeGestureAt=0;
        lastImeWaitLogAt=0;
        directEnterSent=false;
        customImeSent=false;
        directEnterSentAt=0;
        lastConnectionLogAt=0;
        trace("ENTER_WAIT_INPUT_CONNECTION");
        main.postDelayed(()->confirmEnteredCode(expected,id),delay);
    }

    private void confirmEnteredCode(String expected,long attempt){
        if(attempt!=enterAttempt || scanQueue.isIdle())return;
        if(!ScanCoordinator.isActive(this)){
            finishQueuedScan("desativado");return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null){finishQueuedScan("tela_fechada");return;}
        ScreenInfo info=inspect(root);
        if(info.login || info.dialog || isBlockingOverlay(root)){
            suspendForNavigation("modal_during_confirmation");return;
        }
        AccessibilityNodeInfo editor=safeEditor(info,true);
        if(editor==null && info.editors.size()==1 &&
           !blockedLabel(tag(info.editors.get(0))) &&
           info.editors.get(0).isEditable())editor=info.editors.get(0);
        if(editor==null || !editor.isVisibleToUser() || editor.isPassword()){
            status("Entrada saiu da tela; conferência visual recomendada.");
            finishQueuedScan("campo_saiu");return;
        }
        CharSequence current=editor.getText();
        if(current==null || !expected.equals(current.toString().trim())){
            // SET_TEXT pode responder antes da arvore Flutter refletir o texto.
            // No modo rapido, aguardar brevemente antes de concluir sem Enter.
            if(fastImeMode() && !directEnterSent &&
                SystemClock.elapsedRealtime()-confirmStartedAt<180){
                scheduleConfirm(expected,attempt,45);
                return;
            }
            trace("CODE_CHANGED_AFTER_ENTER");
            status("Codigo saiu do campo; confirmar resultado no MLOG.");
            finishQueuedScan("campo_mudou");return;
        }
        long age=SystemClock.elapsedRealtime()-confirmStartedAt;
        // O diagnostico mostrou que o campo esta preenchido em ~120-300 ms.
        // Dar uma janela curta ao Enter do CMX, nao aguardar 600 ms.
        long readiness=fastImeMode()?45:90;
        if(age<readiness){scheduleConfirm(expected,attempt,readiness-age);return;}
        if(!editor.isFocused()){
            if(age<1150){
                focusOrClick(editor);
                trace("REFOCUS_FOR_ENTER");
                scheduleConfirm(expected,attempt,145);
            }else{
                status("Campo perdeu foco; nao confirmei outro controle.");
                finishQueuedScan("foco_perdido");
            }
            return;
        }
        // Teclado Bridge v1.9: opcional e selecionado pelo proprio usuario.
        // Apenas tenta enviar Enter depois que o codigo esta no editor correto,
        // focado e reconhecido como MLOG. A v1.8 permanece como fallback.
        if(!directEnterSent && prefs().getBoolean("experimental_bridge_ime",false)
                && BridgeScanInputMethodService.sendEnterForFocusedMlog()){
            directEnterSent=true;
            customImeSent=true;
            directEnterSentAt=SystemClock.elapsedRealtime();
            trace("FAST_IME_MODE_"+(fastImeMode()?"ON":"OFF"));
            trace("BRIDGE_IME_ENTER_SENT");
            scheduleConfirm(expected,attempt,fastImeMode()?55:110);
            return;
        }
        // Prioridade v1.8: InputConnection Android 13+, sem toque no Gboard.
        // Verificacao de foco / campo / pacote foi feita acima ANTES da acao.
        if(directEnterSent){
            // Nao enviar dois Enter para o mesmo bipe. O Flutter pode levar
            // alguns frames para limpar o campo e atualizar a tela.
            long since=SystemClock.elapsedRealtime()-directEnterSentAt;
            if(since<440){
                scheduleConfirm(expected,attempt,Math.min(fastImeMode()?65:110,440-since));
                return;
            }
            trace(customImeSent?"BRIDGE_IME_ENTER_UNVERIFIED":"DIRECT_ENTER_SENT_UNVERIFIED");
            status("Enter enviado, mas o campo ainda contem o codigo. Conferir MLOG.");
            finishQueuedScan("direct_enter_nao_verificado");
            return;
        }
        if(Build.VERSION.SDK_INT>=33){
            if(sendDirectEnterToFocusedMlogField()){
                directEnterSent=true;
                directEnterSentAt=SystemClock.elapsedRealtime();
                trace("DIRECT_ENTER_SENT");
                scheduleConfirm(expected,attempt,130);
                return;
            }
            // O InputConnection pode aparecer um frame apos o Flutter criar
            // seu EditText. Espera adaptativa limitada, sem tocar na tela.
            if(age<440){
                long now=SystemClock.elapsedRealtime();
                if(now-lastConnectionLogAt>190){
                    lastConnectionLogAt=now;
                    trace("INPUT_CONNECTION_NOT_READY");
                }
                scheduleConfirm(expected,attempt,65);
                return;
            }
            if(!imeEnterRequested)trace("INPUT_CONNECTION_UNAVAILABLE_FALLBACK");
        }
        // Recuperacao antiga: somente se InputConnection nao existir.
        // Nunca usar fallback depois de enviar KEYCODE_ENTER diretamente.
        if(!imeEnterRequested){
            imeEnterRequested=true;
            boolean accepted=false;
            try{
                if(supports(editor,AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId()))
                    accepted=editor.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
            }catch(RuntimeException ignored){}
            trace(accepted?"IME_ENTER_REQUESTED":"IME_ENTER_UNAVAILABLE");
            if(accepted){
                // Flutter precisa de tempo para atualizar resultado do IME.
                scheduleConfirm(expected,attempt,175);
                return;
            }
        }
        if(!imeCheckRequested){
            // Nao marcar "tentativa executada" antes de efetivamente encontrar
            // um controle. No diagnostico v1.6, a busca falhou ~2 ms depois
            // de IME_ENTER_UNAVAILABLE, enquanto Gboard ainda animava.
            if(pressVisibleImeEnter()){
                imeCheckRequested=true;
                trace("GBOARD_ENTER_CLICK");
                verifyingImeClick=true;
                imeGestureAt=SystemClock.elapsedRealtime();
                scheduleConfirm(expected,attempt,195);
                return;
            }
            if(tapKnownGboardConfirm(info,editor)){
                imeCheckRequested=true;
                trace("GBOARD_ACTION_GESTURE");
                verifyingImeClick=true;
                imeGestureAt=SystemClock.elapsedRealtime();
                scheduleConfirm(expected,attempt,205);
                return;
            }
            if(EnterReadinessPolicy.canRetry(age)){
                long now=SystemClock.elapsedRealtime();
                if(now-lastImeWaitLogAt>=260){
                    lastImeWaitLogAt=now;
                    trace("GBOARD_KEY_NOT_READY_RETRY");
                }
                // Estado, nao atraso fixo: verifica janela e tecla em ciclos
                // curtos, sem perder o codigo e sem tocar em numero/quantidade.
                scheduleConfirm(expected,attempt,EnterReadinessPolicy.nextDelay(age));
                return;
            }
            trace("GBOARD_KEY_TIMEOUT");
            status("Codigo no campo, mas tecla de confirmacao nao ficou disponivel.");
            finishQueuedScan("enter_nao_disponivel");
            return;
        }
        if(verifyingImeClick){
            // Um frame Flutter ainda pode levar ~300 ms apos o gesto.
            // Nao pressionar Enter novamente, so observar a conclusao.
            long elapsed=SystemClock.elapsedRealtime()-imeGestureAt;
            if(elapsed<460){
                scheduleConfirm(expected,attempt,Math.min(125,460-elapsed));
                return;
            }
            trace("ENTER_GESTURE_UNCONFIRMED");
            status("Tecla de confirmacao acionada, mas codigo ainda visivel no campo; verificar MLOG.");
            finishQueuedScan("confirmacao_nao_verificada");
            return;
        }
        // Fluxo defensivo: nunca repetir um Enter ja enviado.
        status("Aguardando confirmacao no MLOG.");
        finishQueuedScan("confirmacao_nao_verificada");
    }
    /** Android 13+ permite InputConnection diretamente no AccessibilityService.
     * Nao ativa IME proprio, nao usa clipboard, nao injeta eventos globalmente.
     * O Android so encaminha ao editor cujo InputConnection esta ativo. */
    private boolean sendDirectEnterToFocusedMlogField(){
        if(Build.VERSION.SDK_INT<33 || mlogRoot()==null)return false;
        try{
            InputMethod method=getInputMethod();
            if(method==null || !method.getCurrentInputStarted())return false;
            EditorInfo info=method.getCurrentInputEditorInfo();
            if(info==null || !ScanCoordinator.TARGET.equals(info.packageName)){
                trace("INPUT_CONNECTION_OTHER_PACKAGE");
                return false;
            }
            InputMethod.AccessibilityInputConnection connection=method.getCurrentInputConnection();
            if(connection==null)return false;
            long t=SystemClock.uptimeMillis();
            int flags=KeyEvent.FLAG_SOFT_KEYBOARD;
            int device=KeyCharacterMap.VIRTUAL_KEYBOARD;
            KeyEvent down=new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,
                    0,0,device,0,flags);
            KeyEvent up=new KeyEvent(t,t+8,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER,
                    0,0,device,0,flags);
            connection.sendKeyEvent(down);
            connection.sendKeyEvent(up);
            return true;
        }catch(RuntimeException e){
            trace("INPUT_CONNECTION_SEND_FAILED_"+e.getClass().getSimpleName());
            return false;
        }
    }

    private void scheduleConfirm(String expected,long id,long delay){
        main.postDelayed(()->confirmEnteredCode(expected,id),Math.max(50,delay));
    }

    /** Fallback exclusivo do equipamento CMX 720x1440 / Gboard observado.
     * No diagnostico o Gboard nao expoe a tecla ✓ por acessibilidade. O
     * usuario confirmou o teclado numerico com ✓ na ultima coluna e linha.
     * Acessibilidade limita gestos ao MLOG e aqui validamos a janela IME,
     * app de destino, campo focado, tamanho, e geometria da tela. */
    private boolean tapKnownGboardConfirm(ScreenInfo info,AccessibilityNodeInfo editor){
        if(info==null || editor==null || !editor.isEditable() ||
           !editor.isFocused() || editor.isPassword() ||
           blockedLabel(tag(editor)) || info.login || info.dialog)return false;
        if(!("separacao".equals(info.screen) || "atividades".equals(info.screen) ||
             "conferencia_venda".equals(info.screen) || "consulta".equals(info.screen) ||
             "pedidos".equals(info.screen) || "notas".equals(info.screen)))return false;
        Point p=realScreenSize();
        // A calibracao abaixo e valida SOMENTE para o coletor diagnosticado.
        if(p.x<710 || p.x>730 || p.y<1420 || p.y>1460)return false;
        final float x=p.x*.872f, y=p.y*.921f;
        try{
            for(AccessibilityWindowInfo w:getWindows()){
                if(w.getType()!=AccessibilityWindowInfo.TYPE_INPUT_METHOD)continue;
                AccessibilityNodeInfo ime=w.getRoot();
                if(ime==null || ime.getPackageName()==null ||
                   !"com.google.android.inputmethod.latin".contentEquals(ime.getPackageName()))continue;
                Rect wr=new Rect();w.getBoundsInScreen(wr);
                if(wr.width()<p.x*.88f || wr.height()<p.y*.18f ||
                   wr.top>p.y*.82f || wr.bottom<p.y*.92f ||
                   !wr.contains((int)x,(int)y))continue;
                // Nao clicar se uma tecla ou botao nomeado ocupou essa regiao
                // com texto que NAO corresponde a uma confirmacao.
                List<AccessibilityNodeInfo> keys=new ArrayList<>();visit(ime,keys,0);
                for(AccessibilityNodeInfo key:keys){
                    if(!key.isVisibleToUser())continue;
                    Rect r=new Rect();key.getBoundsInScreen(r);
                    if(r.isEmpty() || !r.contains((int)x,(int)y))continue;
                    String name=norm(key.getContentDescription()).trim();
                    if(name.isEmpty())name=norm(key.getText()).trim();
                    if(name.length()==1 && Character.isDigit(name.charAt(0)))return false;
                    if(has(name,"apagar","backspace","delete","excluir","voltar","camera"))return false;
                }
                Path path=new Path();path.moveTo(x,y);
                GestureDescription.Builder g=new GestureDescription.Builder();
                g.addStroke(new GestureDescription.StrokeDescription(path,0,65));
                return dispatchGesture(g.build(),new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription description){trace("GBOARD_GESTURE_COMPLETED");}
                    @Override public void onCancelled(GestureDescription description){trace("GBOARD_GESTURE_CANCELLED");}
                },main);
            }
        }catch(RuntimeException ignored){}
        return false;
    }

    /** Nunca usar toque por posicao no teclado numerico: o local onde era o
     *  icone MLOG pode ser a tecla '0'. Aceitar so o nome EXPLICITO da tecla
     *  de confirmacao, e apenas na extremidade inferior direita da janela IME. */
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
                    String label=norm(n.getContentDescription()).trim();
                    if(label.isEmpty())label=norm(n.getText()).trim();
                    if(label.isEmpty())continue;
                    if(!("enter".equals(label) || "retorno".equals(label) ||
                         "pesquisar".equals(label) || "buscar".equals(label) ||
                         "concluido".equals(label) || "concluir".equals(label) ||
                         "done".equals(label) || "ir".equals(label) ||
                         "go".equals(label) || "search".equals(label) ||
                         "check".equals(label) || "confirmar".equals(label) ||
                         "ok".equals(label) || "✓".equals(label) ||
                         "✔".equals(label)))continue;
                    if(!n.isVisibleToUser() || !n.isEnabled())continue;
                    Rect r=new Rect();n.getBoundsInScreen(r);
                    if(r.isEmpty() || r.centerX()<wr.left+wr.width()*.70f ||
                       r.centerY()<wr.top+wr.height()*.50f)continue;
                    AccessibilityNodeInfo target=clickable(n);
                    if(target==null || !target.isClickable())continue;
                    Rect tr=new Rect();target.getBoundsInScreen(tr);
                    if(tr.isEmpty() || tr.width()>wr.width()*.25f ||
                       tr.height()>wr.height()*.40f ||
                       tr.centerX()<wr.left+wr.width()*.70f)continue;
                    if(chosen!=null && !sameBounds(chosen,target))return false;
                    chosen=target;
                }
                if(chosen!=null){
                    if(chosen.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
                    if(gestureOnVerifiedImeKey(chosen,wr))return true;
                }
                // Alguns Gboard nao anunciam o texto do icone ✓. So usar um
                // unico controle inferior direito SE o teclado estiver no modo
                // numerico e as teclas 0..9 estiverem identificaveis.
                AccessibilityNodeInfo check= root.getPackageName()!=null &&
                    "com.google.android.inputmethod.latin".contentEquals(root.getPackageName())
                        ?numericImeCheck(nodes,wr):null;
                if(check!=null){
                    if(check.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
                    if(gestureOnVerifiedImeKey(check,wr))return true;
                }
            }
        }catch(RuntimeException ignored){}
        return false;
    }
    /** Tocar somente em tecla cujo no acessivel foi validado dentro da
     *  janela IME. Nao usar coordenadas fixas da tela do MLOG ou do teclado. */
    private boolean gestureOnVerifiedImeKey(AccessibilityNodeInfo key,Rect imeBounds){
        Rect r=new Rect();key.getBoundsInScreen(r);
        if(r.isEmpty() || r.width()>imeBounds.width()*.33f ||
           r.centerX()<imeBounds.left+imeBounds.width()*.70f ||
           r.centerY()<imeBounds.top+imeBounds.height()*.65f ||
           !imeBounds.contains(r.centerX(),r.centerY()))return false;
        Path path=new Path();path.moveTo(r.centerX(),r.centerY());
        GestureDescription.Builder gesture=new GestureDescription.Builder();
        gesture.addStroke(new GestureDescription.StrokeDescription(path,0,65));
        try{return dispatchGesture(gesture.build(),null,main);}
        catch(RuntimeException ignored){return false;}
    }
    private AccessibilityNodeInfo numericImeCheck(List<AccessibilityNodeInfo> nodes,Rect wr){
        java.util.HashSet<String> digits=new java.util.HashSet<>();
        Rect zero=new Rect();
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser())continue;
            String t=norm(n.getText()).trim();
            String d=norm(n.getContentDescription()).trim();
            String digit=t.length()==1?t:d;
            if(digit.length()==1 && digit.charAt(0)>='0' && digit.charAt(0)<='9'){
                digits.add(digit);
                if("0".equals(digit))n.getBoundsInScreen(zero);
            }
        }
        // Confirmar que se trata do teclado numerico fotografado, nunca
        // clicar em um '0' ou em botoes do MLOG por coordenadas.
        if(digits.size()<8 || zero.isEmpty())return null;
        AccessibilityNodeInfo result=null;
        Rect best=new Rect();
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isEnabled() || !n.isVisibleToUser() || !n.isClickable())continue;
            Rect r=new Rect();n.getBoundsInScreen(r);
            if(r.isEmpty() || r.width()>wr.width()*.32f ||
               r.centerX()<wr.left+wr.width()*.77f ||
               Math.abs(r.centerY()-zero.centerY())>Math.max(24,zero.height()*.7f) ||
               r.centerY()<wr.top+wr.height()*.70f ||
               r.centerY()>wr.bottom)continue;
            // Confirmacao no extremo direito da MESMA linha do zero.
            // Se houver dois botoes ambiguos, nao arriscar.
            if(result!=null && !sameBounds(result,n))return null;
            result=n;
            best.set(r);
        }
        return result;
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

    /** Campo da tela pronto: nao confundir um editor qualquer com o input
     *  aberto pelo icone nativo do MLOG. Para separacao o campo precisa estar
     *  focado ou ter rotulo explicito E ter sido aberto na tela atual. */
    private boolean nativeEntryReady(ScreenInfo info,AccessibilityNodeInfo editor){
        if(editor==null || !editor.isEditable() || !editor.isEnabled() ||
           !editor.isVisibleToUser() || editor.isPassword() ||
           blockedLabel(tag(editor)))return false;
        if(!("separacao".equals(info.screen) || "pedidos".equals(info.screen) ||
             "notas".equals(info.screen)))return editor.isFocused() ||
                has(tag(editor),"bipe","digite o codigo","codigo da atividade");
        boolean named=has(tag(editor),"bipe","digite","barcode","codigo de barras","leitura");
        // Campo focado por toque real ou pelo clique nativo anterior.
        boolean recentlyOpened=info.screen.equals(preparedScreen) &&
            SystemClock.elapsedRealtime()-preparedAt<5000;
        // Na separacao por quantidade, exigir que o botao do proprio MLOG
        // tenha sido acionado nesta leitura. Um editor numerico generico nao
        // e prova de que estamos na caixa de codigo de barras.
        if(info.hasQuantity && !recentlyOpened && !named)return false;
        return editor.isFocused() && (recentlyOpened || (named && isImeVisible()));
    }

    private void markNativeButton(String screen,String source){
        preparedScreen=screen;
        preparedAt=SystemClock.elapsedRealtime();
        status("Botao de teclado do MLOG clicado ("+source+"); esperando input.");
    }

    /** Verificar falha em ACTION_CLICK sem entrar em loop de toques.
     * Se o Gboard ja abriu, nao fazer segundo toque (poderia fechar o campo).
     * Se nao abriu, apenas UM gesto no botao conhecido da tela de separacao. */
    private void scheduleNativeFallback(int token,String screen){
        if(!"separacao".equals(screen))return;
        main.postDelayed(()->{
            if(token!=serial || pendingCode==null || prearmFallbackUsed)return;
            AccessibilityNodeInfo root=mlogRoot();
            if(root==null)return;
            ScreenInfo info=inspect(root);
            if(!"separacao".equals(info.screen) || !info.hasSeparationControls ||
                info.dialog || info.login)return;
            AccessibilityNodeInfo e=safeEditor(info,true);
            if(e!=null && nativeEntryReady(info,e))return;
            if(isImeVisible()){
                status("Teclado abriu, mas campo Flutter nao foi exposto; aguardando.");return;
            }
            if(!prefs().getBoolean("guided_taps",true) || !supportedAspect(realScreenSize()))return;
            prearmFallbackUsed=true;
            Point screenSize=realScreenSize();
            status("Primeiro clique nao abriu input: tentando botao do MLOG por posicao.");
            tapAt(token,screenSize.x*(info.hasQuantity?0.44f:0.60f),
                screenSize.y*.603f,screen);
        },650);
    }

    private boolean isImeVisible(){
        try{
            for(AccessibilityWindowInfo w:getWindows())
                if(w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD)return true;
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
        markNativeButton("separacao","toque na posicao do icone do MLOG");
        tapAt(id,size.x*(info.hasQuantity?0.44f:0.60f),size.y*.603f,"separacao");
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
        // Coordenadas de icones nao sao validas quando teclado virtual ocupa a tela.
        if(isImeVisible()){
            status("Teclado ja visivel; nao repetir toque no icone (evita tecla 0).");
            retryFill(id,screen);return;
        }
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
                    gestureInProgress=false;finishQueuedScan("gesto_cancelado");restoreKeyboard();
                    status("Toque assistido cancelado pelo Android.");
                }
            },main);
        }catch(RuntimeException e){dispatched=false;}
        if(!dispatched){gestureInProgress=false;finishQueuedScan("gesto_indisponivel");restoreKeyboard();
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
        // O resultado da consulta pode esconder o titulo, mantendo a busca
        // "Bipe ou digite enderecos..." disponivel para a proxima leitura.
        v.hasConsultation=consultationHint && (consultationTitle || v.searchField!=null);
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
    @Override public void onInterrupt(){cancelPending();scanQueue.clear();++workGeneration;restoreKeyboard();status("Acessibilidade interrompida.");}
    @Override public void onDestroy(){
        if(receiverRegistered)try{unregisterReceiver(receiver);}catch(RuntimeException ignored){}
        main.removeCallbacksAndMessages(null);
        cancelPending();
        scanQueue.clear();
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
