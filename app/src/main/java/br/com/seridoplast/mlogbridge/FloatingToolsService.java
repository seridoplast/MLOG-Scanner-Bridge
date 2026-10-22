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
import android.widget.CheckBox;
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
    private String statusText="Pronto. Ative a função desejada.";
    private final int background=0xff161616;
    private final int text=0xffffffff;

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
    private void draw(boolean open){
        if(wm==null)return;
        int oldX=params==null?dp(12):params.x;
        int oldY=params==null?dp(160):params.y;
        if(view!=null){try{wm.removeView(view);}catch(RuntimeException ignored){} view=null;}
        expanded=open;
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        if(open){
            layout.setBackground(bg(background,18));
            layout.setPadding(dp(16),dp(16),dp(16),dp(16));
        }else{
            layout.setPadding(0,0,0,0);
        }
        view=layout;
        params=new WindowManager.LayoutParams(
            open?dp(314):dp(40),WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE,
            open ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL :
                   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                       WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;
        int screenWidth=getResources().getDisplayMetrics().widthPixels;
        int windowWidth=open?dp(314):dp(40);
        params.x=Math.max(0,Math.min(oldX,Math.max(0,screenWidth-windowWidth)));
        params.y=Math.max(0,oldY);
        params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        if(!open){
            FrameLayout bubble=new FrameLayout(this);
            bubble.setBackground(bg(0xff252525,40));
            bubble.setAlpha(.50f);
            bubble.setContentDescription("Abrir ferramentas do MLOG Bridge");
            ImageView gear=new ImageView(this);
            gear.setImageResource(R.drawable.ic_floating_gear);
            gear.setColorFilter(0xffffffff);
            gear.setPadding(dp(9),dp(9),dp(9),dp(9));
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
            SharedPreferences p=ScanAssistState.prefs(this);
            CheckBox cleaner=new CheckBox(this);cleaner.setText("Tratar códigos invisíveis");
            cleaner.setTextColor(text);cleaner.setChecked(p.getBoolean(ScanAssistState.CLEAN,false));
            cleaner.setOnCheckedChangeListener((b,on)->{
                p.edit().putBoolean(ScanAssistState.CLEAN,on).apply();
                statusText=on?"Tratador ativo para códigos por broadcast.":"Tratador desativado.";
                if(status!=null)status.setText(statusText);
            });LinearLayout.LayoutParams cleanerParams=new LinearLayout.LayoutParams(-1,-2);
            cleanerParams.topMargin=dp(12);layout.addView(cleaner,cleanerParams);
            TextView hint=label("Remove espaços, Enter/Tab e caracteres ocultos. Não modifica dígitos nem pontuação.",12,false);
            hint.setTextColor(0xffcccccc);
            LinearLayout.LayoutParams hintParams=new LinearLayout.LayoutParams(-1,-2);
            hintParams.leftMargin=dp(5);hintParams.rightMargin=dp(5);
            layout.addView(hint,hintParams);
            CheckBox batch=new CheckBox(this);batch.setText("Bipes automáticos (N−1)");batch.setTextColor(text);
            batch.setChecked(p.getBoolean(ScanAssistState.BATCH,false));
            batch.setOnCheckedChangeListener((b,on)->{
                p.edit().putBoolean(ScanAssistState.BATCH,on).putBoolean(ScanAssistState.ARMED,false).apply();
                MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
                if(svc!=null)svc.cancelAutoCount("Bipes automáticos desativados.");
                statusText=on?"Bipes automáticos: informe o total e arme.":"Bipes automáticos desativados.";
                if(status!=null)status.setText(statusText);
            });LinearLayout.LayoutParams batchParams=new LinearLayout.LayoutParams(-1,-2);
            batchParams.topMargin=dp(12);layout.addView(batch,batchParams);
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0,dp(7),0,dp(7));
            row.addView(label("Total de bipes: ",14,true));
            EditText amount=new EditText(this);amount.setSingleLine(true);amount.setTextColor(text);
            amount.setHintTextColor(0xffbbbbbb);amount.setInputType(InputType.TYPE_CLASS_NUMBER);
            amount.setSelectAllOnFocus(true);
            amount.setText(String.valueOf(ScanAssistState.clamp(p.getInt(ScanAssistState.COUNT,1))));
            row.addView(amount,new LinearLayout.LayoutParams(dp(80),dp(48)));layout.addView(row);
            TextView arm=button("▶ Armar 1 bipe físico + N−1",()->{
                int count;
                try{count=Integer.parseInt(amount.getText().toString());}catch(NumberFormatException e){count=0;}
                if(count<1 || count>ScanAssistState.MAX_COUNT){
                    statusText="Informe de 1 a "+ScanAssistState.MAX_COUNT+" bipes no total.";
                    if(status!=null)status.setText(statusText);return;
                }
                if(!batch.isChecked()){
                    statusText="Ative Bipes automáticos primeiro.";
                    if(status!=null)status.setText(statusText);return;
                }
                MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
                if(svc!=null)svc.cancelAutoCount(null);
                p.edit().putInt(ScanAssistState.COUNT,count)
                    .putBoolean(ScanAssistState.ARMED,true).apply();
                statusText="Armado: 1 bipe físico + "+AutoRepeatPolicy.virtualRepeats(count)+" automáticos ("+count+" no total).";
                ScanAssistState.report(statusText);
                collapse();
            });layout.addView(arm);
            TextView stop=button("■ Cancelar bipes automáticos",()->{
                p.edit().putBoolean(ScanAssistState.ARMED,false).apply();
                MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
                if(svc!=null)svc.cancelAutoCount("Bipes automáticos cancelados.");
            });LinearLayout.LayoutParams stopParams=new LinearLayout.LayoutParams(-1,-2);
            stopParams.topMargin=dp(8);layout.addView(stop,stopParams);
            status=label(statusText,12,false);status.setTextColor(0xffcfcfcf);
            LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);
            sl.topMargin=dp(12);layout.addView(status,sl);
            TextView foot=label("Ex.: 100 = 1 bipe físico + 99 virtuais. Mínimo de 155 ms e confirmação do MLOG entre cada repetição. Se falhar, para.",12,false);
            foot.setTextColor(0xffb8b8b8);
            LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,-2);fl.topMargin=dp(9);
            layout.addView(foot,fl);
        }
        try{wm.addView(view,params);}catch(RuntimeException ex){
            view=null;stopSelf();
        }
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
