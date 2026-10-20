package br.com.seridoplast.mlogbridge;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Bridge segura: só escreve quando a janela ativa é do MLOG.
 *  Nunca escreve em campo ambíguo; aprende o alvo tocado uma vez pelo operador.
 */
public class MlogAccessibilityService extends AccessibilityService {
    static final String TARGET = "com.targetsistemas.target_mlog2";
    static final String PREFS = "mlog_bridge";
    private static final String TAG = "MLOG-Bridge";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean subscribed;
    private long lastScanAt;
    private String lastScan = "";
    private long lastFocusAttempt;
    private final BroadcastReceiver scans = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String code = ScannerProtocol.barcode(intent);
            if (code == null || code.isEmpty()) return;
            long t = SystemClock.elapsedRealtime();
            if (code.equals(lastScan) && t-lastScanAt < 180) return;
            lastScan = code; lastScanAt = t;
            report("Recebido: " + code + " (" + ScannerProtocol.type(intent) + ")");
            if (getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("active",true)) {
                applyToMlog(code);
            }
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter filter = new IntentFilter();
        for (String a : ScannerProtocol.ACTIONS) filter.addAction(a);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(scans,filter,Context.RECEIVER_EXPORTED);
            else registerReceiver(scans,filter);
            subscribed = true;
            report("Serviço de acessibilidade ativado");
        } catch (Exception e) { report("Falha ao registrar recepção: "+e.getMessage()); }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent ev) {
        if (ev == null || ev.getPackageName() == null || !TARGET.contentEquals(ev.getPackageName())) return;
        if (getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("learn",false)
                && ev.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            AccessibilityNodeInfo source = ev.getSource();
            if (source != null && editable(source)) {
                learnFrom(source);
                return;
            }
        }
        if (!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("active",true)) return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastFocusAttempt < 350) return;
        lastFocusAttempt=now;
        ui.postDelayed(this::ensureFocus, 120);
    }

    private boolean onMlog() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        return root != null && root.getPackageName() != null && TARGET.contentEquals(root.getPackageName());
    }

    private boolean editable(AccessibilityNodeInfo node) {
        if(node == null) return false;
        return node.isEditable() || node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT)
            || (node.getClassName()!=null && "android.widget.EditText".contentEquals(node.getClassName()));
    }

    private void visit(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> fields,int depth) {
        if(n==null || depth>45 || fields.size()>100) return;
        if(editable(n) && n.isVisibleToUser() && n.isEnabled()) fields.add(n);
        for(int j=0; j<n.getChildCount();j++) {
            AccessibilityNodeInfo child=n.getChild(j);
            if(child!=null) visit(child,fields,depth+1);
        }
    }

    private static String s(CharSequence t){return t==null ? "" : t.toString();}
    private String label(AccessibilityNodeInfo n) {
        StringBuilder b = new StringBuilder();
        b.append(s(n.getContentDescription())).append(" ");
        b.append(s(n.getText())).append(" ");
        if(Build.VERSION.SDK_INT>=26) b.append(s(n.getHintText()));
        return b.toString().toLowerCase(java.util.Locale.ROOT);
    }
    private boolean codeHint(AccessibilityNodeInfo n) {
        String t=label(n);
        return t.contains("cód")||t.contains("codig")||t.contains("barcode")||
             t.contains("barras")||t.contains("produto")||t.contains("leitura")||t.contains("pesquisa");
    }

    /** Ambiguidade gera bloqueio, jamais lançamento de item no campo errado. */
    private AccessibilityNodeInfo choose(List<AccessibilityNodeInfo> f) {
        if(f.isEmpty()) return null;
        // Segurança: um único EditText pode ser login, busca ou quantidade;
        // não inserir um código até reconhecer o campo pelo treino ou hint.
        String savedId=getSharedPreferences(PREFS,MODE_PRIVATE).getString("field_id","");
        if(!savedId.isEmpty()) {
            AccessibilityNodeInfo match=null;
            for(AccessibilityNodeInfo n:f) {
                if(savedId.equals(n.getViewIdResourceName())) {
                    if(match!=null) return null;
                    match=n;
                }
            }
            if(match!=null) return match;
        }
        int trainedX=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("field_x",-1);
        int trainedY=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("field_y",-1);
        if(trainedX>=0 && trainedY>=0) {
            int sx=getResources().getDisplayMetrics().widthPixels;
            int sy=getResources().getDisplayMetrics().heightPixels;
            AccessibilityNodeInfo match=null;
            for(AccessibilityNodeInfo n:f) {
                Rect r=new Rect();n.getBoundsInScreen(r);
                int dx=Math.abs((int)(10000.0*r.centerX()/Math.max(1,sx))-trainedX);
                int dy=Math.abs((int)(10000.0*r.centerY()/Math.max(1,sy))-trainedY);
                if(dx<550 && dy<550){ if(match!=null)return null; match=n; }
            }
            if(match!=null)return match;
        }
        AccessibilityNodeInfo hintMatch=null;
        for(AccessibilityNodeInfo n:f) {
            if(codeHint(n)) {
                if(hintMatch!=null) return null;
                hintMatch=n;
            }
        }
        return hintMatch;
    }

    private AccessibilityNodeInfo targetField() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null || root.getPackageName()==null || !TARGET.contentEquals(root.getPackageName()))return null;
        List<AccessibilityNodeInfo> fields=new ArrayList<>();
        visit(root,fields,0);
        if(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("learn",false)) {
            for(AccessibilityNodeInfo n:fields) if(n.isFocused()) { learnFrom(n); break; }
        }
        return choose(fields);
    }

    private void learnFrom(AccessibilityNodeInfo n) {
        Rect r=new Rect();n.getBoundsInScreen(r);
        int sx=getResources().getDisplayMetrics().widthPixels;
        int sy=getResources().getDisplayMetrics().heightPixels;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString("field_id",n.getViewIdResourceName()==null?"":n.getViewIdResourceName())
            .putInt("field_x",(int)(10000.0*r.centerX()/Math.max(1,sx)))
            .putInt("field_y",(int)(10000.0*r.centerY()/Math.max(1,sy)))
            .putBoolean("learn",false).apply();
        report("Campo aprendido! " + label(n));
        Toast.makeText(this,"MLOG Bridge: campo aprendido",Toast.LENGTH_LONG).show();
    }

    private void ensureFocus() {
        if(!onMlog())return;
        AccessibilityNodeInfo field=targetField();
        if(field!=null && !field.isFocused()) {
            boolean ok=field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if(ok) report("Foco solicitado no campo do código");
        }
    }

    private void applyToMlog(String code) {
        // O hardware transmite globalmente: só inserir se o MLOG estiver de fato em primeiro plano.
        if(!onMlog()) { report("Leitura ignorada: MLOG não está em primeiro plano"); return; }
        AccessibilityNodeInfo f=targetField();
        if(f==null) {
            report("Campo não identificado; use APRENDER CAMPO com o MLOG aberto");
            return;
        }
        if(!f.isFocused()) f.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        if(!f.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT)) {
            report("Campo Flutter não permite ACTION_SET_TEXT; testar integração por teclado");
            return;
        }
        Bundle args=new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,code);
        boolean ok=f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
        report(ok ? "Código inserido no MLOG: "+code : "MLOG recusou inserir o código");
        if(ok && getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("submit",false)) {
            ui.postDelayed(() -> {
                if(!onMlog()) return;
                AccessibilityNodeInfo current=targetField();
                if(current==null)return;
                if(Build.VERSION.SDK_INT>=30) {
                    boolean sent=current.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                    report(sent ? "Confirmação IME solicitada" : "MLOG não disponibilizou confirmação IME");
                } else report("Enter por acessibilidade indisponível neste Android");
            },220);
        }
    }

    private void report(String msg) {
        Log.d(TAG,msg);
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString("last_status",msg)
            .putLong("last_status_at",System.currentTimeMillis()).apply();
    }

    @Override public void onInterrupt() { report("Serviço interrompido"); }
    @Override public void onDestroy() {
        if(subscribed) try {unregisterReceiver(scans);}catch(Exception ignored){}
        subscribed=false;
        super.onDestroy();
    }
}
