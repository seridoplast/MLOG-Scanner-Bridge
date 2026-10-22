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
 * Usa o gatilho fisico para preparar o botao nativo antes do broadcast CMX.
 * Preserva a insercao e nao envia Enter automatico. Nao armazena codigos nem usa clipboard.
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
    private final java.util.ArrayDeque<Long> queuedAt=new java.util.ArrayDeque<>();
    // v2.9.5: trava APENAS o novo toque, por 400 ms, sem esperar Enter.
    private final TimedClickGate timedClickGate=new TimedClickGate();
    private long totalFinished;
    private int workGeneration;
    private long lastEventProbe;
    private long lastEnterEventProbe;
    private long currentScanStartedAt;
    private boolean awaitingEnterConfirmation;
    private boolean configuredFastEvents;
    private boolean configuredTriggerEvents;
    private long lastTriggerPrefocus;
    private long lastUnknownTriggerLogAt;
    private String pendingScreen="";
    private long lastDiagnostic;
    private boolean gestureInProgress;
    private long gestureGeneration; // callbacks de gestos antigos nao podem reabrir ciclo novo
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
        // v2.3: ativar uma unica vez o pre-foco que existia desligado na v2.2.
        // A preferencia continua reversivel em Ajustes.
        if(!prefs().getBoolean("prefocus_v23_migrated",false)){
            prefs().edit().putBoolean("trigger_prefocus",true)
                .putBoolean("prefocus_v23_migrated",true).apply();
        }
        AccessibilityServiceInfo si=getServiceInfo();
        if(si!=null){
            si.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                      AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                      AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR;
            configuredFastEvents=fastImeMode();
            configuredTriggerEvents=prefs().getBoolean("trigger_prefocus",false);
            if(configuredTriggerEvents)
                si.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else si.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            si.notificationTimeout=configuredFastEvents?15:180;
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
                // Cada gatilho admitido permite apenas UMA leitura decodificada.
                // Um gatilho recusado no cooldown nao cria leitura atrasada.
                final long receivedAt=SystemClock.elapsedRealtime();
                final long previousGateGeneration=timedClickGate.generation();
                if(!timedClickGate.admitBroadcast(receivedAt)){
                    trace("BROADCAST_IGNORED_COOLDOWN_OR_ECHO_"+source);
                    return;
                }
                // Se o hardware nao expuser o KeyEvent do gatilho, o broadcast
                // inicia um ciclo novo. Nao deixar pendente um ciclo antigo.
                if(timedClickGate.generation()!=previousGateGeneration){
                    discardOldCycleOnNewTrigger();
                    trace("BROADCAST_REARM_NEW_CYCLE");
                }
                if(!ScanCoordinator.accept(code,fastImeMode())){
                    if(code!=null && !code.isEmpty()) trace("BROADCAST_SUPPRESSED_"+source);
                    return;
                }
                trace("BROADCAST_ACCEPTED_"+source);
                if(lastTriggerPrefocus>0){
                    long delta=SystemClock.elapsedRealtime()-lastTriggerPrefocus;
                    if(delta>=0 && delta<1200)trace("TRIGGER_TO_DECODE_MS_"+delta);
                }
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
            status("v2.9.5: um toque a cada 400 ms; sem fila de cliques; Enter pelo CMX.");
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
        boolean trigger=prefs().getBoolean("trigger_prefocus",false);
        if(fast==configuredFastEvents && trigger==configuredTriggerEvents)return;
        AccessibilityServiceInfo info=getServiceInfo();
        if(info==null)return;
        info.notificationTimeout=fast?15:180;
        if(trigger)info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        else info.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        try{
            setServiceInfo(info);
            configuredFastEvents=fast;
            configuredTriggerEvents=trigger;
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
        // Sem fila: nao executar posteriormente uma leitura antiga.
        // Novo gatilho admitido pode cancelar um ciclo anterior (ver onKeyEvent).
        if(!scanQueue.isIdle()){
            trace("SCAN_DISCARDED_BUSY_NO_QUEUE");
            return;
        }
        boolean idle=true;
        if(!scanQueue.offer(code)){
            status("Excesso de leituras simultaneas: confirme a tela antes de prosseguir.");
            trace("QUEUE_FULL");return;
        }
        queuedAt.addLast(now);
        trace(idle?"SCAN_RECEIVED":"SCAN_QUEUED");
        startQueuedScan();
    }
    private void startQueuedScan(){
        String code=scanQueue.active();
        if(code==null)return;
        final int epoch=++workGeneration;
        currentScanStartedAt=SystemClock.elapsedRealtime();
        if(!queuedAt.isEmpty())
            trace("QUEUE_WAIT_MS_"+(currentScanStartedAt-queuedAt.peekFirst()));
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
        ++enterAttempt; // cancelar espera passiva da leitura anterior
        if(!queuedAt.isEmpty())queuedAt.pollFirst();
        boolean more=scanQueue.complete();
        ++workGeneration; // invalida timeout antigo
        if(more){
            // Breve intervalo para o Flutter recompor a arvore apos Enter.
            // O Flutter precisa reconstruir a arvore. Aguardar so um frame
            // curto; cada nova leitura voltara a verificar campo/foco.
            main.postDelayed(this::startQueuedScan,fastImeMode()?16:65);
        }
    }


    /** O bloqueio de clique e SOMENTE temporal. Ao chegar um novo gatilho
     *  APOS os 400 ms, descartamos qualquer tentativa antiga ainda aberta.
     *  Nenhuma leitura nem gesto antigo e reexecutado depois. */
    private void discardOldCycleOnNewTrigger(){
        // O prazo temporal deve destravar mesmo se algum callback de gesto
        // anterior nao retornou. Impedir que callback velho altere o novo.
        ++gestureGeneration;
        gestureInProgress=false;
        preparing=false;
        if(scanQueue.isIdle() && pendingCode==null)return;
        trace("OLD_CYCLE_CANCELLED_ON_REARM");
        ++serial; ++enterAttempt; ++workGeneration;
        awaitingEnterConfirmation=false;
        directEnterSent=false;
        cancelPending();
        scanQueue.clear();
        queuedAt.clear();
        preparedAt=0; preparedScreen="";
        prearmFallbackUsed=false;
        // Nao cancelamos o Enter do CMX: apenas cancelamos tarefas do Bridge.
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
        queuedAt.clear();
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
        if(pendingCode!=null && !pendingScreen.isEmpty() && time-lastEventProbe>(fastImeMode()?25:75)){
            lastEventProbe=time;
            final int token=serial;
            final String screen=pendingScreen;
            main.postDelayed(()->tryPending(token,screen),fastImeMode()?18:65);
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
        if(pendingCode==null && time-lastDiagnostic>1550 && time-lastAutoActionAt>30000){
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

    /**
     * Reage ao gatilho antes de o scanner terminar de decodificar o codigo.
     * Nao consome a tecla nem solicita a leitura ao CMX. Se o firmware nao
     * disponibilizar eventos de tecla, o fluxo por broadcast permanece igual.
     * A calibracao opcional cobre teclas industriais fora do conjunto usual.
     */
    /**
     * v2.6: um acionamento fisico => uma tentativa de ABRIR a entrada nativa.
     * O KeyEvent chega ANTES do broadcast com o codigo. Nunca consumimos o
     * gatilho, inserimos texto ou enviamos Enter nesta rotina.
     *
     * Nao clicar novamente quando um editor de codigo ja estiver focado:
     * o botao do MLOG pode funcionar como alternancia e fechar a entrada.
     */
    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event==null || !prefs().getBoolean("trigger_prefocus",true) ||
           !ScanCoordinator.isActive(this) || event.getAction()!=KeyEvent.ACTION_DOWN ||
           event.getRepeatCount()!=0)return false;
        final int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_UNKNOWN || code==KeyEvent.KEYCODE_ENTER ||
           code==KeyEvent.KEYCODE_NUMPAD_ENTER || code==KeyEvent.KEYCODE_TAB ||
           code==KeyEvent.KEYCODE_BACK || code==KeyEvent.KEYCODE_HOME ||
           code==KeyEvent.KEYCODE_DEL)return false;
        final long now=SystemClock.elapsedRealtime();
        if(now<navigationGuardUntil)return false;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return false;
        ScreenInfo info=inspect(root);
        if(info.login || info.dialog || "desconhecida".equals(info.screen) ||
           isBlockingOverlay(root))return false;
        final int learned=prefs().getInt("learned_trigger_key",-1);
        final boolean recognized=isScannerTriggerKey(code) || code==learned;
        if(!recognized){
            if(prefs().getBoolean("learn_trigger_armed",false) &&
               !event.isPrintingKey() && !KeyEvent.isModifierKey(code)){
                prefs().edit().putInt("learned_trigger_key",code)
                        .putBoolean("learn_trigger_armed",false).apply();
                trace("PHYSICAL_TRIGGER_LEARNED_"+code);
                status("Botao fisico reconhecido ("+code+"). Tente bipar novamente.");
            }else if(now-lastUnknownTriggerLogAt>900){
                lastUnknownTriggerLogAt=now;
                trace("PHYSICAL_KEY_UNRECOGNIZED_"+code);
            }
            return false;
        }
        trace("PHYSICAL_TRIGGER_SEEN_"+code);
        // A media das confirmacoes visiveis na v2.6 foi 576 ms (12 amostras).
        // Um prazo 176 ms abaixo da media => cooldown fixo de 400 ms. Nao esperamos Enter.
        if(!timedClickGate.admitTrigger(now)){
            trace("CLICK_COOLDOWN_IGNORED_REMAINING_MS_"+timedClickGate.remainingMs(now));
            return false; // nunca consumir o gatilho do CMX
        }
        discardOldCycleOnNewTrigger();
        lastTriggerPrefocus=now;
        trace("CLICK_COOLDOWN_STARTED_400_MS");
        trace("TRIGGER_OPEN_ATTEMPT_"+info.screen);

        // O Flutter ainda esta abrindo um campo a partir desse mesmo gatilho.
        // Nao disparar segundo clique durante a transicao, nem na tecla zero.
        if(gestureInProgress || preparing){
            trace("TRIGGER_OPEN_IN_PROGRESS");return false;
        }
        AccessibilityNodeInfo editor=safeEditor(info,true);
        if(editor!=null && nativeEntryReady(info,editor) && editor.isFocused()){
            trace("TRIGGER_EDITOR_ALREADY_OPEN");return false;
        }
        if(preparedAt>0 && info.screen.equals(preparedScreen) &&
           now-preparedAt<450){
            trace("TRIGGER_OPEN_ALREADY_REQUESTED");return false;
        }

        // Na busca, o campo JA existe. Selecionar a caixa, nao um controle
        // generico nem o Gboard. Inclusive apos resultado de consulta.
        if("consulta".equals(info.screen) || "conferencia_venda".equals(info.screen)){
            boolean canFocus=timedClickGate.reserveClick(now);
            boolean ok=canFocus && (editor!=null ? focusOrClick(editor) :
                info.searchField!=null && focusOrClick(clickable(info.searchField)));
            trace(ok?"TRIGGER_SEARCH_FOCUS_OK":"TRIGGER_SEARCH_FOCUS_UNAVAILABLE");
            return false;
        }

        // Para separacao (inclusive Quantidade), atividades, pedidos e notas:
        // clicar no botao nativo ANTES da decodificacao. Nao esperar que a
        // fila de broadcasts fique ociosa e nao usar visibilidade do IME
        // como sinal de que o campo do Flutter ja esta pronto.
        AccessibilityNodeInfo button="atividades".equals(info.screen)?
             activityKeyboard(info):findKeyboardTarget(info);
        if(button!=null && clickNativeKeyboardOnTrigger(button,info.screen))return false;

        // Fallback de coordenadas somente em duas telas reconhecidas e
        // homologadas. Se um IME ocupa a tela, nao arriscar atingir o zero.
        if(prefs().getBoolean("guided_taps",true) &&
           supportedAspect(realScreenSize())){
            if("separacao".equals(info.screen) && info.hasSeparationControls){
                Point p=realScreenSize();
                triggerGesture(info.screen,p.x*(info.hasQuantity?0.44f:0.60f),p.y*.603f);
            }else if("atividades".equals(info.screen) && info.hasActivitiesTitle){
                Point p=realScreenSize();
                triggerGesture(info.screen,p.x*.375f,p.y*.888f);
            }else trace("TRIGGER_NATIVE_ICON_UNAVAILABLE_"+info.screen);
        }else trace("TRIGGER_NATIVE_ICON_UNAVAILABLE_"+info.screen);
        return false; // nao impedir a leitura original do CMX
    }

    /** Permite uma unica ativacao do botao nativo para o ciclo atual. */
    private boolean clickKeyboardOnce(AccessibilityNodeInfo button,String screen){
        if(button==null || !button.isEnabled() || !button.isVisibleToUser() ||
           button.isEditable() || blockedLabel(tag(button)))return false;
        if(!timedClickGate.reserveClick(SystemClock.elapsedRealtime())){
            trace("SECOND_NATIVE_CLICK_PREVENTED_"+screen);
            return false;
        }
        boolean ok=focusOrClick(button);
        if(ok)markNativeButton(screen,"um toque por ciclo");
        else trace("NATIVE_CLICK_NOT_ACCEPTED_"+screen);
        return ok;
    }

    /** Apenas ACTION_CLICK e considerado abertura. ACTION_FOCUS nao equivale
     * ao toque no botao do Flutter e nao deve ser contado como sucesso. */
    private boolean clickNativeKeyboardOnTrigger(AccessibilityNodeInfo button,String screen){
        AccessibilityNodeInfo target=clickable(button);
        if(target==null || !target.isEnabled() || !target.isVisibleToUser() ||
           target.isEditable() || blockedLabel(tag(target)))return false;
        Rect r=new Rect();target.getBoundsInScreen(r);
        Point p=realScreenSize();
        if(r.isEmpty() || r.width()>p.x*.34f || r.height()>p.y*.18f ||
           isPointCoveredByIme(r.centerX(),r.centerY()))return false;
        if(!timedClickGate.reserveClick(SystemClock.elapsedRealtime())){
            trace("SECOND_NATIVE_CLICK_PREVENTED_"+screen);
            return false;
        }
        try{
            if(target.isClickable() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                markNativeButton(screen,"gatilho fisico");
                trace("TRIGGER_NATIVE_CLICK_OK_"+screen);
                return true;
            }
        }catch(RuntimeException ignored){}
        // O Flutter pode anunciar o botao, mas rejeitar ACTION_CLICK.
        // Tentar UM gesto no centro do proprio icone (nao no teclado Android).
        return triggerGesture(screen,r.centerX(),r.centerY(),true);
    }

    /** Um toque por gatilho em coordenada validada. Nunca em caixas de
     * dialogo, login, quantidade ou quando IME pode sobrepor o icone. */
    /** Um IME compacto pode estar visivel SEM cobrir o icone do MLOG.
     * Nunca usar visibilidade do teclado como veto global. Bloquear o toque
     * somente quando o ponto estiver dentro da area realmente ocupada. */
    private boolean isPointCoveredByIme(float x,float y){
        try{
            for(AccessibilityWindowInfo w:getWindows()){
                if(w.getType()!=AccessibilityWindowInfo.TYPE_INPUT_METHOD)continue;
                Rect bounds=new Rect();
                w.getBoundsInScreen(bounds);
                // Sem limites confiaveis, falhar de forma conservadora.
                if(bounds.isEmpty() || bounds.contains((int)x,(int)y))return true;
            }
        }catch(RuntimeException ignored){return true;}
        return false;
    }

    private boolean triggerGesture(String screen,float x,float y){
        return triggerGesture(screen,x,y,false);
    }
    private boolean triggerGesture(String screen,float x,float y,boolean alreadyReserved){
        if(gestureInProgress || isPointCoveredByIme(x,y))return false;
        if(!alreadyReserved && !timedClickGate.reserveClick(SystemClock.elapsedRealtime())){
            trace("SECOND_NATIVE_GESTURE_PREVENTED_"+screen);
            return false;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return false;
        ScreenInfo i=inspect(root);
        if(!screen.equals(i.screen) || i.dialog || i.login ||
           isBlockingOverlay(root))return false;
        markNativeButton(screen,"gesto pelo gatilho");
        gestureInProgress=true;
        final long gestureId=++gestureGeneration;
        Path path=new Path();path.moveTo(x,y);
        GestureDescription.Builder gesture=new GestureDescription.Builder();
        gesture.addStroke(new GestureDescription.StrokeDescription(path,0,45));
        boolean dispatched;
        try{
            dispatched=dispatchGesture(gesture.build(),new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription g){
                    if(gestureId!=gestureGeneration)return;
                    gestureInProgress=false;
                    trace("TRIGGER_GESTURE_COMPLETED_"+screen);
                }
                @Override public void onCancelled(GestureDescription g){
                    if(gestureId!=gestureGeneration)return;
                    gestureInProgress=false;
                    preparedAt=0;preparedScreen="";
                    trace("TRIGGER_GESTURE_CANCELLED_"+screen);
                }
            },main);
        }catch(RuntimeException e){dispatched=false;}
        if(!dispatched){
            gestureInProgress=false;
            preparedAt=0;preparedScreen="";
            trace("TRIGGER_GESTURE_UNAVAILABLE_"+screen);
        }else trace("TRIGGER_GESTURE_SENT_"+screen);
        return dispatched;
    }

    private static boolean isScannerTriggerKey(int code){
        // Gatilho industrial CMX observado pelo ADB no coletor do usuario.
        return code==353 || (code>=KeyEvent.KEYCODE_F1 && code<=KeyEvent.KEYCODE_F12) ||
               (code>=KeyEvent.KEYCODE_BUTTON_1 && code<=KeyEvent.KEYCODE_BUTTON_16) ||
               code==KeyEvent.KEYCODE_BUTTON_L1 || code==KeyEvent.KEYCODE_BUTTON_R1 ||
               code==KeyEvent.KEYCODE_CAMERA || code==KeyEvent.KEYCODE_FOCUS ||
               (code>=KeyEvent.KEYCODE_PROG_RED && code<=KeyEvent.KEYCODE_PROG_BLUE);
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
        // Nunca executar um segundo clique no mesmo ciclo. O gatilho pode
        // ter aberto o botao nativo ANTES da chegada do broadcast.
        if(timedClickGate.hasClicked() ||
           (preparedAt>0 && i.screen.equals(preparedScreen) &&
            SystemClock.elapsedRealtime()-preparedAt<1100)){
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
            if(button!=null && clickKeyboardOnce(button,"atividades")){
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
            clickKeyboardOnce(button,i.screen)){
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
        if(button!=null && clickKeyboardOnce(button,i.screen)){
            retryFill(serial,i.screen);
            scheduleNativeFallback(serial,i.screen);
            return;
        }
        if("separacao".equals(i.screen) &&
                prefs().getBoolean("layout_fallback",true)){
            AccessibilityNodeInfo n=relativeKeyboard(i);
            if(n!=null && clickKeyboardOnce(n,i.screen)){
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
        // Proibido: novo toque de fallback apos o primeiro toque do gatilho.
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
        int[] intervals=fastImeMode()?new int[]{25,60,110,175,260,390,600,900,1300,1900,2700,3100}:
                new int[]{90,170,280,430,610,850,1150,1500,2000,2700,3100};
        for(int delay:intervals){
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
                status("Codigo ja no campo: aguardando Enter do CMX.");
                awaitCmxConfirmation(code,0);
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
            status("Leitura inserida: aguardando Enter do CMX.");
            // O estado preparado sera limpo ao terminar ESTA leitura;
            // nao usar timer que possa apagar o estado da leitura seguinte.
            awaitCmxConfirmation(code,0);

        }catch(RuntimeException e){status("Falha de acessibilidade ao inserir o codigo.");finishQueuedScan("erro_set_text");}
    }
    // v2.5: o Bridge NAO envia Enter. Apenas observa se o CMX concluiu.
    // MLOG pode atualizar o editor em outro frame depois do Enter do CMX.
    private long confirmStartedAt;
    private boolean directEnterSent; // sempre false nesta versao; preserva evento legado sem acao

    private void awaitCmxConfirmation(String expected,long ignoredDelay){
        final long token=++enterAttempt;
        awaitingEnterConfirmation=true;
        directEnterSent=false;
        confirmStartedAt=SystemClock.elapsedRealtime();
        trace("CMX_ENTER_PASSIVE_WAIT");
        main.postDelayed(()->observeCmxConfirmation(expected,token),40);
    }

    /** Observacao passiva. NUNCA envia teclas, clique no Gboard ou acao IME. */
    private void observeCmxConfirmation(String expected,long token){
        if(token!=enterAttempt || scanQueue.isIdle())return;
        if(!ScanCoordinator.isActive(this)){
            finishQueuedScan("desativado");return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null){finishQueuedScan("mlog_fora_da_tela");return;}
        ScreenInfo info=inspect(root);
        if(info.login || info.dialog || isBlockingOverlay(root)){
            suspendForNavigation("janela_durante_cmx_enter");return;
        }
        AccessibilityNodeInfo editor=safeEditor(info,true);
        if(editor==null && info.editors.size()==1 &&
           !blockedLabel(tag(info.editors.get(0))) &&
           info.editors.get(0).isEditable())editor=info.editors.get(0);
        if(editor==null || !editor.isVisibleToUser()){
            trace("CMX_FIELD_DISAPPEARED");
            finishQueuedScan("campo_saiu");return;
        }
        CharSequence content=editor.getText();
        if(content==null || !expected.equals(content.toString().trim())){
            trace("CMX_FIELD_CHANGED");
            finishQueuedScan("campo_mudou");return;
        }
        long waited=SystemClock.elapsedRealtime()-confirmStartedAt;
        if(waited>=1100){
            trace("CMX_ENTER_NOT_OBSERVED");
            status("Codigo preenchido; Enter do CMX nao confirmado. Confira no MLOG.");
            finishQueuedScan("sem_confirmacao_cmx");return;
        }
        main.postDelayed(()->observeCmxConfirmation(expected,token),
                fastImeMode()?40:75);
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
        // v2.9.5: nenhum segundo clique agendado. Falhas so geram diagnostico.
        // Nunca guardar um gesto para executar apos o cooldown.
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
        if(!timedClickGate.reserveClick(SystemClock.elapsedRealtime())){
            trace("SECOND_TAP_PREVENTED_"+screen);
            // O unico clique permitido ja foi reservado em outra rota.
            retryFill(id,screen);
            return;
        }
        gestureInProgress=true;
        final long gestureId=++gestureGeneration;
        lastAutoActionAt=SystemClock.elapsedRealtime();
        Path path=new Path();path.moveTo(x,y);
        GestureDescription.Builder b=new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(path,0,75));
        boolean dispatched;
        try{
            dispatched=dispatchGesture(b.build(),new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription gesture){
                    if(gestureId!=gestureGeneration)return;
                    gestureInProgress=false;
                    retryFill(id,screen);
                }
                @Override public void onCancelled(GestureDescription gesture){
                    if(gestureId!=gestureGeneration)return;
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
