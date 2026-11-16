package br.com.seridoplast.mlogbridge;

import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;
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
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

/** Optional user-controlled overlay. Never captures screen contents. */
public final class FloatingToolsService extends Service {
    private static FloatingToolsService visible;
    static final String ACTION_REFRESH_THEME = "br.com.seridoplast.mlogbridge.REFRESH_FLOATING_THEME";
    static boolean isRunning(){return visible!=null && visible.view!=null;}
    static boolean isBatchInputOpen(){
        FloatingToolsService svc=visible;
        return svc!=null && svc.expanded && svc.batchDrawerOpen && ScanAssistState.isBatch(svc);
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refreshNotes=new Runnable(){
        @Override public void run(){
            OrderNotesClient.heartbeat(FloatingToolsService.this);
            if(!activeOrder.isEmpty()&&OrderNotesClient.configured(FloatingToolsService.this))
                OrderNotesClient.refresh(FloatingToolsService.this,activeOrder);
            main.postDelayed(this,15000L);
        }
    };
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private View view;
    private TextView status;
    private boolean expanded;
    private int selectedMessagePage=FloatingMessageViewPolicy.OBSERVATION;
    // Persistir posição da BOLHA, não a coordenada do painel expandido.
    private int bubbleX=-1, bubbleY=-1;
    private boolean batchDrawerOpen;
    private String statusText="Tratador de códigos disponível.";
    private String activeOrder="";
    private final ArrayList<OrderNotesClient.Note> activeNotes=new ArrayList<>();
    private static final class PendingCompletion {
        final String order;
        final ArrayList<OrderNotesClient.Note> notes;
        PendingCompletion(String order,List<OrderNotesClient.Note> notes){
            this.order=order;this.notes=new ArrayList<>(notes);
        }
    }
    private final ArrayList<PendingCompletion> pendingCompletions=new ArrayList<>();
    private String confirmingOrder="";
    private final ArrayList<OrderNotesClient.Broadcast> activeBroadcasts=new ArrayList<>();
    private boolean confirmComplete;
    private int background, text, mutedText, green, red, accent, buttonBackground;
    private int buttonStroke, dividerColor, actionBackground, inputHint;
    // 40 dp para nao cobrir campos da separacao; fundo e icone com 15% de opacidade.
    private static final int BUBBLE_DP=40;
    // Alvo de toque maior que a bolha visual, para operacao com luvas no CD.
    private static final int BUBBLE_TOUCH_DP=48;
    // Aplicar opacidade no contêiner, e nao apenas no fundo, para incluir o icone.
    private static final int BUBBLE_FILL=0xff000000;
    private static final float BUBBLE_OPACITY=0.15f;

    /** A change of order permits confirmation of the PREVIOUS order, not automatic completion.
     * Navigation, cancellation and reassignment are not proof of finished separation. */
    static void orderHeaderChanged(String previous,String next){
        FloatingToolsService svc=visible;
        if(svc==null || previous==null || next==null || previous.equals(next))return;
        svc.main.post(()->{
            if(!previous.equals(svc.activeOrder))return;
            if(!svc.activeNotes.isEmpty()){
                boolean queued=false;
                for(PendingCompletion pending:svc.pendingCompletions){
                    if(previous.equals(pending.order)){queued=true;break;}
                }
                if(!queued)svc.pendingCompletions.add(new PendingCompletion(previous,svc.activeNotes));
            }
            svc.activeNotes.clear();
            svc.activeOrder="";
            svc.confirmComplete=false;
            svc.confirmingOrder="";
            svc.draw(svc.expanded);
        });
    }

    static void showOrderNotes(String order,List<OrderNotesClient.Note> notes){
        FloatingToolsService instance=visible;
        if(instance==null)return;
        instance.main.post(()->{
            boolean orderChanged=!order.equals(instance.activeOrder);
            boolean changed=orderChanged || (notes==null?0:notes.size())!=instance.activeNotes.size();
            boolean newObservation=false;
            if(notes!=null){
                for(OrderNotesClient.Note next:notes){
                    boolean existing=false;
                    for(OrderNotesClient.Note previous:instance.activeNotes){
                        if(previous.id.equals(next.id)){
                            existing=true;
                            if(!previous.text.equals(next.text))changed=true;
                            break;
                        }
                    }
                    if(!existing){newObservation=true;changed=true;}
                }
            }
            instance.activeOrder=order;
            if(!changed)return;
            instance.activeNotes.clear();
            if(notes!=null)instance.activeNotes.addAll(notes);
            if(orderChanged)instance.confirmComplete=false;
            if(newObservation&&!instance.activeNotes.isEmpty()){
                instance.selectedMessagePage=FloatingMessageViewPolicy.OBSERVATION;
                instance.draw(true);
            }else instance.draw(instance.expanded);
        });
    }
    static void showBroadcasts(List<OrderNotesClient.Broadcast> messages){
        showBroadcasts(messages,null);
    }
    static void showBroadcasts(List<OrderNotesClient.Broadcast> messages,Runnable confirmedByApp){
        FloatingToolsService instance=visible;if(instance==null)return;
        instance.main.post(()->{
            boolean changed=(messages==null?0:messages.size())!=instance.activeBroadcasts.size();
            boolean newMessage=false;
            if(messages!=null){
                for(OrderNotesClient.Broadcast message:messages){
                    boolean found=false;
                    for(OrderNotesClient.Broadcast previous:instance.activeBroadcasts){
                        if(previous.id.equals(message.id)){found=true; if(!previous.text.equals(message.text))changed=true;break;}
                    }
                    if(!found){newMessage=true;changed=true;}
                }
            }
            if(changed){
                instance.activeBroadcasts.clear();
                if(messages!=null)instance.activeBroadcasts.addAll(messages);
                if(newMessage)instance.selectedMessagePage=FloatingMessageViewPolicy.GENERAL_NOTICE;
                instance.draw(newMessage || instance.expanded);
            }
            // Ack only once the overlay accepted the broadcast payload.
            if(confirmedByApp!=null)confirmedByApp.run();
        });
    }
    static void clearCompleted(String order){
        FloatingToolsService instance=visible;
        if(instance==null)return;
        instance.main.post(()->{
            if(!order.equals(instance.activeOrder))return;
            instance.activeNotes.clear();instance.activeOrder="";instance.confirmComplete=false;instance.draw(false);
        });
    }

    static void report(String message){
        FloatingToolsService instance=visible;
        if(instance!=null) instance.main.post(()->{
            instance.statusText=message;
            if(instance.status!=null){
                instance.status.setText(message);
                // Sem texto de rodape com bipes automaticos desativados.
                instance.status.setVisibility(ScanAssistState.isBatch(instance)?View.VISIBLE:View.GONE);
            }
        });
    }

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
            stopSelf();return START_NOT_STICKY;
        }
        visible=this;
        if(wm==null)wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
        if(intent!=null && ACTION_REFRESH_THEME.equals(intent.getAction())){
            if(view!=null)draw(expanded);
            return START_NOT_STICKY;
        }
        // Never preserve an armed batch across process/service restarts.
        if(view==null){
            OrderNotesClient.startBeaconListener(this);
            main.removeCallbacks(refreshNotes);main.postDelayed(refreshNotes,1500L);
            android.content.SharedPreferences p=ScanAssistState.prefs(this);
            bubbleX=p.getInt("floating_gear_x",dp(12));
            bubbleY=p.getInt("floating_gear_y",dp(160));
            // Never restore an ARMED batch after process restart (safety).
            p.edit().putBoolean(ScanAssistState.ARMED,false).putBoolean(ScanAssistState.CLEAN,true).apply();
            draw(false);
        }
        return START_NOT_STICKY;
    }
    @Override public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        if(view!=null)draw(expanded);
    }
    private void applyTheme(){
        String choice=ScanAssistState.prefs(this).getString("theme_choice","auto");
        boolean dark="dark".equals(choice) || (!"light".equals(choice) &&
            (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==
                Configuration.UI_MODE_NIGHT_YES);
        background=dark?0xff172337:0xffffffff;
        text=dark?0xfff4f7ff:0xff17233a;
        mutedText=dark?0xffa8bddb:0xff63758d;
        green=dark?0xff4bd79c:0xff138255;
        red=dark?0xfffb7b83:0xffb63747;
        accent=dark?0xff8ab4ff:0xff2864e8;
        buttonBackground=dark?0xff253b60:0xffe9f0ff;
        buttonStroke=dark?0xff324763:0xffdce5f2;
        dividerColor=dark?0xff33465e:0xffdce5f2;
        actionBackground=dark?0xff275dd0:0xff2864e8;
        inputHint=dark?0xffa8bddb:0xff63758d;
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int corner){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(corner));
        d.setStroke(dp(1),buttonStroke);return d;
    }
    private GradientDrawable accentBubble(){
        GradientDrawable d=new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(BUBBLE_FILL);
        // Sem contorno: mantem a bolha discreta sobre o MLOG.
        return d;
    }
    private TextView label(String value,int size,boolean bold){
        TextView t=new TextView(this); t.setText(value);t.setTextColor(text);
        t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView button(String value,Runnable cb){
        TextView t=label(value,13,true);t.setPadding(dp(14),dp(14),dp(14),dp(14));
        t.setGravity(Gravity.CENTER_VERTICAL);t.setBackground(bg(buttonBackground,14));
        t.setFocusable(false); // Enter do scanner nunca ativa ferramentas acidentalmente.
        t.setOnClickListener(v->cb.run());return t;
    }
    private TextView iconButton(String value,int iconRes,Runnable callback){
        TextView t=button(value,callback);
        leadingIcon(t,iconRes,text,17);
        return t;
    }
    private void leadingIcon(TextView view,int res,int tint,int size){
        android.graphics.drawable.Drawable drawable=getDrawable(res);
        if(drawable==null)return;
        drawable=drawable.mutate();drawable.setTint(tint);
        drawable.setBounds(0,0,dp(size),dp(size));
        view.setCompoundDrawablesRelative(drawable,null,null,null);
        view.setCompoundDrawablePadding(dp(8));
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
        t.setTextColor(mutedText);
        return t;
    }
    private Switch toggle(boolean active){
        Switch sw=new Switch(this);
        sw.setChecked(active);
        sw.setShowText(false);
        int[][] states=new int[][]{new int[]{android.R.attr.state_checked},new int[]{}};
        sw.setThumbTintList(new ColorStateList(states,new int[]{green,red}));
        sw.setTrackTintList(new ColorStateList(states,new int[]{0xff246e59,0xff96a2b5}));
        sw.setContentDescription(active?"Ativado":"Desativado");
        sw.setOnCheckedChangeListener((v,on)->sw.setContentDescription(on?"Ativado":"Desativado"));
        return sw;
    }
    private View separator(){
        View line=new View(this);line.setBackgroundColor(dividerColor);
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
            TextView description=muted(sub,10);
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
    private TextView messageBody(String message){
        TextView body=label(message,18,false);
        body.setTextColor(0xfff7fbff);
        body.setLineSpacing(dp(4),1.04f);
        body.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
        return body;
    }
    private TextView compactLink(String name,Runnable callback){
        TextView link=label(name,11,true);
        link.setGravity(Gravity.CENTER);
        link.setPadding(dp(5),dp(11),dp(5),dp(11));
        link.setBackground(bg(buttonBackground,11));
        link.setOnClickListener(v->callback.run());
        link.setFocusable(false);
        return link;
    }
    private void switchMessagePage(int page){
        selectedMessagePage=page;
        confirmingOrder="";
        draw(true);
    }
    private void draw(boolean open){
        if(wm==null)return;
        applyTheme();
        if(open){
            // Sempre usar a mesma identidade da referência para observações e avisos.
            background=0xff071e32;
            text=0xfffcfdff;
            mutedText=0xffb8cede;
            accent=0xffa5c8e9;
            buttonBackground=0xff103857;
            buttonStroke=0xff234b67;
            dividerColor=0xff294b62;
            actionBackground=0xff164c75;
            inputHint=0xffb8cede;
        }
        if(bubbleX<0)bubbleX=dp(12);
        if(bubbleY<0)bubbleY=dp(160);
        // Abrir painel pode deslocá-lo para caber na tela; não deslocar a engrenagem.
        int oldX=bubbleX;
        int oldY=bubbleY;
        if(view!=null){try{wm.removeView(view);}catch(RuntimeException ignored){} view=null;}
        expanded=open;
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        if(open){
            GradientDrawable panel=new GradientDrawable();
            panel.setColor(background);
            panel.setCornerRadius(dp(25));
            layout.setBackground(panel);
            layout.setElevation(dp(12));
            layout.setPadding(dp(21),dp(19),dp(21),dp(22));
        }
        view=layout;
        // A janela expandida antiga roubava o foco do Flutter ate para
        // interagir com as ferramentas. Somente o campo numerico do lote precisa
        // receber foco de teclado; demais botoes permanecem nao focaveis.
        boolean needsTextInput=open && batchDrawerOpen && ScanAssistState.isBatch(this);
        int overlayFlags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if(!needsTextInput)overlayFlags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        int panelWidth=Math.min(dp(350),Math.max(dp(260),
            getResources().getDisplayMetrics().widthPixels-dp(22)));
        params=new WindowManager.LayoutParams(
            open?panelWidth:dp(BUBBLE_TOUCH_DP),WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:
                WindowManager.LayoutParams.TYPE_PHONE,
            overlayFlags,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;
        int width=getResources().getDisplayMetrics().widthPixels;
        params.x=Math.max(0,Math.min(oldX,Math.max(0,width-(open?panelWidth:dp(BUBBLE_TOUCH_DP)))));
        params.y=Math.max(0,Math.min(oldY,Math.max(0,getResources().getDisplayMetrics().heightPixels-dp(72))));
        params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        if(!open){
            FrameLayout touchTarget=new FrameLayout(this);
            touchTarget.setContentDescription("Abrir observações e ferramentas do MLOG Bridge");
            FrameLayout bubble=new FrameLayout(this);
            bubble.setBackground(accentBubble());
            // Toda a bolha, incluindo a engrenagem branca, fica com 15% de opacidade.
            // A area de toque de 48 dp permanece 100% ativa e invisivel.
            bubble.setAlpha(BUBBLE_OPACITY);
            bubble.setElevation(0f);
            ImageView bell=new ImageView(this);
            bell.setImageResource(R.drawable.ui_bell);
            bell.setColorFilter(0xffffffff);bell.setPadding(dp(9),dp(9),dp(9),dp(9));
            bubble.addView(bell,new FrameLayout.LayoutParams(dp(BUBBLE_DP),dp(BUBBLE_DP),Gravity.CENTER));
            touchTarget.addView(bubble,new FrameLayout.LayoutParams(dp(BUBBLE_DP),dp(BUBBLE_DP),Gravity.CENTER));
            int unread=activeNotes.size()+activeBroadcasts.size()+pendingCompletions.size();
            if(unread>0){
                TextView badge=label(String.valueOf(Math.min(unread,99)),11,true);
                badge.setGravity(Gravity.CENTER);badge.setTextColor(0xffffffff);
                badge.setBackground(bg(0xffdd304a,12));
                FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.TOP|Gravity.END);
                touchTarget.addView(badge,bp);
            }
            layout.addView(touchTarget,new LinearLayout.LayoutParams(dp(BUBBLE_TOUCH_DP),dp(BUBBLE_TOUCH_DP)));
            touchTarget.setOnTouchListener(new View.OnTouchListener(){
                private float downX,downY;private int startX,startY;private boolean moved;
                @Override public boolean onTouch(View v,MotionEvent e){
                    if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                        downX=e.getRawX();downY=e.getRawY();startX=params.x;startY=params.y;
                        moved=false;return true;
                    }
                    if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                        int dx=(int)(e.getRawX()-downX),dy=(int)(e.getRawY()-downY);
                        if(Math.abs(dx)>dp(5)||Math.abs(dy)>dp(5))moved=true;
                        if(moved){
                            int width=getResources().getDisplayMetrics().widthPixels;
                            int height=getResources().getDisplayMetrics().heightPixels;
                            params.x=Math.max(0,Math.min(width-dp(BUBBLE_TOUCH_DP),startX+dx));
                            params.y=Math.max(0,Math.min(height-dp(72),startY+dy));
                            bubbleX=params.x;bubbleY=params.y;
                            try{wm.updateViewLayout(view,params);}catch(RuntimeException ignored){}
                        }
                        return true;
                    }
                    if(e.getActionMasked()==MotionEvent.ACTION_UP){
                        if(moved)ScanAssistState.prefs(FloatingToolsService.this).edit()
                            .putInt("floating_gear_x",bubbleX)
                            .putInt("floating_gear_y",bubbleY).apply();
                        else {
                            draw(true);
                            if(!activeOrder.isEmpty())OrderNotesClient.refresh(FloatingToolsService.this,activeOrder);
                        }
                        return true;
                    }
                    return true;
                }
            });
        }else{
            int currentPage=FloatingMessageViewPolicy.select(selectedMessagePage,
                activeNotes.size(),activeBroadcasts.size(),pendingCompletions.size());
            selectedMessagePage=currentPage;
            String headingText=currentPage==FloatingMessageViewPolicy.OBSERVATION?"Observação":
                currentPage==FloatingMessageViewPolicy.GENERAL_NOTICE?"Aviso geral":
                currentPage==FloatingMessageViewPolicy.COMPLETION?"Concluir pedido":"Mensagens";
            LinearLayout header=new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView heading=label(headingText,23,true);
            header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
            ImageView minimize=new ImageView(this);
            minimize.setImageResource(R.drawable.ui_minus);
            minimize.setColorFilter(0xffffffff);
            minimize.setPadding(dp(10),dp(10),dp(10),dp(10));
            minimize.setBackground(bg(buttonBackground,13));
            minimize.setContentDescription("Recolher observação ou aviso");
            minimize.setOnClickListener(v->collapse());
            LinearLayout.LayoutParams minLp=new LinearLayout.LayoutParams(dp(43),dp(43));
            minLp.leftMargin=dp(10);
            header.addView(minimize,minLp);
            layout.addView(header);
            layout.addView(separator());
            SharedPreferences pref=ScanAssistState.prefs(this);

            if(currentPage==FloatingMessageViewPolicy.OBSERVATION){
                TextView orderLabel=muted("Pedido "+activeOrder,12);
                LinearLayout.LayoutParams orderLp=new LinearLayout.LayoutParams(-1,-2);
                orderLp.bottomMargin=dp(11);
                layout.addView(orderLabel,orderLp);
                for(int i=0;i<activeNotes.size();i++){
                    if(i>0)layout.addView(separator());
                    TextView message=messageBody(activeNotes.get(i).text);
                    layout.addView(message,new LinearLayout.LayoutParams(-1,-2));
                }
                // Não mostrar botão 'Entendido' nas observações de pedidos.
                // A conclusão permanece disponível somente após trocar de pedido.
            }else if(currentPage==FloatingMessageViewPolicy.GENERAL_NOTICE){
                for(int i=0;i<activeBroadcasts.size();i++){
                    if(i>0)layout.addView(separator());
                    OrderNotesClient.Broadcast alert=activeBroadcasts.get(i);
                    layout.addView(messageBody(alert.text),new LinearLayout.LayoutParams(-1,-2));
                    TextView understood=iconButton("Entendido",R.drawable.ui_check_circle,()->{
                        OrderNotesClient.seen(this,alert.id,(ok,message)->{
                            if(ok){
                                activeBroadcasts.removeIf(n->n.id.equals(alert.id));
                                if(activeBroadcasts.isEmpty() && activeNotes.isEmpty() &&
                                   pendingCompletions.isEmpty() && !ScanAssistState.isBatch(this))
                                    collapse();
                                else draw(true);
                            }else android.widget.Toast.makeText(this,message,
                                android.widget.Toast.LENGTH_LONG).show();
                        });
                    });
                    understood.setGravity(Gravity.CENTER);
                    understood.setTextColor(0xffffffff);
                    understood.setBackground(bg(actionBackground,13));
                    LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);
                    ap.topMargin=dp(19);
                    layout.addView(understood,ap);
                }
            }else if(currentPage==FloatingMessageViewPolicy.COMPLETION){
                layout.addView(messageBody("Confirme somente após finalizar a separação no MLOG."));
                for(PendingCompletion pending:new ArrayList<>(pendingCompletions)){
                    TextView done=iconButton(pending.order.equals(confirmingOrder)?
                        "Confirmar pedido "+pending.order:"Concluir pedido "+pending.order,
                        R.drawable.ui_check_circle,()->{
                            if(!pending.order.equals(confirmingOrder)){
                                confirmingOrder=pending.order;draw(true);return;
                            }
                            confirmingOrder="";
                            OrderNotesClient.complete(this,pending.order,pending.notes,(ok,message)->{
                                if(ok){
                                    pendingCompletions.removeIf(n->n.order.equals(pending.order));
                                    if(pendingCompletions.isEmpty() && activeBroadcasts.isEmpty() &&
                                       activeNotes.isEmpty() && !ScanAssistState.isBatch(this))collapse();
                                    else draw(true);
                                }else android.widget.Toast.makeText(this,message,
                                    android.widget.Toast.LENGTH_LONG).show();
                            });
                        });
                    done.setBackground(bg(actionBackground,13));
                    done.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                    lp.topMargin=dp(12);layout.addView(done,lp);
                }
                if(!confirmingOrder.isEmpty()){
                    TextView warning=muted("Toque novamente para confirmar a conclusão.",12);
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                    lp.topMargin=dp(8);layout.addView(warning,lp);
                }
            }else{
                layout.addView(messageBody("Nenhuma mensagem pendente."));
            }

            // Só aparece quando há outra categoria de mensagem disponível.
            LinearLayout navigation=new LinearLayout(this);
            navigation.setOrientation(LinearLayout.HORIZONTAL);
            navigation.setGravity(Gravity.CENTER_VERTICAL);
            int navItems=0;
            if(!activeNotes.isEmpty()&&currentPage!=FloatingMessageViewPolicy.OBSERVATION){
                TextView openNote=compactLink("Observação",()->switchMessagePage(FloatingMessageViewPolicy.OBSERVATION));
                navigation.addView(openNote,new LinearLayout.LayoutParams(0,-2,1));navItems++;
            }
            if(!activeBroadcasts.isEmpty()&&currentPage!=FloatingMessageViewPolicy.GENERAL_NOTICE){
                TextView openNotice=compactLink("Aviso geral ("+activeBroadcasts.size()+")",
                    ()->switchMessagePage(FloatingMessageViewPolicy.GENERAL_NOTICE));
                navigation.addView(openNotice,new LinearLayout.LayoutParams(0,-2,1));navItems++;
            }
            if(!pendingCompletions.isEmpty()&&currentPage!=FloatingMessageViewPolicy.COMPLETION){
                TextView openDone=compactLink("Concluir ("+pendingCompletions.size()+")",
                    ()->switchMessagePage(FloatingMessageViewPolicy.COMPLETION));
                navigation.addView(openDone,new LinearLayout.LayoutParams(0,-2,1));navItems++;
            }
            if(navItems>0){
                LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,-2);
                nlp.topMargin=dp(21);layout.addView(navigation,nlp);
            }
            // O recurso protegido só aparece no flutuante quando liberado em Ajustes.
            if(ScanAssistState.isBatch(this)){
            layout.addView(separator());
            TextView batchTitle=iconButton("Bipes automáticos",R.drawable.ui_chevron_down,()->{
                batchDrawerOpen=!batchDrawerOpen;
                draw(true);
            });
            layout.addView(batchTitle,new LinearLayout.LayoutParams(-1,-2));
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
                amount.setTextColor(text);amount.setHintTextColor(inputHint);
                amount.setInputType(InputType.TYPE_CLASS_NUMBER);
                amount.setSelectAllOnFocus(true);
                amount.setText(String.valueOf(ScanAssistState.clamp(pref.getInt(ScanAssistState.COUNT,1))));
                row.addView(amount,new LinearLayout.LayoutParams(dp(65),dp(48)));
                drawer.addView(row,rlp);
                TextView arm=iconButton("Armar próximo bipe",R.drawable.ui_play,()->{
                    int count;
                    try{count=Integer.parseInt(amount.getText().toString());}
                    catch(NumberFormatException ex){count=0;}
                    if(count<1 || count>ScanAssistState.MAX_COUNT){
                        statusText="Informe de 1 a "+ScanAssistState.MAX_COUNT+" bipes.";
                        if(status!=null)status.setText(statusText);return;
                    }
                    if(!ScanAssistState.isBatch(this)){
                        statusText="Ative Bipes automáticos em Ajustes com senha.";
                        if(status!=null)status.setText(statusText);return;
                    }
                    if(pref.getBoolean(ShizukuScanMode.PREF,false) && !ShizukuHealthMonitor.ready()){
                        statusText="Shizuku desligado. Não armei bipes automáticos.";
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
                TextView stop=iconButton("Cancelar bipes automáticos",R.drawable.ui_square,()->{
                    pref.edit().putBoolean(ScanAssistState.ARMED,false).apply();
                    MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
                    if(svc!=null)svc.cancelAutoCount("Bipes automáticos cancelados.");
                });
                LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);
                slp.topMargin=dp(8);drawer.addView(stop,slp);
                TextView foot=muted("Confirme a quantidade no MLOG após o lote.",11);
                LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,-2);
                fl.topMargin=dp(10);drawer.addView(foot,fl);
            }
            } // fim da seção de bipes automáticos autorizados
            // Mostrar mensagens do lote apenas quando bipes automaticos estao ativos.
            // Sem uma faixa de status vazia ou "Bipes automaticos desativados" ao desligar.
            status=muted(statusText,12);
            status.setVisibility(ScanAssistState.isBatch(this)?View.VISIBLE:View.GONE);
            LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);
            sl.topMargin=dp(16);layout.addView(status,sl);
        }
        if(open){
            // Limitar o tamanho sem criar uma janela gigante em pedidos curtos.
            ScrollView scroller=new ScrollView(this){
                @Override protected void onMeasure(int widthSpec,int heightSpec){
                    int available=getResources().getDisplayMetrics().heightPixels-dp(120);
                    int cap=Math.min(dp(560),Math.max(dp(180),available));
                    super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST));
                }
            };
            scroller.setFillViewport(false);scroller.setVerticalScrollBarEnabled(true);
            scroller.addView(layout);
            view=scroller;
            // A bolha continua na posicao original apos fechar o painel.
            params.y=Math.max(0,Math.min(oldY,dp(60)));
        }
        try{wm.addView(view,params);}catch(RuntimeException ex){view=null;stopSelf();}
    }
    @Override public void onDestroy(){
        OrderNotesClient.stopBeaconListener();
        main.removeCallbacks(refreshNotes);
        if(visible==this)visible=null;
        if(wm!=null && view!=null)try{wm.removeView(view);}catch(RuntimeException ignored){}
        view=null;
        ScanAssistState.prefs(this).edit().putBoolean(ScanAssistState.ARMED,false).apply();
        MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
        if(svc!=null)svc.cancelAutoCount("Ferramentas flutuantes desligadas.");
        super.onDestroy();
    }
}
