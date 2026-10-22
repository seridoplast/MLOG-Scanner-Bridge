package br.com.seridoplast.mlogbridge;

import android.inputmethodservice.InputMethodService;
import android.content.Context;
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

    // O teclado Bridge continua com Enter MANUAL. Nenhum metodo externo
    // consegue acionar Enter automaticamente nesta versao.

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

    @Override public void onCreate(){super.onCreate();active=this;}
    @Override public void onDestroy(){if(active==this)active=null;super.onDestroy();}

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
        if(!targetMlog)manual=true;
        else if(!restarting || !wasTargetMlog)manual=false;
        numeric=info!=null && ((info.inputType&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_NUMBER);
        upper=false;
        if(panel!=null)render();
    }

    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        targetMlog=info!=null && ScanCoordinator.TARGET.equals(info.packageName);
        // onFinishInputView ja recolheu quando a janela foi escondida.
        // Nao interromper digitacao por um restartInput do mesmo campo.
        if(!targetMlog)manual=true;
        else if(!restarting)manual=false;
        if(panel!=null)render();
    }

    @Override public void onFinishInputView(boolean finishingInput){
        if(targetMlog){manual=false;if(panel!=null)render();}
        super.onFinishInputView(finishingInput);
    }

    @Override public void onFinishInput(){
        if(targetMlog){manual=false;if(panel!=null)render();}
        super.onFinishInput();
    }

    @Override public boolean onKeyUp(int keyCode,KeyEvent event){
        boolean handled=super.onKeyUp(keyCode,event);
        // Nao interceptar nem emitir Enter: o CMX continua responsavel.
        // Se o hardware entregar a tecla a esta IME, so recolher as teclas.
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
        panel.removeAllViews();
        int surface=dark()?0xff111111:0xfff9f9f9;
        panel.setBackgroundColor(surface);
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
            TextView erase=key("⌫ Apagar",()->type("⌫"),1);
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
            TextView t=key(n,()->type(n),1);
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
