package br.com.seridoplast.mlogbridge;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Switch;
import android.content.res.ColorStateList;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Optional user-controlled overlay. Never captures screen contents. */
public final class FloatingToolsService extends Service {
    private static FloatingToolsService visible;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private View view;
    private TextView status;
    private boolean expanded;
    private boolean batchDrawerOpen;
    private String statusText="Pronto. Ative a função desejada.";
    private final int background=0xff161616;
    private final int text=0xffffffff;
    private final int green=0xff20c977;
    private final int red=0xffef5350;

    static void report(String message){
        FloatingToolsService instance=visible;
        if(instance!=null) instance.main.post(()->{
            instance.statusText=message;
            if(instance.status!=null)instance.status.setText(message);
        });
    }

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
            stopSelf();return START_NOT_STICKY;
        }
        visible=this;
        if(wm==null)wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
        // Never preserve an armed batch across process/service restarts.
        if(view==null){
            ScanAssistState.prefs(this).edit().putBoolean(ScanAssistState.ARMED,false).apply();
            draw(false);
        }
        return START_NOT_STICKY;
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int corner){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(corner));
        d.setStroke(dp(1),0xff505050);return d;
    }
    private TextView label(String value,int size,boolean bold){
        TextView t=new TextView(this); t.setText(value);t.setTextColor(text);
        t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView button(String value,Runnable cb){
        TextView t=label(value,14,true);t.setPadding(dp(12),dp(11),dp(12),dp(11));
        t.setGravity(Gravity.CENTER);t.setBackground(bg(0xff353535,12));
        t.setOnClickListener(v->cb.run()); return t;
    }
    private void collapse(){
        try{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null && view!=null)imm.hideSoftInputFromWindow(view.getWindowToken(),0);
        }catch(RuntimeException ignored){}
        draw(false);
    }
    private TextView muted(String value,int sp){
        TextView t=label(value,sp,false);
        t.setTextColor(0xffb5b8bf);
        return t;
    }
    private Switch toggle(boolean active){
        Switch sw=new Switch(this);
        sw.setChecked(active);
        sw.setShowText(false);
        int[][] states=new int[][]{new int[]{android.R.attr.state_checked},new int[]{}};
        sw.setThumbTintList(new ColorStateList(states,new int[]{green,red}));
        sw.setTrackTintList(new ColorStateList(states,new int[]{0xff18754c,0xff7d2d32}));
        sw.setContentDescription(active?"Ativado":"Desativado");
        sw.setOnCheckedChangeListener((v,on)->sw.setContentDescription(on?"Ativado":"Desativado"));
        return sw;
    }
    private View separator(){
        View line=new View(this);line.setBackgroundColor(0xff34373b);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));
        lp.topMargin=dp(14);lp.bottomMargin=dp(14);line.setLayoutParams(lp);
        return line;
    }
    private LinearLayout featureRow(String name,String sub,Switch sw,Runnable expand){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);
        TextView title=label(name,15,true);words.addView(title);
        if(sub!=null && !sub.isEmpty()){
            TextView description=muted(sub,11);
            LinearLayout.LayoutParams detail=new LinearLayout.LayoutParams(-1,-2);
            detail.topMargin=dp(3);words.addView(description,detail);
        }
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams switchLp=new LinearLayout.LayoutParams(dp(56),dp(50));
        switchLp.leftMargin=dp(9);row.addView(sw,switchLp);
        words.setPadding(0,dp(7),0,dp(7));
        if(expand!=null){
            words.setOnClickListener(v->expand.run());
            words.setContentDescription("Abrir ou recolher ajustes de "+name);
        }else{
            words.setOnClickListener(v->sw.setChecked(!sw.isChecked()));
        }
        return row;
    }
    private void draw(boolean open){
        if(wm==null)return;
        int oldX=params==null?dp(12):params.x;
        int oldY=params==null?dp(160):params.y;
        if(view!=null){try{wm.removeView(view);}catch(RuntimeException ignored){} view=null;}
        expanded=open;
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        if(open){
            layout.setBackground(bg(background,18));
            layout.setPadding(dp(20),dp(19),dp(20),dp(19));
        }
        view=layout;
        params=new WindowManager.LayoutParams(
            open?dp(310):dp(40),WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:
                WindowManager.LayoutParams.TYPE_PHONE,
            open?WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL:
                  WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;
        int width=getResources().getDisplayMetrics().widthPixels;
        params.x=Math.max(0,Math.min(oldX,Math.max(0,width-(open?dp(310):dp(40)))));
        params.y=Math.max(0,oldY);
        params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        if(!open){
            FrameLayout bubble=new FrameLayout(this);
            bubble.setBackground(bg(0xff252525,40));bubble.setAlpha(.50f);
            bubble.setContentDescription("Abrir ferramentas do MLOG Bridge");
            ImageView gear=new ImageView(this);
            gear.setImageResource(R.drawable.ic_floating_gear);
            gear.setColorFilter(0xffffffff);gear.setPadding(dp(9),dp(9),dp(9),dp(9));
            bubble.addView(gear,new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));
            layout.addView(bubble,new LinearLayout.LayoutParams(dp(40),dp(40)));
            bubble.setOnTouchListener(new View.OnTouchListener(){
                private float downX,downY;private int startX,startY;private boolean moved;
                @Override public boolean onTouch(View v,MotionEvent e){
                    if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                        downX=e.getRawX();downY=e.getRawY();startX=params.x;startY=params.y;
                        moved=false;return true;
                    }
                    if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                        int dx=(int)(e.getRawX()-downX),dy=(int)(e.getRawY()-downY);
                        if(Math.abs(dx)>dp(5)||Math.abs(dy)>dp(5))moved=true;
                        if(moved){params.x=Math.max(0,startX+dx);params.y=Math.max(0,startY+dy);
                            try{wm.updateViewLayout(view,params);}catch(RuntimeException ignored){}}
                        return true;
                    }
                    if(e.getActionMasked()==MotionEvent.ACTION_UP){if(!moved)draw(true);return true;}
                    return true;
                }
            });
        }else{
            LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=label("MLOG • Ferramentas",16,true);
            header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            header.addView(button("−",this::collapse));layout.addView(header);
            layout.addView(separator());
            SharedPreferences pref=ScanAssistState.prefs(this);
            Switch cleaner=toggle(pref.getBoolean(ScanAssistState.CLEAN,false));
            cleaner.setOnCheckedChangeListener((v,on)->{
                pref.edit().putBoolean(ScanAssistState.CLEAN,on).apply();
                statusText=on?"Tratador ativo":"Tratador desativado";
                if(status!=null)status.setText(statusText);
            });
            layout.addView(featureRow("Tratador de códigos","Remove caracteres invisíveis",cleaner,null));
            layout.addView(separator());
            Switch batch=toggle(pref.getBoolean(ScanAssistState.BATCH,false));
            batch.setOnCheckedChangeListener((v,on)->{
                pref.edit().putBoolean(ScanAssistState.BATCH,on)
                    .putBoolean(ScanAssistState.ARMED,false).apply();
                MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
                if(svc!=null)svc.cancelAutoCount(on?null:"Bipes automáticos desligados.");
                statusText=on?"Escolha o total e arme o próximo bipe.":"Bipes automáticos desligados.";
                if(status!=null)status.setText(statusText);
            });
            layout.addView(featureRow("Bipes automáticos  ▾",
                "Toque no título para abrir",batch,()->{
                    batchDrawerOpen=!batchDrawerOpen;
                    draw(true);
                }));
            if(batchDrawerOpen){
                LinearLayout drawer=new LinearLayout(this);drawer.setOrientation(LinearLayout.VERTICAL);
                drawer.setPadding(dp(0),dp(14),dp(0),dp(8));
                LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,-2);
                layout.addView(drawer,dlp);
                drawer.setAlpha(0f);
                drawer.setTranslationY(-dp(9));
                drawer.animate().alpha(1f).translationY(0f).setDuration(180).start();
                drawer.addView(muted("1 bipe físico + os demais automáticos",12));
                LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);rlp.topMargin=dp(10);
                row.addView(label("Total de bipes",14,true),new LinearLayout.LayoutParams(0,-2,1));
                EditText amount=new EditText(this);amount.setSingleLine(true);
                amount.setTextColor(text);amount.setHintTextColor(0xffbbbbbb);
                amount.setInputType(InputType.TYPE_CLASS_NUMBER);
                amount.setSelectAllOnFocus(true);
                amount.setText(String.valueOf(ScanAssistState.clamp(pref.getInt(ScanAssistState.COUNT,1))));
                row.addView(amount,new LinearLayout.LayoutParams(dp(65),dp(48)));
                drawer.addView(row,rlp);
                TextView arm=button("▶ Armar próximo bipe",()->{
                    int count;
                    try{count=Integer.parseInt(amount.getText().toString());}
                    catch(NumberFormatException ex){count=0;}
                    if(count<1 || count>ScanAssistState.MAX_COUNT){
                        statusText="Informe de 1 a "+ScanAssistState.MAX_COUNT+" bipes.";
                        if(status!=null)status.setText(statusText);return;
                    }
                    if(!batch.isChecked()){
                        statusText="Ative Bipes automáticos no interruptor.";
                        if(status!=null)status.setText(statusText);return;
                    }
                    MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
                    if(svc!=null)svc.cancelAutoCount(null);
                    pref.edit().putInt(ScanAssistState.COUNT,count)
                        .putBoolean(ScanAssistState.ARMED,true).apply();
                    statusText="Armado: 1 físico + "+AutoRepeatPolicy.virtualRepeats(count)+" virtuais.";
                    ScanAssistState.report(statusText);
                    collapse();
                });
                LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,-2);
                alp.topMargin=dp(12);drawer.addView(arm,alp);
                TextView stop=button("■ Cancelar bipes automáticos",()->{
                    pref.edit().putBoolean(ScanAssistState.ARMED,false).apply();
                    MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
                    if(svc!=null)svc.cancelAutoCount("Bipes automáticos cancelados.");
                });
                LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);
                slp.topMargin=dp(8);drawer.addView(stop,slp);
                TextView foot=muted("A partir de 155 ms entre envios. Modo contínuo sem confirmação visual: confira a quantidade no MLOG.",11);
                LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,-2);
                fl.topMargin=dp(10);drawer.addView(foot,fl);
            }
            status=muted(statusText,12);
            LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);
            sl.topMargin=dp(16);layout.addView(status,sl);
        }
        try{wm.addView(view,params);}catch(RuntimeException ex){view=null;stopSelf();}
    }
    @Override public void onDestroy(){
        if(visible==this)visible=null;
        if(wm!=null && view!=null)try{wm.removeView(view);}catch(RuntimeException ignored){}
        view=null;
        ScanAssistState.prefs(this).edit().putBoolean(ScanAssistState.ARMED,false).apply();
        MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
        if(svc!=null)svc.cancelAutoCount("Ferramentas flutuantes desligadas.");
        super.onDestroy();
    }
}
