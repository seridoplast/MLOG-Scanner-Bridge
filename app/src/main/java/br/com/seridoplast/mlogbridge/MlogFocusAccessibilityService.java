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
    // Mensagens de Enter nao devem ficar atras de barreiras sincronas da UI.
    // A acessibilidade e o envio da tecla continuam na thread principal.
    private final Handler enterTimer=Build.VERSION.SDK_INT>=28
            ?Handler.createAsync(Looper.getMainLooper())
            :new Handler(Looper.getMainLooper());
    // Cache somente ENTRE SET_TEXT e seu Enter, nunca entre bipes.
    // O Flutter pode recriar a arvore apos processar cada codigo.
    private AccessibilityNodeInfo insertedEditor;
    private long insertedEditorAt;
    private String scanScreen="";
    private long lastOverlayProbeAt;
    // Aviso especifico de codigo inexistente: o proximo gatilho apenas fecha OK.
    private boolean barcodeErrorHint;
    private long errorDismissGuardUntil;
    private long lastPulseAppCheckAt;
    // Reuso conservador do editor entre bipes: somente se o MLOG manteve
    // exatamente o MESMO campo focado e vazio. Nunca clicar em um botao com
    // coordenadas cacheadas nem assumir que a tela anterior ainda vale.
    private AccessibilityNodeInfo lastKnownEditor;
    private String lastKnownScreen="";
    private long lastKnownEditorAt;

    private final SeparationProductGuard productGuard=new SeparationProductGuard();
    private final LongHoldScanPolicy longHoldPolicy=new LongHoldScanPolicy();
    private int currentLongHoldKey=-1;
    private int longHoldGeneration;
    // Protecao adicional se o Android nao entregar ACTION_UP (ex.: bloqueio da tela).
    private static final long LONG_HOLD_MAX_MS=30000L;

    private ShizukuScanClient shizukuScan;
    // v3.5.5: dois toques validados via ADB no coletor piloto:
    // Volume (648,175) -> 10 ms -> seta do MLOG (55,103), via Shizuku.
    // Nao reenviar qualquer codigo ou Enter durante a recuperacao.
    private static final long VOLUME_DEBOUNCE_MS=60L;
    private static final long VOLUME_KEYBOARD_POLL_MS=120L;
    // A navegacao Flutter pode reabrir o IME depois do toque de retorno.
    private static final long VOLUME_IME_SETTLE_MS=120L;
    private static final long VOLUME_IME_LATE_CHECK_MS=180L;
    private final CameraRecoveryPolicy recoveryPolicy=new CameraRecoveryPolicy();
    private int recoveryEpoch;
    // Ignorar eventos IME transitorios criados pela propria micro-navegacao.
    private long suppressVolumeImeUntil;
    private long lastPhysicalShizukuScanAt;

    // Compativel com o IME antigo, mas a observacao principal usa as janelas
    // reais do Android, independentemente do teclado selecionado.
    void onShizukuImeOpened(){trace("BRIDGE_IME_OPENED_OBSERVED_BY_WINDOWS");}
    void onShizukuImeClosed(){trace("BRIDGE_IME_CLOSED_OBSERVED_BY_WINDOWS");}

    /** Resetar Bipe: chamado apos recolher o painel flutuante. */
    void requestShizukuFocusRecovery(){
        if(!prefs().getBoolean(ShizukuScanMode.PREF,false)){
            volumeFeedback(true,"Recuperacao por Volume exige BipXuco (Shizuku).");
            return;
        }
        final int epoch=++recoveryEpoch;
        // O painel e removido e reconstruido pelo WindowManager. Aguardar
        // um pouco para que a janela MLOG volte a ser a ativa.
        main.postDelayed(()->{
            if(epoch==recoveryEpoch)startVolumeRecovery(true,"reset_button");
        },130L);
    }

    // O Gboard pode abrir/fechar sem um TYPE_WINDOWS_CHANGED confiavel no CMX.
    // Monitorar a janela de IME em intervalos curtos enquanto Shizuku e usado.
    private final Runnable keyboardWindowWatcher=new Runnable(){
        @Override public void run(){
            if(prefs().getBoolean(ShizukuScanMode.PREF,false) &&
               !recoveryPolicy.isRecovering()){
                observeImeVisibility(isImeVisible());
            }else if(!prefs().getBoolean(ShizukuScanMode.PREF,false)){
                recoveryPolicy.resetKeyboard();
            }
            main.postDelayed(this,VOLUME_KEYBOARD_POLL_MS);
        }
    };

    private void observeAndroidKeyboard(AccessibilityEvent event){
        if(event==null || !prefs().getBoolean(ShizukuScanMode.PREF,false) ||
           recoveryPolicy.isRecovering())return;
        int kind=event.getEventType();
        if(kind!=AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
           kind!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
           kind!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)return;
        observeImeVisibility(isImeVisible());
    }

    private void observeImeVisibility(boolean visible){
        long now=SystemClock.elapsedRealtime();
        // A navegacao de ida/volta pode piscar o IME. Isso nao eh um novo
        // pedido de recuperacao; evita loops e toques repetidos no MLOG.
        if(now<suppressVolumeImeUntil){
            recoveryPolicy.resetKeyboard();
            return;
        }
        if(visible==recoveryPolicy.keyboardVisible())return;
        if(visible){
            // Teclado da quantidade do proprio painel nao deve iniciar Volume.
            if(FloatingToolsService.isBatchInputOpen())return;
            AccessibilityNodeInfo root=mlogRoot();
            if(!isVolumeRecoveryReady(root))return;
        }
        boolean closed=recoveryPolicy.observeKeyboard(visible,now);
        final int epoch=++recoveryEpoch;
        if(visible){
            trace("VOLUME_IME_OPENED_MLOG");
            return;
        }
        trace("VOLUME_IME_CLOSED_MLOG");
        if(!closed || !prefs().getBoolean("camera_auto_recovery_v348",true))return;
        main.postDelayed(()->{
            if(epoch!=recoveryEpoch || recoveryPolicy.keyboardVisible())return;
            startVolumeRecovery(false,"keyboard_closed");
        },VOLUME_DEBOUNCE_MS);
    }

    /** Piloto 720x1440: qualquer tela de trabalho que realmente exponha
     * o rotulo Volume e seu botao nas coordenadas homologadas.
     * Dialogos e layouts nao reconhecidos continuam bloqueados. */
    private boolean isVolumeRecoveryReady(AccessibilityNodeInfo root){
        if(root==null || root.getPackageName()==null ||
           !ScanCoordinator.TARGET.contentEquals(root.getPackageName()))return false;
        ScreenInfo info=inspect(root);
        if(info.login || info.dialog || isBlockingOverlay(root))return false;
        Point size=realScreenSize();
        // Aceita qualquer conferencia/separacao que realmente exponha o mesmo
        // conjunto do cabecalho (rotulo Volume e botao exato). Nao confiar no
        // nome da rota Flutter: pode variar entre entrada, pedido e separacao.
        // O segundo toque ainda e fixo, portanto tamanho nao homologado bloqueia.
        return VolumeRecoveryTarget.isReferenceSize(size.x,size.y) &&
            VolumeRecoveryTarget.hasVerifiedVolumeHeader(
                info.hasVolumeHeaderLabel,info.hasVolumeHeaderButton);
    }

    private void volumeFeedback(boolean manual,String message){
        status(message);
        trace("VOLUME_RECOVERY_MESSAGE_"+message.replace(' ','_'));
        if(manual){
            android.widget.Toast.makeText(this,message,
                android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void startVolumeRecovery(boolean manual,String reason){
        if(!prefs().getBoolean(ShizukuScanMode.PREF,false) ||
           (!manual && !prefs().getBoolean("camera_auto_recovery_v348",true)))return;
        long now=SystemClock.elapsedRealtime();
        if(recoveryPolicy.isRecovering()){
            volumeFeedback(manual,"Recuperacao ja em andamento.");return;
        }
        // O diagnostico mostrou fechamento de IME durante a leitura e
        // RECOVERY_BLOCKED pelo limite antigo de 1s. Reagendar uma unica vez
        // evita perder a recuperacao sem disputar com o Enter ainda em voo.
        long sinceScan=now-lastPhysicalShizukuScanAt;
        if(!manual && sinceScan>=0 && sinceScan<1000L &&
           !reason.endsWith("_deferred")){
            final int epoch=recoveryEpoch;
            main.postDelayed(()->{
                if(epoch==recoveryEpoch)
                    startVolumeRecovery(false,reason+"_deferred");
            },Math.max(100L,1050L-sinceScan));
            trace("VOLUME_RECOVERY_DEFERRED_AFTER_SCAN");
            return;
        }
        if(shizukuBatchActive || ScanAssistState.isArmed(this) ||
           (shizukuScan!=null && shizukuScan.hasPendingScans()) ||
           !scanQueue.isIdle() || pendingCode!=null ||
           (!manual && sinceScan>=0 && sinceScan<1000L)){
            volumeFeedback(manual,"Aguarde o fim das leituras antes de recuperar.");return;
        }
        if(shizukuScan==null || !shizukuScan.ready()){
            volumeFeedback(manual,"Shizuku nao esta pronto para recuperar a leitura.");return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(!isVolumeRecoveryReady(root)){
            volumeFeedback(manual,"Volume nao identificado nesta tela do MLOG; recuperacao ignorada.");
            return;
        }
        Point size=realScreenSize();
        if(!VolumeRecoveryTarget.isReferenceSize(size.x,size.y)){
            volumeFeedback(manual,"Recuperacao homologada apenas em 720x1440.");return;
        }
        ScreenInfo info=inspect(root);
        for(AccessibilityNodeInfo editor:info.editors){
            if(!editor.isVisibleToUser() || !editor.isEnabled())continue;
            CharSequence pendingText=editor.getText();
            if(pendingText!=null && pendingText.length()>0){
                volumeFeedback(manual,"Ha texto nao enviado. Limpe-o antes de recuperar.");
                return;
            }
        }
        // Se o teclado persistiu aberto (ex.: apos retorno de tela), feche-o
        // somente com a tela Volume verificada e sem texto pendente. Nunca
        // enviar BACK a esmo, pois isso poderia sair da conferencia.
        if(isImeVisible()){
            if(reason.endsWith("_ime_hidden")){
                volumeFeedback(manual,"Teclado ainda aberto; nao foi seguro recuperar.");
                return;
            }
            suppressVolumeImeUntil=SystemClock.elapsedRealtime()+850L;
            if(!performGlobalAction(GLOBAL_ACTION_BACK)){
                volumeFeedback(manual,"Nao foi possivel fechar o teclado.");return;
            }
            trace("VOLUME_RECOVERY_PRE_IME_HIDE_REQUESTED");
            final int epoch=++recoveryEpoch;
            main.postDelayed(()->{
                if(epoch==recoveryEpoch)
                    startVolumeRecovery(manual,reason+"_ime_hidden");
            },180L);
            return;
        }
        // O watcher pode manter estado antigo por ate 120 ms.
        if(recoveryPolicy.keyboardVisible())recoveryPolicy.resetKeyboard();
        // Mesmo par de coordenadas testado manualmente pelo operador.
        final int x=VolumeRecoveryTarget.x(size.x);
        final int y=VolumeRecoveryTarget.y(size.y);
        trace("VOLUME_RECOVERY_KNOWN_TAP_648_175");
        if(isPointCoveredByIme(x,y)){
            volumeFeedback(manual,"Volume coberto pelo teclado.");return;
        }
        if(!recoveryPolicy.begin(now,manual)){
            volumeFeedback(manual,"Aguarde alguns segundos para nova recuperacao.");return;
        }
        final int epoch=++recoveryEpoch;
        trace("VOLUME_RECOVERY_START_"+reason);
        final String initialScreen=info.screen;
        // Um unico shell Shizuku: 648,175 -> 10ms -> 55,103.
        // Evita o keyevent BACK, que pode fechar o IME em vez de sair da tela.
        // Nao ha como confirmar o resultado Flutter nesses 10 ms.
        boolean accepted=shizukuScan.recoverVolume(x,y,
            VolumeRecoveryTarget.PAUSE_MS,ok->{
                if(epoch!=recoveryEpoch){recoveryPolicy.finish();return;}
                if(!ok){
                    recoveryPolicy.finish();
                    suppressVolumeImeUntil=SystemClock.elapsedRealtime()+700L;
                    recoveryPolicy.resetKeyboard();
                    trace("VOLUME_RECOVERY_SEQUENCE_FAILED");
                    volumeFeedback(manual,"Falha na sequencia rapida de Volume pelo Shizuku.");
                    return;
                }
                trace("VOLUME_RECOVERY_DOUBLE_TAP_SENT_UNVERIFIED");
                // Nao finalizar ainda: o teclado pode reabrir durante o
                // retorno do Flutter. Manter as leituras bloqueadas ate a
                // checagem tardia para evitar codigo enviado para o IME.
                suppressVolumeImeUntil=SystemClock.elapsedRealtime()+1200L;
                main.postDelayed(()->checkVolumeReturnIme(epoch,manual,initialScreen,0),
                    VOLUME_IME_SETTLE_MS);
            });
        if(!accepted){
            recoveryPolicy.finish();
            trace("VOLUME_RECOVERY_SHIZUKU_REJECTED");
            volumeFeedback(manual,"Shizuku ocupado ou indisponivel para Volume.");
        }
    }
    /** Aguarda a volta do Flutter e fecha somente um IME visivel na tela
     * de origem homologada. Nunca usar BACK se a rota Volume ficou aberta. */
    private void checkVolumeReturnIme(int epoch,boolean manual,
            String initialScreen,int pass){
        if(epoch!=recoveryEpoch){recoveryPolicy.finish();return;}
        AccessibilityNodeInfo current=mlogRoot();
        boolean safeOnOrigin=isVolumeRecoveryReady(current);
        if(safeOnOrigin && isImeVisible()){
            // O teclado aberto pode consumir o BACK. Aqui ele e intencional:
            // a seta da tela Volume ja recebeu seu toque via Shizuku.
            boolean requested=performGlobalAction(GLOBAL_ACTION_BACK);
            trace(requested?"VOLUME_RECOVERY_POST_IME_HIDE_REQUESTED":
                "VOLUME_RECOVERY_POST_IME_HIDE_FAILED");
            main.postDelayed(()->finishVolumeReturn(epoch,manual,initialScreen),200L);
        }else if(pass==0){
            // Algumas vezes a abertura do teclado chega depois do retorno.
            main.postDelayed(()->checkVolumeReturnIme(epoch,manual,initialScreen,1),
                VOLUME_IME_LATE_CHECK_MS);
        }else{
            finishVolumeReturn(epoch,manual,initialScreen);
        }
    }

    private void finishVolumeReturn(int epoch,boolean manual,String initialScreen){
        if(epoch!=recoveryEpoch){recoveryPolicy.finish();return;}
        boolean keyboardStillOpen=isImeVisible();
        recoveryPolicy.finish();
        suppressVolumeImeUntil=SystemClock.elapsedRealtime()+700L;
        recoveryPolicy.resetKeyboard();
        trace(keyboardStillOpen?"VOLUME_RECOVERY_IME_STILL_VISIBLE":
            "VOLUME_RECOVERY_IME_HIDDEN");
        if(manual){
            AccessibilityNodeInfo returned=mlogRoot();
            String screen=returned==null?"desconhecida":inspect(returned).screen;
            volumeFeedback(true,keyboardStillOpen?
                "Volume acionado, mas o teclado continua aberto; confira a tela.":
                (initialScreen.equals(screen)?
                "Volume e seta do MLOG acionados. Confira a leitura.":
                "Toques enviados; confira se voltou a conferencia."));
        }
    }

    // Independente do motor IME antigo. Uma leitura fisica inicia o lote,
    // cada passo seguinte exige incremento visivel de 1 no MESMO produto.
    private boolean shizukuBatchActive;
    private int shizukuBatchGeneration;
    private int shizukuBatchTarget;
    private int shizukuBatchDispatched;
    private String shizukuBatchBarcode;
    private String shizukuBatchScreen;
    private SeparatedQuantityProbe.Result shizukuBatchObserved;
    private long shizukuBatchDispatchedAt;

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
    // Ciclo temporizado: sem esperar mudanca visual do Flutter apos o Enter.
    private long lastFixedEnterAt;
    private boolean fixedEnterScheduled;
    // v3.0.4: trava APENAS o novo toque, por 100 ms, sem esperar Enter.
    private final TimedClickGate timedClickGate=new TimedClickGate();
    // Contador sob comando explicito do operador, nunca ativo por padrao.
    // Primeira leitura: CMX normal. Repeticoes: codigo e Enter locais (Bridge IME).
    private boolean autoCountRunning;
    private boolean autoCountFirst;
    private long autoCountFirstEnterAt;
    private int autoCountToken;
    private int autoCountTarget;
    private int autoCountDone;
    private boolean autoCountClicked;
    private boolean autoCountImeWaitLogged;
    private long autoCountImeWaitStartedAt;
    private boolean autoCountEditorReconnectTried;
    private void traceOnceWaitingImeConnection(){
        if(autoCountImeWaitLogged)return;
        autoCountImeWaitLogged=true;
        trace("AUTO_IME_WAIT_LIVE_CONNECTION_"+(autoCountDone+1));
    }
    private String autoCountCode;
    private String autoCountScreen;
    private long autoCountLastActionAt;
    private long autoCountDeadline;
    // v3.0.2: distinguir Enters enviados de incrementos realmente visíveis no MLOG.
    private SeparatedQuantityProbe.Result autoCountObservedProduct;
    private int autoCountUiVerified;
    private int autoCountBeforeEnter;
    private long autoCountEnterAt;
    private int autoCountUnverified;

    
    private long totalFinished;
    private int workGeneration;
    private long lastEventProbe;
    private long lastEnterEventProbe;
    private long lastHardwareEnterAt;
    private boolean conferenceFallbackAttempted;
    private boolean nativeRefocusAttempted;
    private long lastBridgeEnterAt;
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

    // Zebra IME: preparar o campo ao ENTRAR numa tela de leitura, antes do
    // primeiro bipe. Nunca digita nem envia Enter por acessibilidade.
    private final ZebraAutoFocusPolicy zebraAutoFocusPolicy=new ZebraAutoFocusPolicy();
    private boolean zebraAutoFocusPending;
    private final Runnable zebraAutoFocusTask=()->{
        zebraAutoFocusPending=false;
        if(!prefs().getBoolean(ZebraImeMode.PREF,false) ||
           prefs().getBoolean(ZebraProfile0Mode.PREF,false) ||
           !prefs().getBoolean(ZebraImeMode.AUTO_FOCUS_PREF,true) ||
           !ScanCoordinator.isActive(this) ||
           BridgeScanInputMethodService.hasMlogInput())return;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return;
        ScreenInfo info=inspect(root);
        if(!ZebraAutoFocusPolicy.isEligible(info.screen,info.login,info.dialog,
                isBlockingOverlay(root)))return;
        long now=SystemClock.elapsedRealtime();
        if(!zebraAutoFocusPolicy.shouldAttempt(info.screen,now))return;
        boolean accepted=requestZebraKeyboardFocus();
        trace(accepted?"ZEBRA_AUTO_FOCUS_REQUESTED":"ZEBRA_AUTO_FOCUS_NOT_ACCEPTED");
        // O Flutter pode demorar a criar a InputConnection depois do clique.
        // Poucas tentativas espaçadas; nunca um loop por evento/frame.
        if(!BridgeScanInputMethodService.hasMlogInput() &&
           zebraAutoFocusPolicy.hasAttemptsRemaining() && accepted){
            scheduleZebraAutoFocus(ZebraAutoFocusPolicy.MIN_ATTEMPT_GAP_MS);
        }
    };

    private void scheduleZebraAutoFocus(long delayMs){
        if(zebraAutoFocusPending)return;
        zebraAutoFocusPending=true;
        main.postDelayed(zebraAutoFocusTask,delayMs);
    }

    private void handleZebraScreenEvent(AccessibilityEvent event){
        if(prefs().getBoolean(ZebraProfile0Mode.PREF,false))return;
        if(event==null || !prefs().getBoolean(ZebraImeMode.AUTO_FOCUS_PREF,true))return;
        int kind=event.getEventType();
        if(kind!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
           kind!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
           kind!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
        CharSequence pkg=event.getPackageName();
        if(pkg==null || !ScanCoordinator.TARGET.contentEquals(pkg))return;
        if(BridgeScanInputMethodService.hasMlogInput()){
            zebraAutoFocusPolicy.markReady();
            return;
        }
        // Nao percorrer a arvore em todo CONTENT_CHANGED. O trabalho
        // pesado fica concentrado numa tentativa com debounce.
        if(!zebraAutoFocusPending && zebraAutoFocusPolicy.canSchedule(
                SystemClock.elapsedRealtime())){
            scheduleZebraAutoFocus(kind==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED?120:190);
        }
    }

    // Background health check while accessibility service is alive; no focus stealing.
    private final Runnable shizukuWatchdog=new Runnable(){
        @Override public void run(){
            ShizukuHealthMonitor.refresh(MlogFocusAccessibilityService.this);
            main.postDelayed(this,5000);
        }
    };

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        instance=this;
        shizukuScan=new ShizukuScanClient(this);
        shizukuScan.start();
        main.post(shizukuWatchdog);
        main.post(keyboardWindowWatcher);
        BridgePreferences.enableOnUpgrade(prefs());
        updateClickCooldownMs(prefs().getInt(ScanAssistState.CLICK_COOLDOWN_MS,
                (int)TimedClickGate.COOLDOWN_MS));
        if(!ScanAssistState.AUTO_BIPS_ENABLED){
            prefs().edit().putBoolean(ScanAssistState.ARMED,false)
                    .putBoolean(ScanAssistState.BATCH,false).apply();
        }
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
            // Necessario para observar Gboard e outros IMEs, nao so o MLOG.
            // As demais rotas continuam filtradas por pacote no codigo.
            si.packageNames=null;
            si.eventTypes|=AccessibilityEvent.TYPE_WINDOWS_CHANGED |
                           AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            configuredFastEvents=fastImeMode();
            configuredTriggerEvents=!prefs().getBoolean(ShizukuScanMode.PREF,false) &&
                !prefs().getBoolean(ZebraImeMode.PREF,false) &&
                (prefs().getBoolean("trigger_prefocus",false) ||
                 prefs().getBoolean(NativeScannerController.LONG_HOLD_PREF,false));
            if(configuredTriggerEvents)
                si.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else si.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            si.notificationTimeout=configuredFastEvents?15:180;
            setServiceInfo(si);
        }
        receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context context,Intent intent){
                // Shizuku is an isolated broadcast-only input channel. Never touch the
                // MLOG UI, send old Enter, or process alternate/duplicate sources.
                if (prefs().getBoolean(ShizukuScanMode.PREF,false)) {
                    if (intent==null || !DirectScanPolicy.ACTION.equals(intent.getAction()))return;
                    AccessibilityNodeInfo active=getRootInActiveWindow();
                    if(active==null || active.getPackageName()==null ||
                       !ScanCoordinator.TARGET.contentEquals(active.getPackageName())) {
                        status("Leitura bloqueada: abra a conferencia do MLOG.");
                        return;
                    }
                    String raw=ScannerProtocol.barcodeRaw(intent);
                    lastPhysicalShizukuScanAt=SystemClock.elapsedRealtime();
                    // Cancela recuperacao pendente se houve nova leitura.
                    ++recoveryEpoch;
                    if(recoveryPolicy.isRecovering()){
                        trace("VOLUME_RECOVERY_SCAN_BLOCKED_NO_REPLAY");
                        status("Leitura durante recuperacao de Volume: nao enviada. Bipe novamente.");
                        return;
                    }
                    if(shizukuBatchActive){
                        // Another physical scan means operator interrupted the batch.
                        stopShizukuBatch("Novo bipe físico recebido: lote automático interrompido.");
                    }
                    if(ScanAssistState.isArmed(MlogFocusAccessibilityService.this))startShizukuBatch(raw);
                    if(shizukuScan!=null)shizukuScan.scan(raw);
                    if(!ShizukuHealthMonitor.ready())
                        ShizukuHealthMonitor.refresh(MlogFocusAccessibilityService.this);
                    return;
                }
                // Modo Zebra: acessibilidade NUNCA escreve codigo nem envia Enter.
                // Caso o IME ainda nao tenha iniciado, recuperar APENAS o
                // primeiro bipe utilizado para abrir o campo do MLOG.
                if(prefs().getBoolean(ZebraImeMode.PREF,false)){
                    // Profile0 nao consome scan pelo Bridge. O PDA Service
                    // emite eventos de teclado para o MLOG diretamente.
                    if(prefs().getBoolean(ZebraProfile0Mode.PREF,false))return;
                    if(intent!=null && DirectScanPolicy.ACTION.equals(intent.getAction()) &&
                       !BridgeScanInputMethodService.isRunning() &&
                       prefs().getBoolean(ZebraImeMode.AUTO_FOCUS_PREF,true)){
                        String code=BarcodeCleaner.clean(ScannerProtocol.barcodeRaw(intent));
                        if(ZebraImeMode.isValidCode(code,ScannerProtocol.type(intent))){
                            zebraInitialScan=code;
                            zebraInitialScanAt=SystemClock.elapsedRealtime();
                            if(!requestZebraKeyboardFocus())zebraInitialScan=null;
                        }
                    }else if(intent!=null && DirectScanPolicy.ACTION.equals(intent.getAction()) &&
                             !BridgeScanInputMethodService.hasMlogInput() &&
                             prefs().getBoolean(ZebraImeMode.AUTO_FOCUS_PREF,true)){
                        requestZebraKeyboardFocus();
                    }
                    return;
                }
                boolean treat=ScanAssistState.isClean(MlogFocusAccessibilityService.this);
                String code=treat?ScannerProtocol.barcodeRaw(intent):ScannerProtocol.barcode(intent);
                if(treat){
                    String cleaned=BarcodeCleaner.clean(code);
                    if(code!=null && cleaned!=null && !code.equals(cleaned)){
                        trace("SCAN_CLEANED_REMOVED_CHARS_"+
                                (code.length()-cleaned.length()));
                        ScanAssistState.report("Tratador: removidos "+
                                (code.length()-cleaned.length())+" caracteres invisíveis.");
                    }
                    code=cleaned;
                }
                // O gatilho seguinte em aviso de codigo inexistente deve
                // pressionar OK, inclusive se o codigo capturado for invalido.
                if(barcodeErrorHint &&
                    ScanCoordinator.isActive(MlogFocusAccessibilityService.this) &&
                    DirectScanPolicy.accept(intent==null?null:intent.getAction(),
                        prefs().getBoolean(DirectScanPolicy.PREF,false))) {
                    AccessibilityNodeInfo errorRoot=mlogRoot();
                    if(tryDismissKnownBarcodeError(errorRoot))return;
                }
                if(SystemClock.elapsedRealtime()<errorDismissGuardUntil){
                    trace("SCAN_IGNORED_AFTER_ERROR_OK");return;
                }
                // O codigo decodificado nao deve ser alterado nem confirmado
                // quando vier vazio, com controle ou checksum GS1 invalido.
                // O tipo declarado pelo hardware nao e uma prova de que todo
                // numero de 13 digitos e EAN: preservamos os codigos internos.
                if(prefs().getBoolean(NativeScannerController.PREF,false) &&
                   !BarcodeIntegrityPolicy.isValid(code,ScannerProtocol.type(intent))){
                    trace("SCAN_INTEGRITY_REJECTED");
                    status("Leitura invalida: aproxime a etiqueta e bipe novamente.");
                    return;
                }
                // Leitura que contem SOMENTE artefatos nao pode ser inserida.
                if(code!=null && BarcodeCleaner.clean(code).isEmpty()){
                    trace("SCAN_CONTROLS_ONLY_IGNORED");
                    ScanAssistState.report("Codigo ignorado: somente espacos/caracteres invisiveis.");
                    return;
                }
                // Nao misturar leitura fisica com repeticoes automatizadas.
                if(autoCountRunning){
                    trace("AUTO_COUNT_HARDWARE_IGNORED_RUNNING");
                    return;
                }
                if(!ScanCoordinator.isActive(MlogFocusAccessibilityService.this))return;
                // Logar apenas a ORIGEM do broadcast, nunca o codigo lido.
                final String action=intent==null?null:intent.getAction();
                final boolean directMode=prefs().getBoolean(DirectScanPolicy.PREF,false);
                if(!DirectScanPolicy.accept(action,directMode)){
                    // Descartar CMX/HIK quando o operador habilitou o canal
                    // exclusivo: nunca deixar duas fontes escreverem no Flutter.
                    trace("SCAN_SOURCE_IGNORED_NON_EXCLUSIVE");
                    return;
                }
                final String source=ScannerProtocol.ACTION_DIRECT.equals(action)?"DIRECT":
                        ScannerProtocol.ACTION_CMX.equals(action)?"CMX":
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
                    // O modo exclusivo nao pode cancelar um Enter pendente
                    // apenas porque chegou novo bipe. Descarta o bipe ocupado
                    // com aviso em vez de interromper/duplicar a confirmacao.
                    if(!directMode && !prefs().getBoolean(NativeScannerController.PREF,false))
                        discardOldCycleOnNewTrigger();
                    trace(directMode?"DIRECT_BROADCAST_NEW_CYCLE":"BROADCAST_REARM_NEW_CYCLE");
                }
                long dedupWindow=prefs().getBoolean(NativeScannerController.PREF,false)
                        ?timedClickGate.getCooldownMs():ScanDuplicatePolicy.DUPLICATE_WINDOW_MS;
                if(!ScanCoordinator.accept(code,fastImeMode(),dedupWindow)){
                    if(code!=null && !code.isEmpty()) trace("BROADCAST_SUPPRESSED_"+source);
                    return;
                }
                trace("BROADCAST_ACCEPTED_"+source);
                if(prefs().getBoolean(NativeScannerController.PREF,false)){
                    trace("NATIVE_SCANNER_DELIVERY_OK");
                    prefs().edit().putBoolean("native_first_broadcast_seen",true).apply();
                }
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
            if(!prefs().getBoolean(ShizukuScanMode.PREF,false))restoreKeyboard();
            status("Bridge conectado: aguardando leitura do MLOG.");
            if(prefs().getBoolean(NativeScannerController.PREF,false)){
                boolean applied=NativeScannerController.configure(this);
                trace(applied?"NATIVE_SCANNER_RECONFIG_SENT":"NATIVE_SCANNER_RECONFIG_FAILED");
                if(!applied)status("Leitor nativo indisponivel; verifique o servico Hikrobot.");
            }
        }catch(RuntimeException e){status("Falha ao registrar o broadcast do scanner.");}
    }

    void prepareShizuku(){
        recoveryPolicy.resetKeyboard();
        recoveryPolicy.finish();
        ++recoveryEpoch;
        suppressVolumeImeUntil=0L;
        // The mode changed while the accessibility service stays alive.
        // Cancel every outstanding old-mode Enter/focus/tap callback before
        // accepting the first privileged barcode. Never replay old reads.
        main.removeCallbacksAndMessages(null);
        main.post(shizukuWatchdog);
        main.post(keyboardWindowWatcher);
        enterTimer.removeCallbacksAndMessages(null);
        cancelPending();
        scanQueue.clear();
        ++workGeneration;
        zebraInitialScan=null;
        if(shizukuScan!=null)shizukuScan.connect();
    }

    /** Atualiza o bloqueio entre gatilhos sem reiniciar a acessibilidade. */
    boolean updateClickCooldownMs(int value){
        boolean valid=timedClickGate.setCooldownMs(value);
        if(valid)trace("CLICK_COOLDOWN_CONFIGURED_MS_"+value);
        return valid;
    }
    private SharedPreferences prefs(){return getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);}
    /** So acelera quando o operador optou pelo teclado proprio E pelo modo rapido. */
    private boolean fastImeMode(){
        if(prefs().getBoolean(ShizukuScanMode.PREF,false))return false;
        return prefs().getBoolean("experimental_bridge_ime",false) &&
               prefs().getBoolean("fast_ime_mode",false);
    }
    /** O usuario pode alternar sem reiniciar a acessibilidade. */
    void refreshEventThrottle(){
        boolean fast=fastImeMode();
        boolean trigger=!prefs().getBoolean(ShizukuScanMode.PREF,false) &&
            !prefs().getBoolean(ZebraImeMode.PREF,false) &&
            (prefs().getBoolean("trigger_prefocus",false) ||
             prefs().getBoolean(NativeScannerController.LONG_HOLD_PREF,false) ||
             prefs().getBoolean(BridgeEnterPolicy.PREFERENCE,false));
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
        long now=SystemClock.elapsedRealtime();
        if(now<navigationGuardUntil){
            trace("SCAN_DURING_NAVIGATION_IGNORED");
            status("Janela de informacoes/cancelamento: leitura nao executada.");
            return;
        }
        // Modo nativo: NAO percorrer a arvore inteira ANTES de enfileirar.
        // A validacao de app/modal ocorre em onBarcode() e imediatamente
        // antes do Enter; o antigo caminho fazia duas ou tres visitas extras
        // a cada bipe, atrasando inclusive os broadcasts que ja aguardavam.
        if(!prefs().getBoolean(NativeScannerController.PREF,false)){
            AccessibilityNodeInfo currentRoot=mlogRoot();
            if(currentRoot==null || isBlockingOverlay(currentRoot)){
                trace("SCAN_BLOCKED_BY_OVERLAY");
                status("Feche informacoes/aviso antes da proxima leitura.");
                return;
            }
        }
        // Sem fila: nao executar posteriormente uma leitura antiga.
        // Novo gatilho admitido pode cancelar um ciclo anterior (ver onKeyEvent).
        if(!scanQueue.isIdle()){
            // So no modo nativo: reter eventos curtos, mas NAO tratar
            // nenhuma leitura como conferida ate a anterior ser concluida.
            if(prefs().getBoolean(NativeScannerController.PREF,false)){
                if(!scanQueue.offer(code)){
                    trace("NATIVE_QUEUE_FULL");
                    status("Leitor ocupado: capacidade de processamento atingida.");
                    return;
                }
                queuedAt.addLast(now);
                trace("NATIVE_SCAN_QUEUED_DEPTH_"+scanQueue.waitingCount());
                // Nao escrever SharedPreferences a cada bipe rapido.
                // Erros/limites continuam sendo informados imediatamente.
                return;
            }
            trace("SCAN_DISCARDED_BUSY_NO_QUEUE");
            status("Leitura muito rapida: aguarde a confirmacao no MLOG antes do proximo bipe.");
            return;
        }
        if(ScanAssistState.isArmed(this) && !autoCountRunning)armAutoCount(code);
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
        fixedEnterScheduled=false;
        insertedEditor=null;
        insertedEditorAt=0L;
        scanScreen="";
        onBarcode(code);
        // Fail-safe: nunca manter a fila bloqueada indefinidamente.
        main.postDelayed(()->{
            if(workGeneration!=epoch || scanQueue.isIdle() || fixedEnterScheduled)return;
            trace("SCAN_TIMEOUT_BEFORE_CODE_INSERTION");
            status("Nao foi possivel abrir/preencher o campo de leitura.");
            finishQueuedScan("timeout_antes_codigo");
        },prefs().getBoolean(NativeScannerController.PREF,false)?2300:5400);
    }
    private void finishQueuedScan(String reason){
        if(scanQueue.isIdle())return;
        trace("SCAN_FINISH_"+reason);
        awaitingEnterConfirmation=false;
        fixedEnterScheduled=false;
        directEnterSent=false;
        insertedEditor=null;
        insertedEditorAt=0L;
        scanScreen="";
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
        // O primeiro bipe segue confirmado pelo processamento normal do CMX.
        // Repeticoes posteriores sao opt-in e contam envios, nao registros gravados.
        if(autoCountRunning && autoCountFirst){
            autoCountFirst=false;
            if(AutoRepeatPolicy.firstConfirmed(reason)){
                autoCountDone=1;
                // campo_mudou confirma a transição do editor, mas não a gravação.
                // Quando o cartão 'SEPARADO' estiver disponível, exija incremento.
                if(autoCountObservedProduct!=null){
                    trace("AUTO_FIRST_WAITING_SEPARADO");
                    awaitFirstSeparated(autoCountToken,SystemClock.elapsedRealtime());
                }else{
                    ScanAssistState.report("Primeiro bipe tratado pelo MLOG; contador visual indisponível. " +
                        "Repetições sem confirmação de quantidade.");
                    scheduleAutoRepeat();
                }
            }else cancelAutoCount("O MLOG nao confirmou visualmente a 1ª leitura ("+reason+"). " +
                (autoCountFirstEnterAt>0?"Enter fisico observado, mas sem atualizacao do campo. ":"") +
                "Confira o MLOG antes de tentar novamente.");
        }
        ++workGeneration; // invalida timeout antigo
        if(more){
            // Breve intervalo para o Flutter recompor a arvore apos Enter.
            // O Flutter precisa reconstruir a arvore. Aguardar so um frame
            // curto; cada nova leitura voltara a verificar campo/foco.
            main.postDelayed(this::startQueuedScan,
                    prefs().getBoolean(NativeScannerController.PREF,false)?16:(fastImeMode()?16:65));
        }
    }


    /** O bloqueio de clique e SOMENTE temporal. Ao chegar um novo gatilho
     *  APOS o tempo configurado, descartamos qualquer tentativa antiga ainda aberta.
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

    /** Um novo bipe fecha SOMENTE o aviso conhecido de codigo de barras.
     * Nao confirma cancelamentos, finalizacoes, quantidades nem outros modais.
     * O codigo deste mesmo bipe NUNCA e inserido como produto.
     */
    private boolean tryDismissKnownBarcodeError(AccessibilityNodeInfo root){
        if(root==null)return false;
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        visit(root,nodes,0);
        boolean knownError=false;
        AccessibilityNodeInfo okNode=null;
        for(AccessibilityNodeInfo node:nodes){
            if(!node.isVisibleToUser())continue;
            String label=tag(node);
            if(BarcodeErrorDialogPolicy.isKnownNormalized(label))knownError=true;
            if(okNode==null && ("ok".equals(label.trim()) || "entendi".equals(label.trim()))){
                AccessibilityNodeInfo button=clickable(node);
                if(button!=null && button.isVisibleToUser() && button.isEnabled())
                    okNode=button;
            }
        }
        if(!knownError || okNode==null){
            if(!knownError)barcodeErrorHint=false;
            return false;
        }
        boolean clicked=false;
        try { clicked=okNode.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
        catch(RuntimeException ignored){}
        if(!clicked)return false;
        barcodeErrorHint=false;
        errorDismissGuardUntil=SystemClock.elapsedRealtime()+
            Math.max(180L,timedClickGate.getCooldownMs()+40L);
        if(!scanQueue.isIdle()){
            trace("ERROR_OK_INTERRUPTED_PENDING_SCANS");
            ++serial; ++enterAttempt; ++workGeneration;
            fixedEnterScheduled=false;cancelPending();scanQueue.clear();queuedAt.clear();
        }
        stopLongHold();
        trace("BARCODE_ERROR_OK_BY_NEXT_TRIGGER");
        return true;
    }

    /** Flutter pode desenhar dialogs no mesmo TYPE_APPLICATION. Essa guarda
     *  complementa o titulo detectado em inspect(), sem bloquear a pesquisa
     *  normal que possui a secao 'Informacoes Gerais' recolhida. */
    private boolean isBlockingOverlay(AccessibilityNodeInfo root){
        if(root==null)return true;
        CharSequence rootClass=root.getClassName();
        if(rootClass!=null && has(norm(rootClass),"dialog","popupwindow"))return true;
        // Anteriormente inspect() percorria ate 550 nos e visit() percorria
        // tudo uma SEGUNDA vez para cada tentativa. Verificacao unica.
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        visit(root,nodes,0);
        boolean modalTitle=false,close=false;
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser())continue;
            String name=tag(n).trim();
            if(has(name,"login","autenticacao","senha de acesso",
                    "atencao!","atividade cancelada","confirmar cancelamento"))return true;
            if(has(name,"informacoes do item", "informacoes do produto",
                    "detalhes do item", "detalhes do produto",
                    "cancelar separacao", "confirmar cancelamento",
                    "deseja cancelar", "cancelar atividade"))modalTitle=true;
            if(has(name,"fechar", "dispensar", "fechar janela", "voltar a separacao"))close=true;
            if(modalTitle && close)return true;
        }
        return false;
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
        if(prefs().getBoolean(ShizukuScanMode.PREF,false)){
            observeAndroidKeyboard(event);
            return;
        }
        if(prefs().getBoolean(ZebraImeMode.PREF,false)){
            handleZebraScreenEvent(event);
            return;
        }
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
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
           event.getEventType()==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED){
            for(CharSequence text:event.getText()){
                if(BarcodeErrorDialogPolicy.isKnownBarcodeError(text)){
                    barcodeErrorHint=true;
                    trace("BARCODE_ERROR_HINT_FROM_EVENT");
                    break;
                }
            }
            if(BarcodeErrorDialogPolicy.isKnownBarcodeError(event.getContentDescription()))
                barcodeErrorHint=true;
        }
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            lastKnownEditor=null;
            lastKnownScreen="";
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
        // O Flutter emite muitas mudanças do campo durante SET_TEXT e Enter.
        // No modo nativo, se o Enter ja foi agendado, a checagem de modal
        // ocorre no callback do proprio Enter. Evita varrer a mesma arvore
        // em cada CONTENT_CHANGED, mantendo WINDOW_STATE_CHANGED protegido.
        boolean nativeEnterContentEvent = fixedEnterScheduled &&
            prefs().getBoolean(NativeScannerController.PREF,false) &&
            event.getEventType()==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        if(!nativeEnterContentEvent &&
           !prefs().getBoolean(NativeScannerController.PREF,false) &&
           (event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType()==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) &&
           (!scanQueue.isIdle() || pendingCode!=null)){
            long overlayNow=SystemClock.elapsedRealtime();
            // Flutter pode emitir dezenas de CONTENT_CHANGED por codigo.
            // O teste critico ocorre novamente antes do Enter sem debounce.
            if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
               overlayNow-lastOverlayProbeAt>=90L){
                lastOverlayProbeAt=overlayNow;
                AccessibilityNodeInfo visible=mlogRoot();
                if(visible!=null && isBlockingOverlay(visible)){
                    suspendForNavigation("overlay");
                    return;
                }
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
        // v3.2.4: sem varrer a hierarquia quando o scanner esta ocioso.
        // O diagnostico manual continua disponivel sob demanda.
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
    private void observeLongHold(KeyEvent event) {
        if(event==null)return;
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_UP){
            if(longHoldPolicy.holdingKey(key)){
                boolean wasActive=longHoldPolicy.up(key);
                ++longHoldGeneration;
                currentLongHoldKey=-1;
                if(wasActive){NativeScannerController.stopContinuous(this);trace("LONG_HOLD_RELEASE_STOPPED");}
                else trace("LONG_HOLD_RELEASE_BEFORE_1000MS");
            }
            return;
        }
        if(!prefs().getBoolean(NativeScannerController.LONG_HOLD_PREF,false) ||
            !prefs().getBoolean(NativeScannerController.PREF,false) ||
            !ScanCoordinator.isActive(this) || event.getAction()!=KeyEvent.ACTION_DOWN ||
            event.getRepeatCount()!=0)return;
        int learned=prefs().getInt("learned_trigger_key",-1);
        // O firmware deste coletor expôs 277 no diagnostico do gatilho.
        if(!(isScannerTriggerKey(key) || key==277 || key==learned))return;
        // Nao percorrer a tela no ACTION_DOWN: o scanner nativo decodifica
        // independentemente do foco. A verificacao da janela ocorre ao iniciar.
        if(barcodeErrorHint && tryDismissKnownBarcodeError(mlogRoot()))return;
        long now=SystemClock.elapsedRealtime();
        if(!longHoldPolicy.down(key,now))return;
        currentLongHoldKey=key;
        int generation=++longHoldGeneration;
        trace("LONG_HOLD_ARMED_1000MS");
        main.postDelayed(()->{
            if(generation!=longHoldGeneration || !longHoldPolicy.shouldStart(SystemClock.elapsedRealtime()) ||
                !prefs().getBoolean(NativeScannerController.LONG_HOLD_PREF,false))return;
            AccessibilityNodeInfo visible=mlogRoot();
            if(visible==null){
                longHoldPolicy.reset();currentLongHoldKey=-1;return;
            }
            ScreenInfo holdScreen=inspect(visible);
            if(holdScreen.login || holdScreen.dialog || "desconhecida".equals(holdScreen.screen)){
                longHoldPolicy.reset();currentLongHoldKey=-1;return;
            }
            // Depois de 1 s: solicitar NOVOS acionamentos um por vez,
            // em vez de alterar o firmware para modo continuous. O bip
            // fisico inicial ja foi entregue pelo proprio gatilho.
            longHoldPolicy.started();
            trace("LONG_HOLD_REPEAT_STARTED");
            scheduleLongHoldPulse(generation);
            main.postDelayed(()->{
                if(generation==longHoldGeneration && longHoldPolicy.isContinuous()){
                    stopLongHold();
                    trace("LONG_HOLD_SAFETY_STOP_30S");
                }
            },LONG_HOLD_MAX_MS);
        },LongHoldScanPolicy.HOLD_MS);
    }


    /** Repeticoes do hardware enquanto o botao estiver fisicamente segurado.
     * Nao inventa codigos e nao envia Enter artificial para a mesma etiqueta.
     * Se o MLOG acumular leituras, adia novos acionamentos ao inves de encher
     * indefinidamente a fila. A opcao fica desligada por padrao.
     */
    private void scheduleLongHoldPulse(int generation){
        if(generation!=longHoldGeneration || !longHoldPolicy.isContinuous())return;
        long interval=LongHoldScanPolicy.pulseInterval(timedClickGate.getCooldownMs());
        main.postDelayed(()->{
            if(generation!=longHoldGeneration || !longHoldPolicy.isContinuous() ||
               !prefs().getBoolean(NativeScannerController.LONG_HOLD_PREF,false) ||
               !ScanCoordinator.isActive(this)){
                if(generation==longHoldGeneration)stopLongHold();
                return;
            }
            // Verificar o aplicativo apenas a cada 350 ms, nunca a arvore
            // inteira a cada pulso. Um aviso especifico interrompe a repeticao.
            long pulseNow=SystemClock.elapsedRealtime();
            if(barcodeErrorHint || pulseNow-lastPulseAppCheckAt>=350L){
                lastPulseAppCheckAt=pulseNow;
                if(barcodeErrorHint || mlogRoot()==null){
                    stopLongHold();
                    trace("LONG_HOLD_STOPPED_MLOG_NOT_READY");
                    return;
                }
            }
            if(LongHoldScanPolicy.canPulse(scanQueue.waitingCount(),pendingCode!=null)){
                if(!NativeScannerController.pulseTrigger(this)){
                    stopLongHold();
                    trace("LONG_HOLD_PULSE_UNAVAILABLE");
                    return;
                }
            }else trace("LONG_HOLD_PULSE_BACKPRESSURE");
            scheduleLongHoldPulse(generation);
        },interval);
    }

    void stopLongHold(){
        ++longHoldGeneration;
        if(longHoldPolicy.isContinuous())NativeScannerController.stopContinuous(this);
        longHoldPolicy.reset();currentLongHoldKey=-1;
    }

    @Override protected boolean onKeyEvent(KeyEvent event){
        if(prefs().getBoolean(ShizukuScanMode.PREF,false))return false;
        if(prefs().getBoolean(ZebraImeMode.PREF,false))return false;
        observeLongHold(event);
        if(event!=null && BridgeEnterPolicy.active(prefs().getBoolean(
                BridgeEnterPolicy.PREFERENCE,false)) &&
           (event.getKeyCode()==KeyEvent.KEYCODE_ENTER ||
            event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER) &&
           awaitingEnterConfirmation && !scanQueue.isIdle() &&
           event.getDeviceId()!=KeyCharacterMap.VIRTUAL_KEYBOARD &&
           (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD)==0 &&
           SystemClock.elapsedRealtime()-confirmStartedAt<1800 &&
           mlogRoot()!=null){
            // Um Enter fisico do CMX recebido DURANTE a leitura e consumido
            // no modo Bridge. Alguns CMX usam injecao por IME/sistema e nao
            // passam pelo filtro: sufixo OFF continua obrigatorio no CMX.
            trace("CMX_ENTER_FILTERED_IN_BRIDGE_MODE");
            return true;
        }
        if(event!=null && event.getAction()==KeyEvent.ACTION_UP &&
           (event.getKeyCode()==KeyEvent.KEYCODE_ENTER ||
            event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER))
            lastHardwareEnterAt=SystemClock.elapsedRealtime();
        // O CMX pode entregar Enter ao Android em separado do broadcast.
        // NUNCA consumimos essa tecla; servira para diagnosticar a primeira
        // confirmacao sem reemitir Enter.
        if(event!=null && autoCountRunning && autoCountFirst &&
            (event.getKeyCode()==KeyEvent.KEYCODE_ENTER ||
             event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER) &&
            event.getAction()==KeyEvent.ACTION_UP){
            autoCountFirstEnterAt=SystemClock.elapsedRealtime();
            trace("AUTO_FIRST_HARDWARE_ENTER_OBSERVED");
        }
        // No modo nativo, o ACTION_DOWN nao precisa procurar campo, janela,
        // botao de teclado nem executar pre-foco: o broadcast chega depois.
        // Quando houver aviso de codigo, pressionar gatilho fecha somente OK.
        if(event!=null && prefs().getBoolean(NativeScannerController.PREF,false) &&
           event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 &&
           ScanCoordinator.isActive(this)){
            int k=event.getKeyCode();
            int learned=prefs().getInt("learned_trigger_key",-1);
            if((isScannerTriggerKey(k) || k==277 || k==learned) && barcodeErrorHint &&
               tryDismissKnownBarcodeError(mlogRoot()))return true;
        }
        if(prefs().getBoolean(NativeScannerController.PREF,false))return false;
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
        if(autoCountRunning){
            trace("AUTO_COUNT_TRIGGER_IGNORED");
            return false; // CMX hardware permanece livre; Bridge nao toca MLOG
        }
        // No modo direto, o disparo e tratado SOMENTE quando chega o codigo.
        // Nao clicar nem rearmar duas vezes no botao antes da decodificacao.
        if(prefs().getBoolean(DirectScanPolicy.PREF,false)){
            trace("DIRECT_TRIGGER_PASSIVE_HARDWARE");
            return false;
        }
        // Bloqueio configuravel nos Ajustes Avancados; default 100 ms.
        // Continua independente do Enter do CMX.
        if(!timedClickGate.admitTrigger(now)){
            trace("CLICK_COOLDOWN_IGNORED_REMAINING_MS_"+timedClickGate.remainingMs(now));
            return false; // nunca consumir o gatilho do CMX
        }
        if(!prefs().getBoolean(DirectScanPolicy.PREF,false))
            discardOldCycleOnNewTrigger();
        lastTriggerPrefocus=now;
        trace("CLICK_COOLDOWN_STARTED_MS_"+timedClickGate.getCooldownMs());
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

        // Conferência de Venda: dois layouts verificados em prints 720x1440.
        // Antes do primeiro produto o par camera/teclado está mais acima.
        // Com cartão do produto, o mesmo par desce. Nunca confundir com a
        // tela antiga de busca da conferência, que usa um campo superior.
        if(ConferenceScreenPolicy.isMapped(info.screen)){
            AccessibilityNodeInfo target=findKeyboardTarget(info);
            if(target!=null && clickNativeKeyboardOnTrigger(target,info.screen))return false;
            Point size=realScreenSize();
            if(prefs().getBoolean("guided_taps",true) && supportedAspect(size) &&
               mappedConferenceVerified(info)){
                trace("TRIGGER_CONFERENCE_GUIDED_"+info.screen);
                triggerGesture(info.screen,size.x*ConferenceScreenPolicy.keyboardX(info.screen),
                    size.y*ConferenceScreenPolicy.keyboardY(info.screen));
            }else trace("CONFERENCE_KEYBOARD_NOT_SAFE_"+info.screen);
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

    private String zebraInitialScan;
    private long zebraInitialScanAt;
    /** Um bipe durante a abertura do primeiro campo, valido por no maximo 900ms. */
    String consumeZebraInitialScan(){
        String c=zebraInitialScan;
        long age=SystemClock.elapsedRealtime()-zebraInitialScanAt;
        zebraInitialScan=null;
        return c!=null && age>=0 && age<=900L?c:null;
    }
    private long lastZebraFocusRequestAt;
    /** Auxilio OPCIONAL de foco: jamais digita codigo nem envia Enter.
     * Funciona so em telas do MLOG reconhecidas; sem servico habilitado,
     * o operador toca no icone de teclado manualmente. */
    boolean requestZebraKeyboardFocus(){
        if(prefs().getBoolean(ZebraProfile0Mode.PREF,false))return false;
        if(!prefs().getBoolean(ZebraImeMode.PREF,false) ||
           !prefs().getBoolean(ZebraImeMode.AUTO_FOCUS_PREF,true))return false;
        if(BridgeScanInputMethodService.hasMlogInput())return true;
        long now=SystemClock.elapsedRealtime();
        if(now-lastZebraFocusRequestAt<650L)return true;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return false;
        ScreenInfo info=inspect(root);
        if(info.login || info.dialog || "desconhecida".equals(info.screen) ||
           isBlockingOverlay(root))return false;
        lastZebraFocusRequestAt=now;
        AccessibilityNodeInfo editor=safeEditor(info,true);
        if(editor!=null && editor.isEditable() && editor.isVisibleToUser() &&
           !editor.isPassword()){
            // Foco visual do Flutter nao prova que o Android entregou um
            // InputConnection ao IME. Em varios coletores o EditText aparece
            // como focused=true, mas o teclado so inicia apos ACTION_CLICK.
            boolean connected=BridgeScanInputMethodService.hasMlogInput();
            boolean focused=editor.isFocused();
            boolean clicked=false;
            if(!connected && editor.isClickable()){
                try{clicked=editor.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
                catch(RuntimeException ignored){}
            }
            if(!clicked && !focused)focused=focusOrClick(editor);
            trace(clicked?"ZEBRA_FOCUSED_EDITOR_CLICKED_FOR_IME":
                focused?"ZEBRA_FOCUS_EDITOR_REQUESTED":"ZEBRA_EDITOR_NOT_FOCUSABLE");
            return clicked || focused;
        }
        AccessibilityNodeInfo button="atividades".equals(info.screen)?
            activityKeyboard(info):findKeyboardTarget(info);
        boolean clicked=button!=null && clickNativeKeyboardOnTrigger(button,info.screen);
        trace(clicked?"ZEBRA_MLOG_KEYBOARD_OPEN_REQUESTED":
            "ZEBRA_KEYBOARD_ICON_NOT_EXPOSED");
        return clicked;
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

    /** Caminho rapido somente quando o editor anterior continua focado,
     * vazio e no mesmo windowId. Ao menor sinal de mudanca, usa o fluxo
     * completo de descoberta e mapeamento da tela.
     */
    private boolean tryReuseFocusedNativeEditor(AccessibilityNodeInfo root,String code){
        if(!prefs().getBoolean(NativeScannerController.PREF,false) ||
           lastKnownEditor==null || root==null || code==null || code.isEmpty() ||
           !NativeFocusReusePolicy.withinWindow(
               SystemClock.elapsedRealtime(),lastKnownEditorAt) ||
           lastKnownScreen.isEmpty())return false;
        // Uma janela modal no mesmo processo invalida o reuso.
        if(barcodeErrorHint)return false;
        AccessibilityNodeInfo editor=lastKnownEditor;
        try{
            if(!editor.refresh())return false;
            CharSequence text=editor.getText();
            // O MLOG precisa ter esvaziado o campo anterior por conta propria.
            // Nunca substituir um codigo pendente/humano neste atalho.
            if(!NativeFocusReusePolicy.editorReusable(
                    editor.getWindowId()==root.getWindowId(),
                    editor.isFocused(),editor.isVisibleToUser(),
                    editor.isEditable(),editor.isEnabled(),editor.isPassword(),
                    blockedLabel(tag(editor)),text==null || text.length()==0))return false;
            scanScreen=lastKnownScreen;
            ++serial;
            trace("NATIVE_FOCUS_REUSED_EMPTY_EDITOR");
            focusAndSet(editor,code);
            return true;
        }catch(RuntimeException ignored){return false;}
    }

    private void onBarcode(String code){
        lastAutoActionAt=SystemClock.elapsedRealtime();
        if(pendingCode!=null){status("Leitura anterior aguardando campo; mantendo fila.");return;}
        // Nao cancelar a confirmacao anterior simplesmente porque chegou
        // outro broadcast. A confirmacao nova sera identificada no queueEnter.
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null){status("Ignorado: MLOG nao esta na tela ativa.");finishQueuedScan("fora_do_mlog");return;}
        if(tryReuseFocusedNativeEditor(root,code))return;
        ScreenInfo i=inspect(root);
        if("desconhecida".equals(i.screen) && !i.login && !i.dialog &&
           i.editors.size()==1 && SystemClock.elapsedRealtime()-preparedAt<9000 &&
           ("atividades".equals(preparedScreen) || "consulta".equals(preparedScreen) ||
            ConferenceScreenPolicy.isMapped(preparedScreen) ||
            "conferencia_venda".equals(preparedScreen) || "separacao".equals(preparedScreen) ||
            "pedidos".equals(preparedScreen) || "notas".equals(preparedScreen))){
            // Modal sem titulo do Flutter: somente editor unico do MLOG e
            // somente apos preparacao de uma tela reconhecida.
            i.screen=preparedScreen;
        }
        if(i.dialog || barcodeErrorHint){
            if(tryDismissKnownBarcodeError(root)){
                finishQueuedScan("aviso_codigo_ok");return;
            }
        }
        if(i.login || i.dialog ||
           (!prefs().getBoolean(NativeScannerController.PREF,false) && isBlockingOverlay(root)) ||
           "desconhecida".equals(i.screen)){
            status("Leitura pausada: tela desconhecida ou aviso aberto.");finishQueuedScan("tela_bloqueada");return;
        }
        scanScreen=i.screen;
        // Diferenciar um editor QUALQUER do campo efetivamente aberto pelo
        // botao de teclado do MLOG. Antes, um editor desfocado bloqueava o
        // clique no botao e somente fazia o Gboard aparecer.
        AccessibilityNodeInfo ready=safeEditor(i,true);
        if(ready!=null && prefs().getBoolean(NativeScannerController.PREF,false) &&
           ConferenceScreenPolicy.isMapped(i.screen) && ready.isEditable() &&
           ready.isVisibleToUser() && !ready.isFocused()){
            // Se o Flutter deixou o editor visivel mas desfocado depois do
            // Enter anterior, focar o MESMO campo, nao tocar no botao que
            // pode alternar/fechar o input. Nao perde um segundo bipe.
            startPending(code,i.screen);
            focusOrClick(ready);
            retryFill(serial,i.screen);
            trace("NATIVE_NEXT_SCAN_REFOCUS_EDITOR");
            return;
        }
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

        // Conferência de Venda: o botão nativo aparece em duas alturas.
        // Somente clicar se o título e o estado do cartão forem reconhecidos.
        if(ConferenceScreenPolicy.isMapped(i.screen)){
            startPending(code,i.screen);
            if(isImeVisible()){
                retryFill(serial,i.screen);
                return;
            }
            AccessibilityNodeInfo button=findKeyboardTarget(i);
            if(button!=null && clickKeyboardOnce(button,i.screen)){
                retryFill(serial,i.screen);
                return;
            }
            if(prefs().getBoolean("guided_taps",true) &&
               mappedConferenceVerified(i)){
                tapKnownConferenceKeyboard(serial,i);
            }else{
                finishQueuedScan("conferencia_botao_nao_identificado");
                status("Conferência: botão de teclado não identificado; envie diagnóstico.");
            }
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
        pendingDeadline=SystemClock.elapsedRealtime()+
            (prefs().getBoolean(NativeScannerController.PREF,false)?1200:3400);
        pendingScreen=type;
        ++serial;
        trace("WAIT_EDITOR_"+type);
    }
    private void retryFill(int id,String intendedScreen){
        int[] intervals=prefs().getBoolean(NativeScannerController.PREF,false)?
                new int[]{15,40,80,130,190,280,400,550,750,1000,1190}:
                fastImeMode()?new int[]{25,60,110,175,260,390,600,900,1300,1900,2700,3100}:
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
                  ConferenceScreenPolicy.isMapped(intendedScreen) ||
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
            if(DirectScanPolicy.barcodeRepeatedInField(code,existing.toString().trim())){
                trace("DIRECT_DUPLICATE_TEXT_DETECTED");
                status("Codigo duplicado no campo: corrija a Saida de Foco do scanner.");
                finishQueuedScan("campo_concatenado");
                return;
            }
            if(code.equals(existing.toString().trim())){
                cancelPending();
                status("Codigo ja no campo: aguardando confirmacao.");
                awaitCmxConfirmation(code,0);
                return;
            }
            // Em Consulta/Conferencia, a caixa de busca conserva o ultimo codigo.
            // Uma NOVA bipagem pode substitui-lo, somente nessa tela reconhecida.
            String currentScreen=scanScreen;
            if(!prefs().getBoolean(NativeScannerController.PREF,false)){
                AccessibilityNodeInfo root=mlogRoot();
                ScreenInfo info=root==null?null:inspect(root);
                currentScreen=info==null?"":info.screen;
            }
            boolean newSearch="consulta".equals(currentScreen) ||
                "conferencia_venda".equals(currentScreen) ||
                (prefs().getBoolean(NativeScannerController.PREF,false) &&
                 ConferenceScreenPolicy.isMapped(currentScreen));
            boolean previouslyInserted=("atividades".equals(currentScreen) ||
                "separacao".equals(currentScreen)) &&
                existing.toString().trim().equals(lastEnteredByBridge) &&
                currentScreen.equals(lastEnteredScreen);
            // Se o wedge digitou a versao com artefatos, podemos substitui-la
            // SOMENTE quando sua limpeza resulta exatamente no mesmo codigo
            // recebido pelo broadcast. Nao alterar textos manuais diferentes.
            boolean wedgeEquivalent=ScanAssistState.isClean(this) &&
                code.equals(BarcodeCleaner.clean(existing.toString()));
            if(!newSearch && !previouslyInserted && !wedgeEquivalent){
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
            // O tipo de tela ja foi verificado na entrada desta leitura.
            // Evita percorrer toda a arvore Flutter pela 3a vez neste bipe.
            lastEnteredByBridge=code;
            lastEnteredScreen=scanScreen;
            insertedEditor=editor;
            insertedEditorAt=SystemClock.elapsedRealtime();
            cancelPending();
            trace("SET_TEXT_OK");
            // No modo nativo o fluxo normal nao atualiza SharedPreferences
            // em cada bipe. A escrita em disco compete com o agendamento
            // do Enter em aparelhos com CPU/flash lentos.
            if(!prefs().getBoolean(NativeScannerController.PREF,false)){
                status(prefs().getBoolean(DirectScanPolicy.PREF,false)?
                    "Codigo inserido: confirmacao pelo Bridge.":
                    "Leitura inserida: aguardando Enter do CMX.");
            }
            // O estado preparado sera limpo ao terminar ESTA leitura;
            // nao usar timer que possa apagar o estado da leitura seguinte.
            awaitCmxConfirmation(code,0);

        }catch(RuntimeException e){status("Falha de acessibilidade ao inserir o codigo.");finishQueuedScan("erro_set_text");}
    }
    // Envio de Enter proprio e opt-in. Sem modo proprio, observa o CMX.
    // MLOG pode atualizar o editor em outro frame depois do Enter do CMX.
    private long confirmStartedAt;
    private boolean directEnterSent; // sempre false nesta versao; preserva evento legado sem acao

    private void awaitCmxConfirmation(String expected,long ignoredDelay){
        if(prefs().getBoolean(NativeScannerController.PREF,false)){
            scheduleFixedEnter();
            return;
        }
        final long token=++enterAttempt;
        awaitingEnterConfirmation=true;
        directEnterSent=false;
        conferenceFallbackAttempted=false;
        nativeRefocusAttempted=false;
        confirmStartedAt=SystemClock.elapsedRealtime();
        trace(prefs().getBoolean(BridgeEnterPolicy.PREFERENCE,false)?
            "BRIDGE_ENTER_ARMED":"CMX_ENTER_PASSIVE_WAIT");
        main.postDelayed(()->observeCmxConfirmation(expected,token),
                prefs().getBoolean(NativeScannerController.PREF,false)?20:40);
    }

    /** Modo nativo: intervalo configurado vale para o inicio da leitura e
     * tambem para o Enter. O codigo ja foi inserido por ACTION_SET_TEXT.
     * Nunca espera alteracao do Flutter depois do envio do Enter.
     */
    private void scheduleFixedEnter(){
        final long token=++enterAttempt;
        awaitingEnterConfirmation=false;
        fixedEnterScheduled=true;
        long now=SystemClock.elapsedRealtime();
        long interval=timedClickGate.getCooldownMs();
        long delay=FixedEnterCadence.delayMs(currentScanStartedAt,
                lastFixedEnterAt,interval,now);
        trace("FIXED_ENTER_SCHEDULED_DELAY_MS_"+delay);
        final long intendedAt=now+delay;
        enterTimer.postDelayed(()->{
            trace("FIXED_ENTER_TIMER_LAG_MS_"+
                    Math.max(0L,SystemClock.elapsedRealtime()-intendedAt));
            fireFixedEnter(token);
        },delay);
    }

    // O Enter continua temporizado. A unica verificacao adicional ocorre
    // ANTES dele: comparar o texto editavel com o broadcast completo.
    // Nunca aguarda atualizacao de quantidade ou confirmacao DEPOIS do Enter.
    private static final long PRE_ENTER_REPAIR_DELAY_MS=35L;

    /** Reutiliza o editor SOMENTE dentro da mesma leitura (SET_TEXT -> Enter).
     * refresh() detecta nos descartados pelo Flutter; windowId impede usar
     * uma janela antiga. O teste de overlay continua independente.
     */
    private AccessibilityNodeInfo validatedInsertedEditor(AccessibilityNodeInfo root){
        AccessibilityNodeInfo e=insertedEditor;
        if(e==null || root==null || SystemClock.elapsedRealtime()-insertedEditorAt>1200L)
            return null;
        try{
            if(e.getWindowId()!=root.getWindowId() || !e.refresh() ||
               !e.isVisibleToUser() || !e.isEditable() || !e.isEnabled() ||
               e.isPassword() || blockedLabel(tag(e)))return null;
            return e;
        }catch(RuntimeException ignored){return null;}
    }

    private void fireFixedEnter(long token){
        fireFixedEnterChecked(token,false);
    }

    private void fireFixedEnterChecked(long token,boolean wasRepaired){
        if(token!=enterAttempt || scanQueue.isIdle() || !fixedEnterScheduled)return;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null || barcodeErrorHint){
            trace("FIXED_ENTER_CANCELLED_NOT_MLOG");
            status("Enter cancelado: MLOG nao esta ativo ou exibiu aviso.");
            finishQueuedScan("enter_cancelado");
            return;
        }
        // O ACTION_SET_TEXT do Android apenas informa que a acao foi
        // aceita. No Flutter, o campo pode ainda estar parcial em outro frame.
        // Recapturar o editor evita validar um AccessibilityNodeInfo antigo.
        String expected=scanQueue.active();
        AccessibilityNodeInfo editor=validatedInsertedEditor(root);
        boolean cachedEditorReady=editor!=null;
        if(cachedEditorReady)trace("FIXED_ENTER_EDITOR_FAST_PATH");
        if(editor==null){
            // Recuperacao quando o Flutter recriou o campo depois de SET_TEXT.
            ScreenInfo info=inspect(root);
            editor=safeEditor(info,true);
            trace("FIXED_ENTER_EDITOR_FULL_LOOKUP");
        }
        boolean focused=editor!=null && editor.isEditable() && editor.isEnabled() &&
                !editor.isPassword() && editor.isFocused();
        CharSequence actual=null;
        if(focused){
            try{
                // Cache ja passou no refresh() nesta chamada. Nao fazer
                // outra ida ao processo de acessibilidade sem necessidade.
                if(!cachedEditorReady && !editor.refresh())focused=false;
                else actual=editor.getText();
            }catch(RuntimeException ignored){focused=false;}
        }
        if(!focused || !BarcodePreEnterGuard.matches(expected,actual)){
            // Flutter pode estar trocando o editor no frame do Enter.
            // Uma unica espera curta evita cancelar por foco transitorio.
            if(!focused && !wasRepaired){
                trace("PRE_ENTER_EDITOR_RECHECK_ONCE");
                main.postDelayed(()->fireFixedEnterChecked(token,true),
                        PRE_ENTER_REPAIR_DELAY_MS);
                return;
            }
            // No maximo UMA correcao de ACTION_SET_TEXT. Sem loop de 1,5 s.
            if(!wasRepaired && focused && BarcodePreEnterGuard.canRepairPrefix(expected,actual)){
                try{
                    Bundle data=new Bundle();
                    data.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,expected);
                    if(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data)){
                        trace("PRE_ENTER_PARTIAL_REWRITE_ONCE");
                        main.postDelayed(()->fireFixedEnterChecked(token,true),
                                PRE_ENTER_REPAIR_DELAY_MS);
                        return;
                    }
                }catch(RuntimeException ignored){}
            }
            if(focused && !BarcodePreEnterGuard.canRepairPrefix(expected,actual))
                trace("PRE_ENTER_FOREIGN_TEXT_BLOCKED");
            trace(focused?"PRE_ENTER_INCOMPLETE_BLOCKED":"PRE_ENTER_EDITOR_NOT_READY_BLOCKED");
            status("Codigo incompleto no campo: Enter bloqueado. Bipe novamente.");
            // Nao confirmar textos parciais nem executar Enter para uma fila antiga.
            finishQueuedScan("codigo_incompleto_sem_enter");
            return;
        }
        // v3.2.4: nao percorrer novamente o cartao do produto antes do Enter.
        // A protecao aqui e SOMENTE editor correto + codigo completo.
        trace("PRE_ENTER_CODE_EXACT_MATCH");
        trace("FIXED_ENTER_TIMER_FIRED");
        boolean sent=BridgeScanInputMethodService.sendEnterForVerifiedScan();
        if(sent)trace("FIXED_ENTER_BRIDGE_IME");
        if(!sent){
            // Nenhuma tentativa dupla quando o teclado ja aceitou o Enter.
            if(Build.VERSION.SDK_INT>=30){
                try{sent=editor.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());}
                catch(RuntimeException ignored){}
                if(sent)trace("FIXED_ENTER_EDITOR_ACTION");
            }
            if(!sent){
                sent=sendAutoEnterByAccessibility();
                if(sent)trace("FIXED_ENTER_ACCESSIBILITY_FALLBACK");
            }
        }
        // Sem consulta repetitiva de quantidade/produto neste caminho rapido.
        lastFixedEnterAt=SystemClock.elapsedRealtime();
        if(sent && editor!=null && !scanScreen.isEmpty()){
            // Nao confundir "Enter enviado" com sucesso do pedido: o cache
            // so e usado se o editor real ainda estiver focado e vazio.
            lastKnownEditor=editor;
            lastKnownScreen=scanScreen;
            lastKnownEditorAt=lastFixedEnterAt;
        }else{
            lastKnownEditor=null;
            lastKnownScreen="";
            lastKnownEditorAt=0L;
        }
        trace(sent?"FIXED_ENTER_SENT_NO_VISUAL_WAIT":"FIXED_ENTER_UNAVAILABLE");
        if(!sent)status("Enter nao enviado. Confira o teclado Bridge e a acessibilidade.");
        finishQueuedScan(sent?"enter_temporizado_enviado":"enter_indisponivel");
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
        // Conferencia: recuperacao unica se o CMX nao enviou Enter e o
        // codigo continua no mesmo editor. Nunca repetir automaticamente.
        // Aguardar o wedge fisico antes de tentar, evitando dois Enters.
        boolean directMode=prefs().getBoolean(DirectScanPolicy.PREF,false);
        boolean nativeMode=prefs().getBoolean(NativeScannerController.PREF,false);
        boolean bridgeMode=prefs().getBoolean(BridgeEnterPolicy.PREFERENCE,false) || nativeMode;
        boolean screenVerified=mappedConferenceVerified(info) ||
            (directMode && "separacao".equals(info.screen) && info.hasSeparationControls);
        // No modo nativo, o Flutter pode desfocar a entrada apos SET_TEXT;
        // recuperar o campo sem consumir um SEGUNDO bipe fisico.
        if(nativeMode && !editor.isFocused() && !nativeRefocusAttempted &&
           waited>=45 && waited<700 && !conferenceFallbackAttempted && screenVerified){
            nativeRefocusAttempted=true;
            boolean refocused=focusOrClick(editor);
            trace(refocused?"NATIVE_REFOCUS_REQUESTED":"NATIVE_REFOCUS_FAILED");
            main.postDelayed(()->observeCmxConfirmation(expected,token),45);
            return;
        }
        // Em modo Bridge, Enter do CMX deve estar OFF no CMXScanner. Sem
        // isso o firmware pode confirmar fora dos KeyEvents observaveis.
        // Nunca enviar duas vezes para o mesmo ciclo/mesmo codigo.
        if(!conferenceFallbackAttempted &&
           BridgeEnterPolicy.shouldAttempt(bridgeMode,info.screen,waited,
              !nativeMode && lastHardwareEnterAt>=confirmStartedAt,editor.isFocused(),
              screenVerified,nativeMode?BridgeEnterPolicy.NATIVE_DELAY_MS:
              BridgeEnterPolicy.DELAY_MS)) {
            conferenceFallbackAttempted=true;
            // MESMO mecanismo primario observado no APK do CMXScanner:
            // getRootInActiveWindow().findFocus(FOCUS_INPUT)
            // .performAction(0x01020054 / ACTION_IME_ENTER).
            // Nosso editor ja foi verificado por origem, foco e conteudo.
            // No video o Android utiliza o IME do Bridge e o Flutter possui
            // InputConnection ativa. Priorizar DOWN/UP no IME: ACTION_IME_ENTER
            // do no de acessibilidade pode devolver true SEM o Flutter registrar.
            boolean sent=false;
            // O diagnostico de 24/10 mostrou que o par DOWN/UP emitido
            // pela InputConnection concluiu ciclos que o fallback de
            // acessibilidade declarava enviados, mas sem mudar o Flutter.
            // O caller verificou a tela, o campo focado e o codigo exato.
            if(nativeMode){
                sent=BridgeScanInputMethodService.sendEnterForVerifiedScan();
                if(sent)trace("NATIVE_ENTER_VERIFIED_IME_SENT");
            } else if(BridgeScanInputMethodService.hasLiveInputForMlog()){
                sent=BridgeScanInputMethodService.sendEnterForAutoCount();
                if(sent)trace("DIRECT_ENTER_IME_KEYPAIR_SENT");
            }
            // Se a InputConnection nao esta disponivel, usar a mesma acao
            // do CMX no editor focado; somente UMA tentativa por leitura.
            if(!sent && Build.VERSION.SDK_INT>=30 && editor.isFocused()){
                try{sent=editor.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());}
                catch(RuntimeException ignored){}
                if(sent)trace("ENTER_ACTION_IME_ENTER_SENT");
            }
            if(!sent){
                sent=sendAutoEnterByAccessibility();
                if(sent)trace("ENTER_ACCESSIBILITY_INPUT_METHOD_SENT");
            }
            if(sent)lastBridgeEnterAt=SystemClock.elapsedRealtime();
            trace(sent?"BRIDGE_ENTER_SENT_ONCE":"BRIDGE_ENTER_UNAVAILABLE");
            if(sent)status(nativeMode?
                "Enter nativo enviado; aguardando o MLOG atualizar.":
                "Enter do Bridge enviado. Verifique o registro no MLOG.");
            else status("Sem conexao para Enter do Bridge. Confira teclado/acessibilidade.");
        }
        if(waited>=1500){
            if(nativeMode)trace(conferenceFallbackAttempted?
                    "NATIVE_ENTER_UNVERIFIED_AFTER_ATTEMPT":"NATIVE_ENTER_NOT_AVAILABLE");
            else trace(directMode?"DIRECT_ENTER_UNVERIFIED":"CMX_ENTER_NOT_OBSERVED");
            status(nativeMode?"MLOG nao confirmou visualmente o Enter; confira antes de continuar.":
                directMode?"Enter enviado ou indisponivel; confira se o MLOG registrou.":
                "Codigo preenchido; Enter do CMX nao confirmado. Confira no MLOG.");
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
        // v2.9.7: nenhum segundo clique agendado. Falhas so geram diagnostico.
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
        if(ConferenceScreenPolicy.isMapped(info.screen)){
            // Prioridade ao ícone semântico, mas somente na área do botão
            // direito do par mostrado nas capturas de Conferência de Venda.
            if(info.keyboard!=null){
                AccessibilityNodeInfo target=clickable(info.keyboard);
                if(target!=null && target.isVisibleToUser() && target.isEnabled()){
                    Rect r=new Rect();target.getBoundsInScreen(r);
                    Point p=realScreenSize();
                    if(supportedAspect(p) && ConferenceScreenPolicy.rightButtonCandidate(
                        info.screen,r.centerX()/(float)p.x,r.centerY()/(float)p.y,
                        r.width()/(float)p.x,r.height()/(float)p.y))return target;
                }
            }
            return conferenceKeyboardPair(info);
        }
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
    /** Só usa o par camera/teclado no cartão de Conferência de Venda.
     * Cada controle deve ter dimensões plausíveis e ambos na mesma linha.
     * Com dois pares ou sem identificação confiável, retorna null. */
    private AccessibilityNodeInfo conferenceKeyboardPair(ScreenInfo info){
        if(!ConferenceScreenPolicy.isMapped(info.screen))return null;
        Point p=realScreenSize();
        if(!supportedAspect(p))return null;
        AccessibilityNodeInfo found=null;
        for(AccessibilityNodeInfo node:info.clicks){
            if(!node.isVisibleToUser() || !node.isEnabled() ||
               node.isEditable() || blockedLabel(tag(node)))continue;
            Rect r=new Rect();node.getBoundsInScreen(r);
            if(!ConferenceScreenPolicy.rightButtonCandidate(info.screen,
                r.centerX()/(float)p.x,r.centerY()/(float)p.y,
                r.width()/(float)p.x,r.height()/(float)p.y))continue;
            boolean cameraBeside=false;
            for(AccessibilityNodeInfo sibling:info.clicks){
                if(sibling==node || !sibling.isVisibleToUser() || !sibling.isEnabled())continue;
                Rect c=new Rect();sibling.getBoundsInScreen(c);
                if(ConferenceScreenPolicy.leftCameraCandidate(info.screen,
                    c.centerX()/(float)p.x,c.centerY()/(float)p.y,
                    c.width()/(float)p.x,c.height()/(float)p.y) &&
                    Math.abs(c.centerY()-r.centerY())<p.y*.035f){
                    cameraBeside=true;break;
                }
            }
            if(!cameraBeside)continue;
            if(found!=null && !sameBounds(found,node))return null;
            found=node;
        }
        if(found!=null)trace("CONFERENCE_KEYBOARD_PAIR_FOUND_"+info.screen);
        return found;
    }

    private void tapKnownConferenceKeyboard(int id,ScreenInfo info){
        Point size=realScreenSize();
        if(!supportedAspect(size) || !mappedConferenceVerified(info)){
            finishQueuedScan("conferencia_nao_homologada");
            status("Conferência: layout não identificado para toque guiado.");
            return;
        }
        float x=size.x*ConferenceScreenPolicy.keyboardX(info.screen);
        float y=size.y*ConferenceScreenPolicy.keyboardY(info.screen);
        trace("CONFERENCE_GUIDED_BUTTON_"+info.screen);
        tapAt(id,x,y,info.screen);
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
        boolean hasConferencePageTitle,hasConferenceInitialPrompt,hasConferenceProductAction,hasEntryConferenceTitle;
        boolean hasSeparationHeader,hasVolumeHeaderLabel,hasVolumeHeaderButton;
        AccessibilityNodeInfo keyboard,searchField;
        final List<AccessibilityNodeInfo> editors=new ArrayList<>();
        final List<AccessibilityNodeInfo> clicks=new ArrayList<>();
    }
    private static boolean mappedConferenceVerified(ScreenInfo info){
        boolean title=ConferenceScreenPolicy.ENTRY_PRODUCT.equals(info.screen)
            ?info.hasEntryConferenceTitle:info.hasConferencePageTitle;
        return ConferenceScreenPolicy.isStateVerified(info.screen,title,
            info.hasConferenceInitialPrompt,info.hasConferenceProductAction);
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
            // O diagnostico de 31/10 mostrou que a tela usada era "Separacao",
            // e nao "Conferencia de Venda". O botao Volume existe em ambas.
            if("separacao".equals(t.trim()))v.hasSeparationHeader=true;
            if(t.startsWith("volume:")){
                Rect volumeLabelBounds=new Rect();n.getBoundsInScreen(volumeLabelBounds);
                if(VolumeRecoveryTarget.isVerifiedHeaderLabel(volumeLabelBounds.left,
                    volumeLabelBounds.top,volumeLabelBounds.right,
                    volumeLabelBounds.bottom))v.hasVolumeHeaderLabel=true;
            }
            CharSequence nodeClass=n.getClassName();
            if(n.isClickable() && n.isEnabled() && nodeClass!=null &&
               "android.widget.Button".contentEquals(nodeClass)){
                Rect rect=new Rect();n.getBoundsInScreen(rect);
                if(VolumeRecoveryTarget.isVerifiedHeaderButton(
                    rect.left,rect.top,rect.right,rect.bottom))
                    v.hasVolumeHeaderButton=true;
            }
            if(has(t,"bipar atividades de separacao","bipar atividade de separacao","bipar atividades"))v.hasActivitiesTitle=true;
            if(has(t,"nenhuma atividade registrada"))v.noActivities=true;
            if(has(t,"iniciar"))v.hasStart=true;
            if(has(t,"busca da conferencia de venda"))v.hasConferenceTitle=true;
            if(has(t,"conferencia de venda"))v.hasConferencePageTitle=true;
            if(has(t,"conf. de entrada", "conf de entrada", "conferencia de entrada"))v.hasEntryConferenceTitle=true;
            if(has(t,"para iniciar a conferencia", "bipe qualquer produto"))v.hasConferenceInitialPrompt=true;
            if(has(t,"bipar produtos"))v.hasConferenceProductAction=true;
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
            boolean barcodeError=(has(t,"nao encontrado","nao localizado",
                "nao identificado","codigo invalido","codigo incorreto","produto invalido") &&
                BarcodeErrorDialogPolicy.isKnownNormalized(t));
            if(has(t,"atencao!","atividade cancelada","confirmar cancelamento") || barcodeError){
                v.dialog=true;
                if(barcodeError)barcodeErrorHint=true;
            }
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
        if(v.hasEntryConferenceTitle && v.hasConferenceProductAction)
            v.screen=ConferenceScreenPolicy.ENTRY_PRODUCT;
        else if(v.hasConferenceTitle)v.screen="conferencia_venda";
        else if(v.hasConferencePageTitle && v.hasConferenceProductAction)
            v.screen="conferencia_produto";
        else if(v.hasConferencePageTitle && v.hasConferenceInitialPrompt)
            v.screen="conferencia_inicio";
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
                ConferenceScreenPolicy.isMapped(info.screen) || "notas".equals(info.screen) || "pedidos".equals(info.screen) ||
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
    /** Interface do contador: apenas um bipe original; os seguintes sao
     *  simulacoes no campo do MLOG, e NAO comandos enviados ao CMX. */
    /** Extrai só o contador visual do cartão de separação, nunca o campo editável. */
    private SeparationVisualReceipt separationReceiptOnScreen(){
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null)return null;
        java.util.ArrayList<AccessibilityNodeInfo> nodes=new java.util.ArrayList<>();
        visit(root,nodes,0);
        for(AccessibilityNodeInfo node:nodes){
            if(node==null || !node.isVisibleToUser())continue;
            SeparationVisualReceipt result=SeparationVisualReceipt.parse(node.getContentDescription());
            if(result!=null)return result;
        }
        return null;
    }
    private SeparatedQuantityProbe.Result separatedOnScreen(){
        return separatedOnScreen(mlogRoot());
    }
    private SeparatedQuantityProbe.Result separatedOnScreen(AccessibilityNodeInfo root){
        if(root==null)return null;
        java.util.ArrayList<AccessibilityNodeInfo> nodes=new java.util.ArrayList<>();
        visit(root,nodes,0);
        for(AccessibilityNodeInfo node:nodes){
            if(node==null || !node.isVisibleToUser())continue;
            SeparatedQuantityProbe.Result result=SeparatedQuantityProbe.parse(node.getContentDescription());
            if(result!=null)return result;
        }
        return null;
    }

    private void armAutoCount(String code){
        // Motor preservado, mas a interface desativada nao pode armar um lote.
        if(!ScanAssistState.AUTO_BIPS_ENABLED || code==null || code.isEmpty())return;
        android.content.SharedPreferences p=prefs();
        int quantity=ScanAssistState.clamp(p.getInt(ScanAssistState.COUNT,1));
        p.edit().putBoolean(ScanAssistState.ARMED,false).apply();
        if(quantity<=1){ScanAssistState.report("Quantidade 1: leitura normal pelo CMX.");return;}
        // Android 14: a conexao da propria acessibilidade pode enviar Enter
        // mesmo quando o teclado Bridge nao for o IME selecionado.
        // Conferimos disponibilidade real apenas no momento da repeticao,
        // quando ja existe um editor ativo; nunca alternar teclado sozinho.
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null || isBlockingOverlay(root)){
            ScanAssistState.report("Contador nao iniciado: volte a uma tela de leitura.");return;
        }
        ScreenInfo info=inspect(root);
        if(info.login || info.dialog || "desconhecida".equals(info.screen)){
            ScanAssistState.report("Contador exige tela de leitura reconhecida.");return;
        }
        ++autoCountToken;
        autoCountRunning=true;
        autoCountFirst=true;
        autoCountFirstEnterAt=0L;
        autoCountCode=code;
        autoCountScreen=info.screen;
        autoCountTarget=quantity;
        autoCountDone=0;
        autoCountUiVerified=0;
        autoCountUnverified=0;
        autoCountObservedProduct=separatedOnScreen();
        autoCountBeforeEnter=autoCountObservedProduct==null?-1:autoCountObservedProduct.count;
        autoCountClicked=false;
        autoCountLastActionAt=SystemClock.elapsedRealtime();
        trace("AUTO_COUNT_ARMED_TOTAL_"+quantity);
        ScanAssistState.report("Bipes automáticos: aguardando confirmação do 1º bipe físico; faltam "+(quantity-1)+" virtuais.");
    }

    /** Automatic repeats via the SAME Shizuku text+Enter pipeline as physical scans.
     *  We never use the old IME/Accessibility Enter path in Shizuku mode.
     *  No retry after uncertain delivery. Requires a visible count before arming.
     */
    private void startShizukuBatch(String barcode){
        prefs().edit().putBoolean(ScanAssistState.ARMED,false).apply();
        if(!ScanAssistState.isBatch(this) || !ShizukuScanMode.supported(barcode) ||
            !ShizukuHealthMonitor.ready()){
            ScanAssistState.report("Lote não iniciado: Shizuku indisponível ou código inválido.");
            return;
        }
        int total=ScanAssistState.clamp(prefs().getInt(ScanAssistState.COUNT,1));
        if(total<=1){ScanAssistState.report("Total 1: bipe normal.");return;}
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null || isBlockingOverlay(root)){
            ScanAssistState.report("Lote não iniciado: tela MLOG bloqueada.");return;
        }
        ScreenInfo info=inspect(root);
        SeparatedQuantityProbe.Result baseline=separatedOnScreen(root);
        if(!"separacao".equals(info.screen) || info.dialog || info.login || baseline==null){
            ScanAssistState.report("Lote não iniciado: abra Separação com contador SEPARADO visível.");
            return;
        }
        shizukuBatchActive=true;
        shizukuBatchGeneration++;
        shizukuBatchTarget=total;
        shizukuBatchDispatched=1; // first physical scan follows immediately
        shizukuBatchBarcode=barcode;
        shizukuBatchScreen=info.screen;
        shizukuBatchObserved=baseline;
        shizukuBatchDispatchedAt=SystemClock.elapsedRealtime();
        ScanAssistState.report("Bipe 1/"+total+" enviado; aguardando confirmação na Separação.");
        final int generation=shizukuBatchGeneration;
        main.postDelayed(()->checkShizukuBatch(generation),120);
    }

    /** Fail closed if count doesn't increase exactly by one in 5 seconds. */
    private void checkShizukuBatch(int generation){
        if(!shizukuBatchActive || generation!=shizukuBatchGeneration)return;
        if(!ScanAssistState.isBatch(this) || !prefs().getBoolean(ShizukuScanMode.PREF,false) ||
            !ShizukuHealthMonitor.ready()){
            stopShizukuBatch("Lote interrompido: Shizuku desligado ou opção desativada.");return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null || isBlockingOverlay(root)){
            stopShizukuBatch("Lote interrompido: tela MLOG indisponível.");return;
        }
        ScreenInfo info=inspect(root);
        if(info.dialog || info.login || !shizukuBatchScreen.equals(info.screen)){
            stopShizukuBatch("Lote interrompido: tela de Separação mudou.");return;
        }
        SeparatedQuantityProbe.Result result=separatedOnScreen(root);
        if(result!=null && shizukuBatchObserved!=null &&
            shizukuBatchObserved.sameProduct(result)){
            int delta=result.count-shizukuBatchObserved.count;
            if(delta==1){
                shizukuBatchObserved=result;
                int done=shizukuBatchDispatched;
                ScanAssistState.report("MLOG confirmou "+done+"/"+shizukuBatchTarget+
                    " leituras (SEPARADO +1).");
                if(done>=shizukuBatchTarget){
                    stopShizukuBatch("Lote concluído: "+done+
                        " leituras verificadas na tela. Confira o pedido.");return;
                }
                // Fast but never overlap a pending barcode/Enter injection.
                long wait=Math.max(120L,450L-(SystemClock.elapsedRealtime()-shizukuBatchDispatchedAt));
                main.postDelayed(()->sendNextShizukuBatch(generation),wait);
                return;
            }
            if(delta>1 || delta<0){
                stopShizukuBatch("Lote parado: contador mudou "+delta+
                    " unidades. Confira possíveis duplicidades.");return;
            }
        }else if(result!=null){
            stopShizukuBatch("Lote parado: produto mudou. Confira a quantidade.");return;
        }
        if(SystemClock.elapsedRealtime()-shizukuBatchDispatchedAt>5000L){
            stopShizukuBatch("Lote parado: MLOG não confirmou o incremento em 5 s. Não reenviado.");
            return;
        }
        main.postDelayed(()->checkShizukuBatch(generation),100);
    }
    private void sendNextShizukuBatch(int generation){
        if(!shizukuBatchActive || generation!=shizukuBatchGeneration)return;
        // Recheck state and UI immediately before each send.
        if(!ScanAssistState.isBatch(this) || !ShizukuHealthMonitor.ready() ||
            shizukuScan==null || !ShizukuScanMode.supported(shizukuBatchBarcode)){
            stopShizukuBatch("Lote parado: Shizuku indisponível.");return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null || isBlockingOverlay(root)){
            stopShizukuBatch("Lote parado: MLOG indisponível.");return;
        }
        ScreenInfo info=inspect(root);
        SeparatedQuantityProbe.Result current=separatedOnScreen(root);
        if(info.dialog || info.login || !shizukuBatchScreen.equals(info.screen) ||
           current==null || shizukuBatchObserved==null ||
           !shizukuBatchObserved.sameProduct(current) ||
           current.count!=shizukuBatchObserved.count){
            stopShizukuBatch("Lote parado: estado do produto não corresponde ao último bipe.");return;
        }
        // No retry if command fails after text injection: operator must inspect MLOG.
        if(!shizukuScan.scan(shizukuBatchBarcode)){
            stopShizukuBatch("Lote parado: Shizuku não aceitou a próxima leitura.");return;
        }
        shizukuBatchDispatched++;
        shizukuBatchDispatchedAt=SystemClock.elapsedRealtime();
        ScanAssistState.report("Enviado "+shizukuBatchDispatched+"/"+shizukuBatchTarget+
            "; aguardando SEPARADO aumentar 1.");
        main.postDelayed(()->checkShizukuBatch(generation),120);
    }
    private void stopShizukuBatch(String message){
        if(!shizukuBatchActive)return;
        shizukuBatchActive=false;
        shizukuBatchGeneration++;
        shizukuBatchBarcode=null;
        shizukuBatchObserved=null;
        prefs().edit().putBoolean(ScanAssistState.ARMED,false).apply();
        if(message!=null)ScanAssistState.report(message);
    }

    void onCmxEnterObservedByKeyboard(){
        if(autoCountRunning && autoCountFirst){
            autoCountFirstEnterAt=SystemClock.elapsedRealtime();
            trace("AUTO_FIRST_ENTER_BRIDGE_KEYBOARD_OBSERVED");
        }
    }

    /** Botao cancelar acessivel a partir da janela flutuante. */
    void cancelAutoCount(String message){
        stopShizukuBatch(message);
        boolean wasRunning=autoCountRunning;
        ++autoCountToken;
        autoCountRunning=false;
        autoCountFirst=false;
        autoCountFirstEnterAt=0L;
        autoCountCode=null;
        autoCountScreen=null;
        autoCountObservedProduct=null;
        autoCountClicked=false;
        prefs().edit().putBoolean(ScanAssistState.ARMED,false).apply();
        if(wasRunning)trace("AUTO_COUNT_CANCELLED_AT_"+autoCountDone);
        if(message!=null)ScanAssistState.report(message);
    }

    /** Evita considerar a primeira digitação como produto confirmado antes
     * de o cartão 'SEPARADO' realmente mudar. */
    private void awaitFirstSeparated(int token,long startedAt){
        if(token!=autoCountToken || !autoCountRunning || autoCountFirst)return;
        SeparatedQuantityProbe.Result now=separatedOnScreen();
        if(now!=null && autoCountObservedProduct!=null){
            if(!autoCountObservedProduct.sameProduct(now)){
                cancelAutoCount("Produto mudou antes da confirmação do primeiro bipe. Confira o pedido.");return;
            }
            int delta=now.count-autoCountObservedProduct.count;
            if(delta==1){
                autoCountUiVerified=1;
                autoCountObservedProduct=now;
                trace("AUTO_FIRST_SEPARADO_CONFIRMED");
                ScanAssistState.report("Primeiro bipe visto em SEPARADO; iniciando "+
                    (autoCountTarget-1)+" repetições.");
                scheduleAutoRepeat();return;
            }
            if(delta>1){
                cancelAutoCount("SEPARADO subiu mais de 1 unidade no primeiro bipe. Confira o pedido.");return;
            }
        }
        if(SystemClock.elapsedRealtime()-startedAt>=1800L){
            trace("AUTO_FIRST_SEPARADO_NOT_CONFIRMED");
            cancelAutoCount("Campo reagiu ao primeiro bipe, mas SEPARADO não aumentou em 1,8 s. " +
                "Confira o MLOG; não iniciei as repetições.");return;
        }
        main.postDelayed(()->awaitFirstSeparated(token,startedAt),90);
    }

    /** Intervalo mínimo entre Enters virtuais, não confirmação de gravação pelo Flutter. */
    private void scheduleAutoRepeat(){
        if(!autoCountRunning || autoCountFirst)return;
        if(autoCountDone>=autoCountTarget){
            ScanAssistState.report("Lote finalizado: "+autoCountDone+" envios, "+
                autoCountUiVerified+" com resposta visual observada. Confira o pedido no MLOG.");
            trace("AUTO_COUNT_FINISHED");
            cancelAutoCount(null);
            return;
        }
        final int token=autoCountToken;
        long delay=AutoRepeatPolicy.delayMs(autoCountLastActionAt,
                SystemClock.elapsedRealtime());
        main.postDelayed(()->beginAutoRepeat(token),delay);
    }

    private void beginAutoRepeat(int token){
        if(token!=autoCountToken || !autoCountRunning || autoCountFirst)return;
        autoCountClicked=false;
        // Flutter pode fechar a janela do IME ao receber Enter, mas manter
        // EditText marcado como focado. Esperar reabertura REAL antes de escrever.
        autoCountDeadline=SystemClock.elapsedRealtime()+6000;
        trace("AUTO_COUNT_NEXT_"+(autoCountDone+1));
        pollAutoRepeatEditor(token);
    }

    private void pollAutoRepeatEditor(int token){
        if(token!=autoCountToken || !autoCountRunning || autoCountFirst)return;
        if(SystemClock.elapsedRealtime()>autoCountDeadline){
            cancelAutoCount("Nao foi possivel abrir campo para item "+(autoCountDone+1)+". Parei sem reenviar.");return;
        }
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null || isBlockingOverlay(root)){
            cancelAutoCount("MLOG saiu da tela ou mostrou aviso; contador interrompido.");return;
        }
        ScreenInfo info=inspect(root);
        if(info.login || info.dialog || !autoCountScreen.equals(info.screen)){
            cancelAutoCount("Tela mudou durante o contador; interrompido.");return;
        }
        AccessibilityNodeInfo editor=safeEditor(info,true);
        // V3.0.4: campo focado != teclado aberto. No diagnostico da v3.0.3,
        // InputMethod sumiu entre o segundo e o terceiro bipe, mas o editor
        // continuou focado e o Bridge usou uma conexao IME antiga.
        // Nunca inserir/enviar Enter virtual sem reabrir uma janela IME ativa.
        if(!isImeVisible()){
            if(!autoCountClicked){
                trace("AUTO_IME_HIDDEN_REOPEN_"+(autoCountDone+1));
                AccessibilityNodeInfo button="atividades".equals(info.screen)?
                    activityKeyboard(info):findKeyboardTarget(info);
                boolean requested=false;
                if(button!=null && button.isEnabled() && button.isVisibleToUser()){
                    AccessibilityNodeInfo target=clickable(button);
                    Rect bounds=new Rect();target.getBoundsInScreen(bounds);
                    if(!bounds.isEmpty() && !isPointCoveredByIme(bounds.centerX(),bounds.centerY())){
                        try{requested=target.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
                        catch(RuntimeException ignored){}
                        if(requested){
                            markNativeButton(info.screen,"auto_reabrir_ime");
                            trace("AUTO_NATIVE_REOPEN_CLICK_"+(autoCountDone+1));
                        }
                    }
                }
                // Alguns layouts Flutter nao expoem mais o botao depois de
                // abrir o editor. Tocar NO EDITOR correto pode reconectar o IME.
                if(!requested && editor!=null && editor.isEnabled() &&
                   editor.isVisibleToUser() && editor.isEditable() &&
                   !editor.isPassword() && !blockedLabel(tag(editor))){
                    requested=focusOrClick(editor);
                    if(requested)trace("AUTO_EDITOR_REOPEN_CLICK_"+(autoCountDone+1));
                }
                // Uma unica tentativa de reabertura; sem toques pendentes.
                autoCountClicked=requested;
                if(!requested){
                    trace("AUTO_IME_REOPEN_UNAVAILABLE_"+(autoCountDone+1));
                }
            }
            main.postDelayed(()->pollAutoRepeatEditor(token),110);
            return;
        }
        if(editor!=null && nativeEntryReady(info,editor) &&
           editor.isEditable() && !editor.isPassword() && !blockedLabel(tag(editor))){
            if(!editor.isFocused()){
                focusOrClick(editor);
                main.postDelayed(()->pollAutoRepeatEditor(token),45);return;
            }
            if(!BridgeScanInputMethodService.hasLiveInputForMlog()){
                // A janela pode estar aberta mas o Flutter ainda nao associou
                // uma nova conexao ao editor. Uma unica tentativa de clique
                // no CAMPO correto, nunca outro botao de produto.
                traceOnceWaitingImeConnection();
                long now=SystemClock.elapsedRealtime();
                if(autoCountImeWaitStartedAt==0L)autoCountImeWaitStartedAt=now;
                if(now-autoCountImeWaitStartedAt>350L && !autoCountEditorReconnectTried){
                    autoCountEditorReconnectTried=true;
                    if(focusOrClick(editor))trace("AUTO_EDITOR_RECONNECT_CLICK_"+(autoCountDone+1));
                }
                main.postDelayed(()->pollAutoRepeatEditor(token),90);
                return;
            }
            trace("AUTO_IME_READY_"+(autoCountDone+1));
            // O Flutter pode conservar o texto da bipagem anterior mesmo apos Enter.
            // Forcar uma transicao texto antigo -> vazio -> mesmo codigo para a nova
            // repeticao ter um evento de alteracao real; nunca tocar outro campo.
            CharSequence previous=editor.getText();
            if(AutoRepeatPolicy.refreshSameCode(previous,autoCountCode,autoCountDone)){
                Bundle empty=new Bundle();
                empty.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"");
                boolean cleared=false;
                try{cleared=editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,empty);}
                catch(RuntimeException ignored){}
                if(!cleared){
                    trace("AUTO_COUNT_CLEAR_FAILED_RETRY");
                    main.postDelayed(()->pollAutoRepeatEditor(token),45);return;
                }
                trace("AUTO_COUNT_STALE_FIELD_CLEARED");
            }
            Bundle bundle=new Bundle();
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    autoCountCode);
            boolean inserted=false;
            try{inserted=editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,bundle);}
            catch(RuntimeException ignored){}
            if(!inserted){
                main.postDelayed(()->pollAutoRepeatEditor(token),45);return;
            }
            trace("AUTO_COUNT_SET_TEXT_OK");
            main.postDelayed(()->submitAutoRepeatEnter(token),180);
            return;
        }
        if(!autoCountClicked){
            AccessibilityNodeInfo button="atividades".equals(info.screen)?
                activityKeyboard(info):findKeyboardTarget(info);
            if(button!=null && button.isEnabled() && button.isVisibleToUser()){
                AccessibilityNodeInfo target=clickable(button);
                Rect bounds=new Rect();target.getBoundsInScreen(bounds);
                if(!bounds.isEmpty() && !isPointCoveredByIme(bounds.centerX(),bounds.centerY())){
                    try{
                        if(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                            autoCountClicked=true;
                            markNativeButton(info.screen,"contador_assistido");
                            trace("AUTO_COUNT_NATIVE_CLICK_OK");
                        }
                    }catch(RuntimeException ignored){}
                }
            }
        }
        // Nunca repetir o clique se o Flutter estiver demorando para expor o editor.
        main.postDelayed(()->pollAutoRepeatEditor(token),55);
    }

    /** Fallback Android 13+: Enter apenas no editor reconhecido do MLOG.
     *  O envio nao comprova que o Flutter gravou a operacao.
     */
    private boolean sendAutoEnterByAccessibility(){
        if(Build.VERSION.SDK_INT<33)return false;
        try{
            InputMethod method=getInputMethod();
            if(method==null || method.getCurrentInputConnection()==null)return false;
            EditorInfo editor=method.getCurrentInputEditorInfo();
            if(editor==null || !ScanCoordinator.TARGET.equals(editor.packageName))return false;
            int kind=editor.inputType & android.text.InputType.TYPE_MASK_VARIATION;
            if(kind==android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
               kind==android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)return false;
            InputMethod.AccessibilityInputConnection conn=method.getCurrentInputConnection();
            long now=SystemClock.uptimeMillis();
            conn.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER,0,0,0,0,KeyEvent.FLAG_SOFT_KEYBOARD));
            conn.sendKeyEvent(new KeyEvent(now,now+1,KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ENTER,0,0,0,0,KeyEvent.FLAG_SOFT_KEYBOARD));
            trace("AUTO_COUNT_ENTER_ACCESSIBILITY_IME");
            return true;
        }catch(RuntimeException exception){
            trace("AUTO_COUNT_ACCESSIBILITY_IME_UNAVAILABLE");
            return false;
        }
    }

    private void submitAutoRepeatEnter(int token){
        if(token!=autoCountToken || !autoCountRunning || autoCountFirst)return;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null || isBlockingOverlay(root)){
            cancelAutoCount("Janela mudou antes do Enter; contador interrompido.");return;
        }
        ScreenInfo info=inspect(root);
        if(!autoCountScreen.equals(info.screen)){
            cancelAutoCount("Tela mudou antes do Enter; contador interrompido.");return;
        }
        AccessibilityNodeInfo editor=safeEditor(info,true);
        if(!AutoRepeatPolicy.canSubmit(isImeVisible(),
                BridgeScanInputMethodService.hasLiveInputForMlog())){
            trace("AUTO_IME_LOST_BEFORE_ENTER_"+(autoCountDone+1));
            // Ainda nao enviamos Enter para este item: reabrir o teclado
            // uma vez, usando o prazo e a mesma quantidade pendente.
            autoCountClicked=false;
            main.postDelayed(()->pollAutoRepeatEditor(token),85);
            return;
        }
        if(editor==null || !editor.isFocused() || editor.isPassword() ||
           editor.getText()==null || !autoCountCode.equals(editor.getText().toString().trim())){
            cancelAutoCount("Nao confirmei que o codigo estava no campo. Parei sem Enter.");return;
        }
        // Capturar o contador ANTES do Enter para verificar se o Flutter
        // realmente incrementou 'SEPARADO' depois da operação.
        SeparatedQuantityProbe.Result before=separatedOnScreen();
        autoCountBeforeEnter=(before!=null && autoCountObservedProduct!=null &&
            autoCountObservedProduct.sameProduct(before))?before.count:-1;
        // Preferir ação de editor (Flutter onSubmitted) no teclado Bridge.
        boolean sent=BridgeScanInputMethodService.sendEnterForAutoCount();
        if(sent){trace("AUTO_COUNT_ENTER_BRIDGE_IME");}
        else sent=sendAutoEnterByAccessibility();
        if(!sent && Build.VERSION.SDK_INT>=30 &&
           supports(editor,AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())){
            try{
                sent=editor.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                if(sent)trace("AUTO_COUNT_ENTER_EDITOR_ACTION");
            }catch(RuntimeException ignored){}
        }
        if(!sent){
            cancelAutoCount("Enter automatico indisponivel para este campo. " +
                "Abra o teclado nativo do MLOG e selecione o teclado Bridge; tente de novo.");return;
        }
        autoCountLastActionAt=SystemClock.elapsedRealtime();
        autoCountEnterAt=autoCountLastActionAt;
        autoCountDone=AutoRepeatPolicy.afterEnterDispatched(autoCountDone,autoCountTarget);
        trace("AUTO_COUNT_ENTER_DISPATCHED_"+autoCountDone);
        trace("AUTO_COUNT_SLOW_ENTER_CADENCE_MS_"+AutoRepeatPolicy.MIN_INTERVAL_MS);
        ScanAssistState.report("Enters enviados: "+autoCountDone+"/"+autoCountTarget+
            "; confirmações observadas: "+autoCountUiVerified+". Aguardando MLOG.");
        // O retorno sendKeyEvent=true NAO comprova processamento pelo Flutter.
        // Esperar a contagem SEPARADO aumentar, sem re-enviar o mesmo Enter.
        main.postDelayed(()->observeAutoRepeatResult(token),150);
    }

    /** Confirmação por contador de separação quando disponível.
     * Se não houver contador exposto, aplicar uma cadência conservadora,
     * mas não classificar a leitura como confirmada.
     */
    private void observeAutoRepeatResult(int token){
        if(token!=autoCountToken || !autoCountRunning || autoCountFirst)return;
        SeparatedQuantityProbe.Result now=separatedOnScreen();
        if(autoCountBeforeEnter>=0 && autoCountObservedProduct!=null){
            if(now!=null && autoCountObservedProduct.sameProduct(now)){
                if(now.count>autoCountBeforeEnter){
                    int delta=now.count-autoCountBeforeEnter;
                    if(delta>1){
                        trace("AUTO_COUNT_UNEXPECTED_UI_JUMP_"+delta);
                        cancelAutoCount("MLOG aumentou "+delta+" unidades de uma vez. " +
                            "Parei para evitar quantidade duplicada. Confira o pedido.");return;
                    }
                    autoCountUiVerified++;
                    autoCountObservedProduct=now;
                    trace("AUTO_COUNT_SEPARADO_CONFIRMED_"+autoCountUiVerified);
                    ScanAssistState.report("Confirmados na tela: "+autoCountUiVerified+
                        "/"+autoCountTarget+"; Enters enviados: "+autoCountDone+".");
                    scheduleAutoRepeat();return;
                }
            }else if(now!=null && !autoCountObservedProduct.sameProduct(now)){
                // O MLOG pode avançar para o próximo item quando a última
                // unidade foi efetivamente recebida.
                if(autoCountDone>=autoCountTarget){
                    autoCountUiVerified++;
                    trace("AUTO_COUNT_PRODUCT_ADVANCED_FINAL");
                    scheduleAutoRepeat();
                }else cancelAutoCount("MLOG mudou de produto antes de completar o lote. " +
                    "Confira quantos registros foram lançados.");
                return;
            }
            // Acessibilidade do Flutter nem sempre expõe o incremento do cartão
            // no momento em que o pedido já processou a confirmação.
            // Sem incremento, aguardar uma cadência conservadora, registrar a
            // falta de evidência e continuar apenas enquanto campo/produto
            // permanecerem consistentes. Não classificar envio como gravação.
            if(SystemClock.elapsedRealtime()-autoCountEnterAt<AutoRepeatPolicy.MIN_INTERVAL_MS){
                main.postDelayed(()->observeAutoRepeatResult(token),120);return;
            }
            autoCountUnverified++;
            trace("AUTO_COUNT_UNVERIFIED_CONTINUING_"+autoCountDone);
            ScanAssistState.report("Enters enviados "+autoCountDone+"/"+autoCountTarget+
                "; confirmações visíveis "+autoCountUiVerified+
                "; "+autoCountUnverified+" sem confirmação. Conferir pedido.");
            scheduleAutoRepeat();
            return;
        }
        // Consultas/atividades podem não expor quantidade separada.
        // Nelas mantemos tentativas com cadência maior e aviso explícito.
        if(SystemClock.elapsedRealtime()-autoCountEnterAt<650L){
            main.postDelayed(()->observeAutoRepeatResult(token),100);return;
        }
        autoCountUnverified++;
        trace("AUTO_COUNT_NO_VISIBLE_METER_"+autoCountDone);
        ScanAssistState.report("Enviados "+autoCountDone+"/"+autoCountTarget+
            "; sem contador visível nesta tela ("+autoCountUnverified+" sem confirmação).");
        scheduleAutoRepeat();
    }

    @Override public void onInterrupt(){lastKnownEditor=null;lastKnownScreen="";stopLongHold();cancelAutoCount("Acessibilidade interrompida; contador cancelado.");cancelPending();scanQueue.clear();++workGeneration;if(!prefs().getBoolean(ShizukuScanMode.PREF,false))restoreKeyboard();status("Acessibilidade interrompida.");}
    @Override public void onDestroy(){
        ++recoveryEpoch;
        recoveryPolicy.finish();
        lastKnownEditor=null;
        lastKnownScreen="";
        stopLongHold();
        cancelAutoCount("Acessibilidade encerrada; contador cancelado.");
        if(receiverRegistered)try{unregisterReceiver(receiver);}catch(RuntimeException ignored){}
        if(shizukuScan!=null)shizukuScan.stop();
        main.removeCallbacksAndMessages(null);
        enterTimer.removeCallbacksAndMessages(null);
        cancelPending();
        scanQueue.clear();
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
