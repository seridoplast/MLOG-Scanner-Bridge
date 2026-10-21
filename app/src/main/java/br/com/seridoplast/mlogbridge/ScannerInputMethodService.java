package br.com.seridoplast.mlogbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.Locale;

/** Teclado de leitura sem teclas. Jamais registra texto digitado, codigo ou senha. */
public class ScannerInputMethodService extends InputMethodService {
    private static volatile ScannerInputMethodService instance;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BroadcastReceiver receiver;
    private boolean registered,editorStarted;
    private int editorSerial;

    static ScannerInputMethodService current(){return instance;}

    @Override public void onCreate() {
        super.onCreate();
        instance=this;
        receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent intent){
                // ScanCoordinator evita duplicar quando acessibilidade tambem recebe.
                ScanCoordinator.onScan(ScannerInputMethodService.this,ScannerProtocol.barcode(intent));
            }
        };
        IntentFilter f=new IntentFilter();
        for(String action:ScannerProtocol.ACTIONS)f.addAction(action);
        try{
            if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_EXPORTED);
            else registerReceiver(receiver,f);
            registered=true;
        }catch(RuntimeException e){ ScanCoordinator.status(this,"Falha ao conectar transmissao do scanner."); }
    }

    @Override public View onCreateInputView() {
        // Area minima de 1px: nao esconde a tela de separacao.
        View v=new View(this);
        v.setMinimumHeight(0);
        v.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1,1));
        return v;
    }

    @Override public boolean onEvaluateFullscreenMode(){return false;}

    @Override public void onStartInput(EditorInfo a,boolean restarting){
        super.onStartInput(a,restarting);
        editorStarted=true;
        editorSerial++;
        ScanCoordinator.onInputStarted(this);
        if(hasSafeMlogEditor(ScanCoordinator.isArmed()))
            ScanCoordinator.status(this,"Campo MLOG disponivel para leitura.");
    }
    @Override public void onFinishInput(){
        editorStarted=false;editorSerial++;
        ScanCoordinator.onInputFinished();
        super.onFinishInput();
    }

    private static boolean isPassword(EditorInfo i){
        if(i==null)return true;
        int cls=i.inputType & InputType.TYPE_MASK_CLASS;
        int vari=i.inputType & InputType.TYPE_MASK_VARIATION;
        return cls==InputType.TYPE_CLASS_TEXT &&
            (vari==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
             vari==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
             vari==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
            cls==InputType.TYPE_CLASS_NUMBER && vari==InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }
    private static boolean disallowedEditor(EditorInfo info,boolean verifiedButtonClick){
        if(info==null)return true;
        String hint=(info.hintText==null?"":info.hintText.toString()).toLowerCase(Locale.ROOT);
        String field=(info.fieldName==null?"":info.fieldName).toLowerCase(Locale.ROOT);
        String names=hint+" "+field;
        if(names.contains("quantidade") || names.contains("senha") || names.contains("password") ||
                names.contains("peso") || names.contains("volume") || names.contains("usuário") ||
                names.contains("usuario"))return true;
        // Quantidade costuma ser um campo numerico. So liberar a leitura em
        // campo numerico se seu rotulo confirmar que se trata de codigo.
        int kind=info.inputType & InputType.TYPE_MASK_CLASS;
        if(kind==InputType.TYPE_CLASS_NUMBER && !verifiedButtonClick &&
                !(names.contains("codigo") || names.contains("código") ||
                  names.contains("barcode") || names.contains("barras")))return true;
        return false;
    }
    boolean hasAnyMlogEditor(){
        EditorInfo i=getCurrentInputEditorInfo();
        return editorStarted && i!=null && ScanCoordinator.TARGET.equals(i.packageName) &&
                getCurrentInputConnection()!=null;
    }
    boolean hasSafeMlogEditor(boolean verifiedButtonClick){
        EditorInfo i=getCurrentInputEditorInfo();
        return hasAnyMlogEditor() && !isPassword(i) &&
                !disallowedEditor(i,verifiedButtonClick);
    }

    boolean tryInsert(String code,boolean requireArmed){
        if(!ScanCoordinator.isActive(this) || !hasSafeMlogEditor(requireArmed))return false;
        if(requireArmed && !ScanCoordinator.isArmed())return false;
        InputConnection ic=getCurrentInputConnection();
        if(ic==null)return false;
        final int serial=editorSerial;
        final boolean ok;
        try{ok=ic.commitText(code,1);}catch(RuntimeException e){return false;}
        if(!ok)return false;
        ScanCoordinator.status(this,"Codigo entregue ao campo do MLOG (sem salvar conteudo).");
        SharedPreferences p=getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);
        if(p.getBoolean("send_enter",false)){
            main.postDelayed(()->{
                if(editorSerial!=serial || !hasSafeMlogEditor(requireArmed) ||
                        (requireArmed && !ScanCoordinator.isArmed()))return;
                InputConnection con=getCurrentInputConnection();
                if(con==null)return;
                con.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));
                con.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));
                ScanCoordinator.status(this,"Leitura entregue e Enter enviado.");
            },110);
        }
        return true;
    }

    @Override public void onDestroy(){
        ScanCoordinator.onInputFinished();
        if(instance==this)instance=null;
        if(registered)try{unregisterReceiver(receiver);}catch(RuntimeException ignored){}
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
