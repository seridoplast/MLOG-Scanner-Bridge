package br.com.seridoplast.mlogbridge;

import android.inputmethodservice.InputMethodService;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.util.ArrayDeque;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * IME experimental, habilitavel pelo usuario.
 * Nao coleta/loga dados digitados e nao tem permissao de rede.
 * Enquanto selecionado, eh um IME do sistema. Android NAO permite que apps
 * comuns alternem automaticamente entre IMEs por gatilho.
 * No MLOG: barra compacta por padrao (sem teclado grande).
 * Outras apps: teclado manual simples para nao impedir digitacao.
 */
public final class BridgeScanInputMethodService extends InputMethodService {
    private static BridgeScanInputMethodService active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout panel;
    private boolean targetMlog=false;
    private boolean manual=false;
    private boolean numeric=false;
    private boolean upper=false;

    // Modo Zebra (opt-in): servico nativo -> broadcast -> InputConnection MLOG.
    // Este caminho NAO usa nem requer a AccessibilityService. Nenhum dado fica em disco.
    private BroadcastReceiver zebraReceiver;
    private final ArrayDeque<String> zebraQueue = new ArrayDeque<>();
    private boolean zebraBusy=false;
    private String zebraLastValue="";
    private long zebraLastAt=-1;
    private long zebraFirstWaitAt;
    private long zebraLastEnterAt;
    private long zebraCurrentStartedAt;
    private long zebraCurrentCommittedAt;
    private long zebraLastConfigAt;
    private int zebraGeneration;

    private SharedPreferences zebraPrefs(){
        return getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);
    }
    private boolean zebraEnabled(){
        return ZebraProfile0Mode.usesBridgeIme(
                   zebraPrefs().getBoolean(ZebraImeMode.PREF,false),
                   zebraPrefs().getBoolean(ZebraProfile0Mode.PREF,false)) &&
               ScanCoordinator.isActive(this);
    }
    /** IME so consegue digitar depois que o MLOG criou uma InputConnection.
     * Teclado selecionado nao significa campo focado. */
    static boolean hasMlogInput(){
        BridgeScanInputMethodService ime=active;
        return ime!=null && ime.zebraEditorReady();
    }
    static boolean isRunning(){return active!=null;}
    static void resetForModeChange(){
        BridgeScanInputMethodService ime=active;
        if(ime==null)return;
        ime.main.post(()->{
            ++ime.zebraGeneration;
            ime.zebraQueue.clear();
            ime.zebraBusy=false;
            ime.zebraFirstWaitAt=0;
            ime.zebraReport("MODE_CHANGE_QUEUE_CLEARED");
        });
    }
    /** Receber o primeiro bipe retido enquanto o teclado ainda iniciava.
     * So vale no MLOG com a conexao ja ativa; sem buffers persistentes. */
    private void acceptInitialFocusScan(){
        MlogFocusAccessibilityService helper=MlogFocusAccessibilityService.current();
        if(helper==null || !zebraEditorReady())return;
        String initial=helper.consumeZebraInitialScan();
        if(initial==null)return;
        if(zebraQueue.size()>=ZebraImeMode.MAX_WAITING){
            zebraReport("INITIAL_QUEUE_FULL");return;
        }
        zebraQueue.addFirst(initial);
        zebraReport("INITIAL_SCAN_RECOVERED_AFTER_FOCUS");
        main.post(this::zebraPump);
    }
    @Override public boolean onEvaluateInputViewShown(){
        // Em coletores com teclado/gatilho fisico, Android pode esconder um IME.
        // Forcar apenas a barra compacta quando o modo Zebra esta selecionado.
        return zebraEnabled() || super.onEvaluateInputViewShown();
    }
    private boolean zebraEditorReady(){
        if(!zebraEnabled() || !targetMlog)return false;
        EditorInfo info=getCurrentInputEditorInfo();
        if(info==null || !ScanCoordinator.TARGET.equals(info.packageName) ||
           isPassword(info.inputType) || isLoginHint(info.hintText))return false;
        return getCurrentInputConnection()!=null;
    }
    private void zebraReport(String stage){
        Log.i("MLOGBridge","ZEBRA_IME_"+stage); // nunca registrar o codigo
    }
    private void zebraStatus(String text){
        ScanCoordinator.status(this,text);
    }
    private void configureNativeIfNeeded(){
        if(!zebraEnabled())return;
        long now=SystemClock.elapsedRealtime();
        if(zebraLastConfigAt>0 && now-zebraLastConfigAt<30000L)return;
        zebraLastConfigAt=now;
        if(!NativeScannerController.configure(this))zebraReport("NATIVE_CONFIG_UNAVAILABLE");
    }
    private void registerZebraReceiver(){
        if(zebraReceiver!=null)return;
        zebraReceiver=new BroadcastReceiver(){
            @Override public void onReceive(Context context,Intent intent){
                if(!zebraEnabled() || intent==null ||
                    !DirectScanPolicy.ACTION.equals(intent.getAction()))return;
                String raw=ScannerProtocol.barcodeRaw(intent);
                String code=BarcodeCleaner.clean(raw);
                if(!ZebraImeMode.isValidCode(code,ScannerProtocol.type(intent))){
                    zebraReport("INVALID_SCAN_IGNORED");
                    zebraStatus("Leitura invalida; tente bipar novamente.");
                    return;
                }
                long now=SystemClock.elapsedRealtime();
                if(ZebraImeMode.isEcho(code,zebraLastValue,now-zebraLastAt)){
                    zebraReport("BROADCAST_ECHO_SUPPRESSED");return;
                }
                zebraLastValue=code;zebraLastAt=now;
                if(!zebraEditorReady()){
                    // O Zebra DataWedge tambem precisa de um editor ativo.
                    // Com acessibilidade opcional, tocar APENAS no botao de
                    // teclado do MLOG; IME continua responsavel por codigo/Enter.
                    MlogFocusAccessibilityService helper=MlogFocusAccessibilityService.current();
                    boolean opening=helper!=null &&
                        zebraPrefs().getBoolean(ZebraImeMode.AUTO_FOCUS_PREF,true) &&
                        helper.requestZebraKeyboardFocus();
                    if(!opening){
                        zebraReport("NO_MLOG_INPUT_IGNORED");
                        zebraStatus("Sem campo do MLOG. Abra o icone de teclado e selecione o teclado Bridge.");
                        return;
                    }
                    zebraReport("OPENING_MLOG_EDITOR_WITH_OPTIONAL_ACCESSIBILITY");
                }
                if(zebraQueue.size()>=ZebraImeMode.MAX_WAITING){
                    zebraReport("QUEUE_FULL_IGNORED");
                    zebraStatus("Fila do leitor cheia. Confira os produtos no MLOG.");
                    return;
                }
                zebraQueue.addLast(code);
                zebraReport("SCAN_QUEUED_DEPTH_"+zebraQueue.size());
                zebraPump();
            }
        };
        IntentFilter f=new IntentFilter(DirectScanPolicy.ACTION);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(zebraReceiver,f,Context.RECEIVER_EXPORTED);
        else registerReceiver(zebraReceiver,f);
    }
    private void zebraDropHead(String reason){
        zebraReport(reason);
        if(!zebraQueue.isEmpty())zebraQueue.removeFirst();
        zebraBusy=false;
        zebraFirstWaitAt=0;
        main.post(this::zebraPump);
    }
    private void zebraPump(){
        if(zebraBusy || zebraQueue.isEmpty() || !zebraEnabled())return;
        if(!zebraEditorReady()){
            // Nao escrever no campo errado. Nunca reencaminhar o codigo
            // apos mais de 750 ms: pode ter mudado de tela/produto.
            long now=SystemClock.elapsedRealtime();
            if(zebraFirstWaitAt==0)zebraFirstWaitAt=now;
            if(now-zebraFirstWaitAt>=ZebraImeMode.MAX_EDITOR_WAIT_MS){
                zebraStatus("Campo do MLOG indisponivel: leitura nao enviada.");
                zebraDropHead("NO_EDITOR_TIMEOUT");
            }else main.postDelayed(this::zebraPump,ZebraImeMode.EDITOR_RETRY_MS);
            return;
        }
        zebraBusy=true;
        zebraCurrentStartedAt=SystemClock.elapsedRealtime();
        String code=zebraQueue.peekFirst();
        InputConnection conn=getCurrentInputConnection();
        EditorInfo info=getCurrentInputEditorInfo();
        if(conn==null || info==null){zebraDropHead("LOST_EDITOR");return;}
        // Se o usuario digitou algo, nunca concatenar e nunca apagar silenciosamente.
        CharSequence before=null;
        try{before=conn.getTextBeforeCursor(128,0);}catch(RuntimeException ignored){}
        if(before!=null && before.length()>0){
            // Flutter pode levar alguns frames para limpar o editor depois do Enter.
            // Reagendar sem reenviar codigo evita perder bipes legitimamente rapidos.
            zebraBusy=false;
            long now=SystemClock.elapsedRealtime();
            if(zebraFirstWaitAt==0)zebraFirstWaitAt=now;
            if(now-zebraFirstWaitAt>=ZebraImeMode.MAX_EDITOR_WAIT_MS){
                zebraStatus("Campo permaneceu ocupado; leitura nao enviada.");
                zebraDropHead("FIELD_BUSY_TIMEOUT");
            }else main.postDelayed(this::zebraPump,ZebraImeMode.EDITOR_RETRY_MS);
            return;
        }
        boolean written=false;
        try{
            conn.beginBatchEdit();
            if(zebraPrefs().getBoolean(ZebraImeMode.CHAR_EVENTS_PREF,false)){
                // Opcao semelhante ao DataWedge "Send Characters as Events".
                // OFF (padrao): commitText em bloco, mais rapido.
                // ON: eventos fisicos emulados pelo IME, mais custosos.
                KeyEvent[] sequence=KeyCharacterMap.load(
                    KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(code.toCharArray());
                written=sequence!=null && sequence.length>0;
                if(written){
                    for(KeyEvent event:sequence){
                        if(!conn.sendKeyEvent(event)){written=false;break;}
                    }
                }
            }else written=conn.commitText(code,1);
        }catch(RuntimeException ex){zebraReport("COMMIT_EXCEPTION");}
        finally{try{conn.endBatchEdit();}catch(RuntimeException ignored){}}
        if(!written){
            zebraStatus("MLOG nao aceitou o codigo. Bipe novamente.");
            zebraDropHead("COMMIT_REJECTED");return;
        }
        zebraFirstWaitAt=0;
        zebraCurrentCommittedAt=SystemClock.elapsedRealtime();
        zebraReport("CODE_COMMITTED_MS_"+(zebraCurrentCommittedAt-zebraCurrentStartedAt));
        long ms=ZebraImeMode.interval(zebraPrefs().getInt(
                ScanAssistState.CLICK_COOLDOWN_MS,100));
        // O valor escolhido controla o tempo entre a insercao e o Enter.
        // Piso de 20 ms para o Flutter absorver commitText assíncrono.
        long delay=Math.max(20L,ms);
        final int token=++zebraGeneration;
        main.postDelayed(()->zebraEnter(token,code,conn),delay);
    }
    private void zebraEnter(int token,String expected,InputConnection original){
        if(token!=zebraGeneration || !zebraEnabled() || zebraQueue.isEmpty())return;
        if(!zebraEditorReady()){
            zebraStatus("MLOG perdeu o foco antes do Enter. Confira a leitura.");
            zebraDropHead("FOCUS_LOST_BEFORE_ENTER");return;
        }
        InputConnection current=getCurrentInputConnection();
        // A troca do InputConnection pode ser normal no Flutter, mas so dar Enter
        // quando conseguimos confirmar que se trata do codigo desta leitura.
        CharSequence before=null;
        try{before=current.getTextBeforeCursor(expected.length()+4,0);}
        catch(RuntimeException ignored){}
        if(before!=null && before.length()>0 &&
           !before.toString().endsWith(expected)){
            zebraStatus("Codigo do campo diferente do bipado; Enter bloqueado.");
            zebraDropHead("TEXT_MISMATCH_NO_ENTER");return;
        }
        if(current!=original && (before==null || before.length()==0)){
            zebraStatus("Campo mudou antes do Enter. Confira a leitura.");
            zebraDropHead("EDITOR_CHANGED_NO_ENTER");return;
        }
        if(!zebraPrefs().getBoolean(ZebraImeMode.ENTER_PREF,true)){
            zebraReport("ENTER_DISABLED_BY_OPERATOR");
            zebraDropHead("CYCLE_WITHOUT_ENTER");
            return;
        }
        long now=SystemClock.uptimeMillis();
        int dev=KeyCharacterMap.VIRTUAL_KEYBOARD;
        int flags=KeyEvent.FLAG_SOFT_KEYBOARD;
        boolean down=false,up=false;
        try{
            down=current.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER,0,0,dev,0,flags));
            up=current.sendKeyEvent(new KeyEvent(now,now+7,KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ENTER,0,0,dev,0,flags));
        }catch(RuntimeException ex){zebraReport("ENTER_EXCEPTION");}
        boolean sent=down||up;
        if(!sent && zebraPrefs().getBoolean(ZebraImeMode.ACTION_FALLBACK_PREF,false)){
            EditorInfo info=getCurrentInputEditorInfo();
            int action=info==null?EditorInfo.IME_ACTION_NONE:
                    info.imeOptions&EditorInfo.IME_MASK_ACTION;
            if(action!=EditorInfo.IME_ACTION_NONE &&
               action!=EditorInfo.IME_ACTION_UNSPECIFIED){
                try{sent=current.performEditorAction(action);}
                catch(RuntimeException ignored){}
            }
        }
        if(!sent)zebraStatus("Enter nao aceito pelo MLOG. Confira o produto.");
        zebraReport(sent?"ENTER_SENT_UNVERIFIED":"ENTER_UNAVAILABLE");
        zebraLastEnterAt=SystemClock.elapsedRealtime();
        zebraReport("ENTER_FROM_COMMIT_MS_"+(zebraLastEnterAt-zebraCurrentCommittedAt));
        zebraDropHead(sent?"CYCLE_FINISHED":"CYCLE_FAILED");
    }

    // Apagar contínuo: 1 caractere imediatamente; repetição enquanto o dedo
    // permanece pressionado. Ao soltar, não há exclusões pendentes.
    private static final long ERASE_HOLD_DELAY_MS=350;
    private static final long ERASE_REPEAT_MS=70;
    private boolean eraseHeld=false;
    private final Runnable repeatErase=new Runnable(){
        @Override public void run(){
            if(!eraseHeld)return;
            type("⌫");
            main.postDelayed(this,ERASE_REPEAT_MS);
        }
    };
    private void stopErase(){
        eraseHeld=false;
        main.removeCallbacks(repeatErase);
    }
    private TextView eraseKey(String label){
        TextView t=key(label,()->type("⌫"),1);
        t.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    stopErase();
                    eraseHeld=true;
                    type("⌫");
                    main.postDelayed(repeatErase,ERASE_HOLD_DELAY_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopErase();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if(event.getX()<0 || event.getY()<0 ||
                       event.getX()>v.getWidth() || event.getY()>v.getHeight())stopErase();
                    return true;
                default:return true;
            }
        });
        return t;
    }

    // O Enter automatico existe exclusivamente no modo Zebra opt-in.
    // Os metodos legados abaixo continuam disponiveis para o modo anterior.
    /** Diagnostica IME ativo E InputConnection da tela MLOG atual.
     * isInputViewShown e especialmente importante: a conexao antiga pode
     * continuar existindo depois do Enter, quando o Flutter escondeu teclado.
     */
    static boolean hasLiveInputForMlog(){
        BridgeScanInputMethodService ime=active;
        if(ime==null || !ime.targetMlog || !ime.isInputViewShown())return false;
        EditorInfo info=ime.getCurrentInputEditorInfo();
        return info!=null && ScanCoordinator.TARGET.equals(info.packageName) &&
            !isPassword(info.inputType) && ime.getCurrentInputConnection()!=null;
    }
    /** Usar SOMENTE apos o Bridge verificar tela, editor focado e texto exato.
     * Flutter pode esconder a interface visual do IME enquanto mantem uma
     * InputConnection valida. Nao depender de isInputViewShown neste fluxo.
     */
    static boolean sendEnterForVerifiedScan(){
        BridgeScanInputMethodService ime=active;
        if(ime==null || !ime.targetMlog)return false;
        EditorInfo info=ime.getCurrentInputEditorInfo();
        InputConnection connection=ime.getCurrentInputConnection();
        if(info==null || connection==null ||
           !ScanCoordinator.TARGET.equals(info.packageName) ||
           isPassword(info.inputType))return false;
        long now=SystemClock.uptimeMillis();
        int flags=KeyEvent.FLAG_SOFT_KEYBOARD;
        int device=KeyCharacterMap.VIRTUAL_KEYBOARD;
        boolean down=connection.sendKeyEvent(new KeyEvent(now,now,
            KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0,0,device,0,flags));
        boolean up=connection.sendKeyEvent(new KeyEvent(now,now+7,
            KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER,0,0,device,0,flags));
        if(down || up){
            android.util.Log.i("MLOGBridge",down&&up?
                "ENTER_VERIFIED_IME_KEYPAIR":"ENTER_VERIFIED_IME_PARTIAL");
            // Se somente um evento foi aceito, nao tentar outro Enter:
            // seu resultado e ambiguo e requer verificacao visual.
            return true;
        }
        android.util.Log.i("MLOGBridge","ENTER_VERIFIED_IME_REJECTED");
        return false;
    }
    static boolean sendEnterForAutoCount(){
        BridgeScanInputMethodService ime=active;
        if(!hasLiveInputForMlog()){
            android.util.Log.i("MLOGBridge","AUTO_ENTER_IME_VIEW_NOT_ACTIVE");
            return false;
        }
        InputConnection connection=ime.getCurrentInputConnection();
        EditorInfo info=ime.getCurrentInputEditorInfo();
        if(connection==null || info==null ||
           !ScanCoordinator.TARGET.equals(info.packageName) ||
           isPassword(info.inputType))return false;
        // Compatibilidade com o fluxo lento que funcionava na v1.9:
        // SEMPRE preferir um par físico DOWN/UP de Enter, nunca declarar
        // sucesso apenas porque performEditorAction() retornou true.
        long now=SystemClock.uptimeMillis();
        int flags=KeyEvent.FLAG_SOFT_KEYBOARD;
        int device=KeyCharacterMap.VIRTUAL_KEYBOARD;
        boolean down=connection.sendKeyEvent(new KeyEvent(now,now,
                KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0,0,device,0,flags));
        boolean up=connection.sendKeyEvent(new KeyEvent(now,now+7,
                KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER,0,0,device,0,flags));
        if(down && up){
            android.util.Log.i("MLOGBridge","AUTO_ENTER_METHOD_KEYPAIR_SENT");
            return true; // NUNCA enviar um segundo Enter para esta repeticao.
        }
        if(down || up){
            // Parte do par foi aceita. Nao executar editorAction depois,
            // pois pode confirmar o mesmo produto duas vezes.
            android.util.Log.i("MLOGBridge","AUTO_ENTER_METHOD_PARTIAL_KEYPAIR");
            return true; // Tentativa ambigua, NAO uma gravacao confirmada.
        }
        android.util.Log.i("MLOGBridge","AUTO_ENTER_METHOD_KEYPAIR_UNAVAILABLE");
        // Apenas se a conexao recusou ambas as teclas, tentar acao IME.
        int action=info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if(action!=EditorInfo.IME_ACTION_UNSPECIFIED &&
           action!=EditorInfo.IME_ACTION_NONE){
            boolean handled=connection.performEditorAction(action);
            android.util.Log.i("MLOGBridge","AUTO_ENTER_METHOD_FALLBACK_EDITOR_ACTION_"+action+"_"+handled);
            return handled;
        }
        return false;
    }

    private static boolean isLoginHint(CharSequence hint){
        if(hint==null)return false;
        String s=hint.toString().toLowerCase(java.util.Locale.ROOT);
        return s.contains("login") || s.contains("senha") || s.contains("password") ||
               s.contains("usuario") || s.contains("usuário") || s.contains("e-mail");
    }

    private static boolean isPassword(int type){
        int c=type&InputType.TYPE_MASK_CLASS;
        int v=type&InputType.TYPE_MASK_VARIATION;
        return c==InputType.TYPE_CLASS_TEXT &&
             (v==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
              v==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
              v==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
             c==InputType.TYPE_CLASS_NUMBER &&
              v==InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    @Override public void onCreate(){
        super.onCreate();active=this;
        registerZebraReceiver();
        configureNativeIfNeeded();
    }
    @Override public void onDestroy(){
        stopErase();zebraGeneration++;zebraQueue.clear();
        main.removeCallbacksAndMessages(null);
        if(zebraReceiver!=null){
            try{unregisterReceiver(zebraReceiver);}catch(RuntimeException ignored){}
            zebraReceiver=null;
        }
        if(active==this)active=null;
        super.onDestroy();
    }

    @Override public View onCreateInputView(){
        panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        render();
        return panel;
    }

    @Override public void onStartInput(EditorInfo info,boolean restarting){
        super.onStartInput(info,restarting);
        boolean wasTargetMlog=targetMlog;
        targetMlog=info!=null && ScanCoordinator.TARGET.equals(info.packageName);
        // Campo novo / nova app => compacto no MLOG. Se o Flutter reiniciar
        // o input do MESMO campo durante digitacao, manter QWERTY aberto.
        if(!targetMlog){
            manual=true;
            if(!zebraQueue.isEmpty()){
                zebraReport("LEFT_MLOG_PENDING_CLEARED");
                ++zebraGeneration;zebraQueue.clear();zebraBusy=false;
                zebraFirstWaitAt=0;
            }
        }else if(!restarting || !wasTargetMlog)manual=false;
        numeric=info!=null && ((info.inputType&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_NUMBER);
        upper=false;
        if(panel!=null)render();
        if(zebraEnabled()){
            configureNativeIfNeeded();
            if(targetMlog)acceptInitialFocusScan();
            if(zebraEditorReady() && !isInputViewShown() &&
               zebraPrefs().getBoolean(ZebraImeMode.SHOW_PREF,true)){
                // Com campo ativo, pede a barra pequena do Bridge mesmo em
                // equipamentos que reportam teclado fisico.
                main.post(()->{try{requestShowSelf(0);}catch(RuntimeException ignored){}});
            }
            main.post(this::zebraPump);
        }
    }

    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        targetMlog=info!=null && ScanCoordinator.TARGET.equals(info.packageName);
        // onFinishInputView ja recolheu quando a janela foi escondida.
        // Nao interromper digitacao por um restartInput do mesmo campo.
        if(!targetMlog)manual=true;
        else if(!restarting)manual=false;
        if(panel!=null)render();
        if(zebraEnabled())main.post(this::zebraPump);
    }

    @Override public void onFinishInputView(boolean finishingInput){
        stopErase();
        if(targetMlog){manual=false;if(panel!=null)render();}
        super.onFinishInputView(finishingInput);
    }

    @Override public void onFinishInput(){
        stopErase();
        if(targetMlog){manual=false;if(panel!=null)render();}
        targetMlog=false;
        super.onFinishInput();
    }

    @Override public boolean onKeyUp(int keyCode,KeyEvent event){
        boolean handled=super.onKeyUp(keyCode,event);
        // Nao interceptar nem emitir Enter: o CMX continua responsavel.
        // Se o hardware entregar a tecla a esta IME, apenas registrar que
        // a tecla passou e recolher o QWERTY quando aberto.
        if(targetMlog &&
            (keyCode==KeyEvent.KEYCODE_ENTER || keyCode==KeyEvent.KEYCODE_NUMPAD_ENTER)){
            MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
            if(svc!=null)svc.onCmxEnterObservedByKeyboard();
        }
        if(targetMlog && manual &&
            (keyCode==KeyEvent.KEYCODE_ENTER || keyCode==KeyEvent.KEYCODE_NUMPAD_ENTER)){
            main.post(()->collapseInMlog());
        }
        return handled;
    }

    private void collapseInMlog(){
        if(targetMlog && manual){manual=false;if(panel!=null)render();}
    }

    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private boolean dark(){return (getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    private GradientDrawable background(int c,int stroke,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(radius));
        if(stroke!=0)g.setStroke(dp(1),stroke);return g;
    }
    private TextView key(String caption,Runnable onClick,int weight){
        TextView t=new TextView(this);
        t.setText(caption);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT,Typeface.NORMAL);
        t.setTextColor(dark()?0xffeeeeee:0xff1a1a1a);
        t.setBackground(background(dark()?0xff292929:0xfff0f0f0,dark()?0xff3b3b3b:0xffdddddd,8));
        t.setOnClickListener(v->onClick.run());
        t.setPadding(dp(2),dp(3),dp(2),dp(3));
        return t;
    }
    private void render(){
        if(panel==null)return;
        stopErase();
        panel.removeAllViews();
        int surface=dark()?0xff111111:0xfff9f9f9;
        panel.setBackgroundColor(surface);
        if(targetMlog && zebraEnabled() &&
           !zebraPrefs().getBoolean(ZebraImeMode.SHOW_PREF,true)){
            // A conexao de entrada continua ativa, somente a barra some.
            return;
        }
        // Tela MLOG: uma barra pequena em vez do Gboard de tela cheia.
        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(5),dp(3),dp(5),dp(3));
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=new TextView(this);
        label.setText(targetMlog?"Bridge · Leitura":"Bridge · Teclado");
        label.setTextSize(12);
        label.setTextColor(dark()?0xffeeeeee:0xff111111);
        label.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(label,new LinearLayout.LayoutParams(0,dp(31),1));
        TextView toggle=key(manual?"Ocultar teclas":"Digitar",()->{manual=!manual;render();},1);
        top.addView(toggle,new LinearLayout.LayoutParams(dp(100),dp(32)));
        if(targetMlog && !manual){
            TextView erase=eraseKey("⌫ Apagar");
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(86),dp(32));
            ep.leftMargin=dp(5);
            top.addView(erase,ep);
        }
        TextView switcher=key("⇄",()->{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showInputMethodPicker();
        },1);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(38),dp(32));sp.leftMargin=dp(5);
        top.addView(switcher,sp);
        panel.addView(top);
        if(manual){
            // Mesma disposicao QWERTY do Gboard, sem fileira numerica em cima.
            // Botao manual Enter; a confirmacao automatica das bipagens
            // continua independente, via KEYCODE_ENTER apos o campo pronto.
            if(numeric){
                row(new String[]{"1","2","3"});
                row(new String[]{"4","5","6"});
                row(new String[]{"7","8","9"});
                row(new String[]{".","0","⌫"});
                row(new String[]{"ABC","Espaço","Enter ↵"});
            }else{
                rowChars("qwertyuiop",0);
                rowChars("asdfghjkl",dp(18));
                row(new String[]{"⇧",upper?"Z":"z",upper?"X":"x",upper?"C":"c",
                        upper?"V":"v",upper?"B":"b",upper?"N":"n",upper?"M":"m","⌫"});
                row(new String[]{"?123",",","Espaço",".","Enter ↵"});
            }
        }
    }

    private void rowChars(String chars,int inset){
        String[] names=new String[chars.length()];
        for(int i=0;i<chars.length();i++){
            String c=chars.substring(i,i+1);
            names[i]=upper?c.toUpperCase():c;
        }
        row(names,inset);
    }
    private void row(String[] names){row(names,0);}
    private void row(String[] names,int inset){
        LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(dp(3)+inset,dp(3),dp(3)+inset,dp(3));
        for(String n:names){
            TextView t="⌫".equals(n)?eraseKey(n):key(n,()->type(n),1);
            float weight="Espaço".equals(n)?3.8f:("Enter ↵".equals(n)?1.5f:1f);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),weight);
            lp.leftMargin=dp(2);lp.rightMargin=dp(2);
            line.addView(t,lp);
        }
        panel.addView(line);
    }

    private void type(String value){
        InputConnection ic=getCurrentInputConnection();
        if(ic==null)return;
        if("⌫".equals(value)){
            // Primeiro apagar selecao; se nao houver, apagar o caractere
            // anterior ao cursor (sem precisar expandir QWERTY).
            CharSequence selected=ic.getSelectedText(0);
            if(selected!=null && selected.length()>0){
                ic.commitText("",1);
            }else if(!ic.deleteSurroundingText(1,0)){
                long when=SystemClock.uptimeMillis();
                ic.sendKeyEvent(new KeyEvent(when,when,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL,0));
                ic.sendKeyEvent(new KeyEvent(when,when+1,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL,0));
            }
            return;
        }
        if("⇧".equals(value)){upper=!upper;render();return;}
        if("?123".equals(value) || "ABC".equals(value)){numeric=!numeric;render();return;}
        if("Enter ↵".equals(value)){
            // Tecla Enter manual no lugar de Tab, como solicitada.
            // Um unico par DOWN/UP; se a conexao recusar, tentar a acao
            // IME anunciada pelo editor (Search/Done/Go/Next).
            long now=SystemClock.uptimeMillis();
            KeyEvent down=new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0);
            KeyEvent up=new KeyEvent(now,now+7,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER,0);
            boolean sent=ic.sendKeyEvent(down);
            sent=ic.sendKeyEvent(up)||sent;
            if(!sent){
                EditorInfo info=getCurrentInputEditorInfo();
                int action=info==null?EditorInfo.IME_ACTION_UNSPECIFIED:
                    (info.imeOptions&EditorInfo.IME_MASK_ACTION);
                if(action!=EditorInfo.IME_ACTION_UNSPECIFIED &&
                   action!=EditorInfo.IME_ACTION_NONE)
                    ic.performEditorAction(action);
            }
            // Recolher apenas o QWERTY do Bridge; nao esconder a barra de leitura.
            collapseInMlog();
            return;
        }
        if("Espaço".equals(value))value=" ";
        ic.commitText(value,1);
        if(upper && value.length()==1 && Character.isLetter(value.charAt(0))){
            upper=false;
            if(manual && !numeric)render();
        }
    }
}
