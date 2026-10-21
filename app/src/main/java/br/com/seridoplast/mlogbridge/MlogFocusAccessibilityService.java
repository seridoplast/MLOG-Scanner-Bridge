package br.com.seridoplast.mlogbridge;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Foco assistido EXCLUSIVAMENTE no MLOG.
 * Nao le outros aplicativos, nao captura teclas/senhas, nao usa coordenadas
 * de toque nem APIs de gestos, nao armazena texto de tela/codigos.
 * O Flutter pode nao publicar o botao na arvore de acessibilidade: nesse caso
 * falha com seguranca, ao inves de tocar numa posicao arbitraria.
 */
public class MlogFocusAccessibilityService extends AccessibilityService {
    private static volatile MlogFocusAccessibilityService instance;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private BroadcastReceiver scanReceiver;
    private long lastProbe, lastAutoTry;
    private boolean registered;
    private static final String TARGET=ScanCoordinator.TARGET;

    static MlogFocusAccessibilityService current() { return instance; }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance=this;
        scanReceiver=new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                ScanCoordinator.onScan(MlogFocusAccessibilityService.this,ScannerProtocol.barcode(intent));
            }
        };
        IntentFilter f=new IntentFilter();
        for(String action:ScannerProtocol.ACTIONS) f.addAction(action);
        try {
            if(Build.VERSION.SDK_INT>=33) registerReceiver(scanReceiver,f,Context.RECEIVER_EXPORTED);
            else registerReceiver(scanReceiver,f);
            registered=true;
            ScanCoordinator.status(this,"Acessibilidade conectada: aguardando MLOG.");
        } catch(RuntimeException e) {
            ScanCoordinator.status(this,"Nao foi possivel receber os eventos do scanner.");
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event==null || event.getPackageName()==null ||
                !TARGET.contentEquals(event.getPackageName())) return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastProbe<700) return;
        lastProbe=now;
        ui.postDelayed(() -> {
            AccessibilityNodeInfo root=mlogRoot();
            if(root==null) return;
            UiScan s=inspect(root);
            ScanCoordinator.status(this, describe(s));
            SharedPreferences prefs=getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE);
            if(!ScanCoordinator.isActive(this) || !prefs.getBoolean("auto_focus",true))return;
            if(!(s.separation || (s.workScreen && s.labeledKeyboard!=null)) ||
                    now-lastAutoTry<4500) return;
            // Nunca roubar foco enquanto a pessoa esta digitando quantidade ou texto.
            if(ScanCoordinator.hasAnyMlogEditor() || ScanCoordinator.isArmed())return;
            lastAutoTry=SystemClock.elapsedRealtime();
            openCodeField(s, false);
        },200);
    }

    @Override public void onInterrupt() {
        ScanCoordinator.status(this,"Servico de foco interrompido pelo Android.");
    }

    @Override public void onDestroy() {
        if(instance==this) instance=null;
        if(registered)try{ unregisterReceiver(scanReceiver); }catch(RuntimeException ignored){}
        ui.removeCallbacksAndMessages(null);
        ScanCoordinator.onInputFinished();
        super.onDestroy();
    }

    private AccessibilityNodeInfo mlogRoot() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        return root!=null && root.getPackageName()!=null &&
                TARGET.contentEquals(root.getPackageName())?root:null;
    }

    void handleBarcode(String barcode) {
        if(barcode==null || !ScanCoordinator.isActive(this)) return;
        // Com IME visivel, a janela ativa pode ser o proprio teclado invisivel.
        // O EditorInfo do IME confirma o pacote MLOG sem depender da janela.
        if(ScanCoordinator.insertIfArmed(barcode))return;
        AccessibilityNodeInfo root=mlogRoot();
        if(root==null) {
            ScanCoordinator.status(this,"Leitura ignorada: janela do MLOG nao acessivel.");return;
        }
        UiScan s=inspect(root);
        // Mesmo com uma tela diferente, so abre um botao rotulado e inequivoco.
        if((!s.separation && !s.workScreen) ||
                (s.labeledKeyboard==null && !s.separation)) {
            ScanCoordinator.status(this,"Campo nao identificado nesta tela; nada foi enviado.");
            return;
        }
        ScanCoordinator.queueAfterClick(barcode);
        ui.postDelayed(ScanCoordinator::expirePending,2100);
        if(!openCodeField(s,true)) {
            ScanCoordinator.cancelExpect();
            ScanCoordinator.status(this,"Nao achei o botao de codigo com confianca; leitura nao inserida.");
        }
    }

    private boolean openCodeField(UiScan scan,boolean fromScan) {
        AccessibilityNodeInfo button=scan.labeledKeyboard;
        if(button==null && scan.separation && getSharedPreferences(ScanCoordinator.PREFS,MODE_PRIVATE)
                .getBoolean("layout_fallback",false)) button=scan.relativeKeyboard;
        if(button==null || !button.isEnabled() || !button.isVisibleToUser()) {
            if(fromScan) ScanCoordinator.status(this,"Botao teclado nao acessivel nesta tela. Veja diagnostico.");
            return false;
        }
        ScanCoordinator.expectCodeEditor();
        boolean clicked=button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if(!clicked) {
            ScanCoordinator.cancelExpect();
            ScanCoordinator.status(this,"MLOG recusou abrir campo do codigo.");
            return false;
        }
        ScanCoordinator.status(this,"Botao de codigo acionado; aguardando campo de texto.");
        // Algumas versoes Flutter nao disparam novo onStartInput apos o clique.
        ui.postDelayed(ScanCoordinator::flushIfEditorOpened,220);
        ui.postDelayed(ScanCoordinator::flushIfEditorOpened,520);
        return true;
    }

    private String describe(UiScan scan) {
        if(scan.separation) {
            return "Separacao: "+scan.buttons+" botoes acessiveis; teclado " +
                    (scan.labeledKeyboard!=null?"rotulado":(scan.relativeKeyboard!=null?"identificado por layout":"nao identificado")) +
                    "; quantidade "+(scan.quantity?"visivel":"ausente")+".";
        }
        return "MLOG "+(scan.workScreen?"operacional":"sem contexto seguro")+": "+
                scan.buttons+" botoes; teclado "+
                (scan.labeledKeyboard!=null?"rotulado":"nao identificado")+".";
    }

    static final class UiScan {
        boolean separation,quantity,workScreen,ambiguousKeyboard,loginScreen;
        int buttons;
        AccessibilityNodeInfo labeledKeyboard,relativeKeyboard;
        Rect fabricAnchor;
        final List<AccessibilityNodeInfo> controls=new ArrayList<>();
        final List<Rect> controlRects=new ArrayList<>();
    }

    private UiScan inspect(AccessibilityNodeInfo root) {
        UiScan result=new UiScan();
        int[] limit={0};
        traverse(root,result,limit,0);
        if(result.loginScreen) { result.labeledKeyboard=null; result.relativeKeyboard=null; return result; }
        if(result.separation && result.labeledKeyboard==null && result.fabricAnchor!=null)
            result.relativeKeyboard=findSecondButtonBelowCode(result);
        return result;
    }

    private static String clean(CharSequence value) {
        return value==null?"":value.toString().toLowerCase(Locale.ROOT);
    }
    private static boolean has(String s,String... pieces) {
        for(String p:pieces) if(s.contains(p))return true;
        return false;
    }
    private static String tag(AccessibilityNodeInfo node) {
        return clean(node.getContentDescription())+" "+clean(node.getText())+" "+
                clean(node.getHintText())+" "+clean(node.getViewIdResourceName());
    }
    private boolean badButtonLabel(String tag) {
        return has(tag,"quantidade","volume","pular item","encerrar item","trocar lote",
                "camera","câmera","excluir","cancelar","fechar","pesquisar");
    }
    private void traverse(AccessibilityNodeInfo node, UiScan out,int[] visited,int depth) {
        if(node==null || depth>45 || visited[0]++>430) return;
        if(!node.isVisibleToUser())return;
        String id=tag(node);
        if(has(id,"separação","separacao"))out.separation=true;
        if(has(id,"nota fiscal","notas fiscais","entrada de notas","recebimento",
                "conferencia de nota","conferência de nota","conferir notas"))out.workScreen=true;
        if(has(id,"senha","login","autenticacao","autenticação"))out.loginScreen=true;
        if(has(id,"quantidade:"))out.quantity=true;
        if(out.fabricAnchor==null && has(id,"cod. fabric:","cód. fabric:","cod fabric:")) {
            Rect r=new Rect();node.getBoundsInScreen(r);
            int height=getResources().getDisplayMetrics().heightPixels;
            if(!r.isEmpty() && r.height()<height*.18f)out.fabricAnchor=r;
        }
        if(node.isClickable() && node.isEnabled()) {
            out.buttons++;
            Rect rect=new Rect();node.getBoundsInScreen(rect);
            if(!rect.isEmpty()) {out.controls.add(node);out.controlRects.add(rect);}
            if(!badButtonLabel(id) && has(id,"teclado","keyboard","digitar codigo",
                    "digitar código","entrada manual","ler codigo","ler código",
                    "inserir codigo","inserir código")) {
                if(out.labeledKeyboard==null && !out.ambiguousKeyboard)out.labeledKeyboard=node;
                else if(out.labeledKeyboard!=null && !sameBounds(out.labeledKeyboard,node)) {
                    out.labeledKeyboard=null;out.ambiguousKeyboard=true;
                }
            }
        }
        for(int j=0;j<node.getChildCount();j++) traverse(node.getChild(j),out,visited,depth+1);
    }
    private static boolean sameBounds(AccessibilityNodeInfo a,AccessibilityNodeInfo b) {
        Rect ra=new Rect(),rb=new Rect();a.getBoundsInScreen(ra);b.getBoundsInScreen(rb);
        return Math.abs(ra.centerX()-rb.centerX())<15 && Math.abs(ra.centerY()-rb.centerY())<15;
    }

    /**
     * Fallback OPCIONAL e restrito a separacao: encontra a segunda acao na
     * fileira situada abaixo de 'Cod. Fabric'. Nao usa pixel fixo nem gestos.
     * Exige 2 ou 3 controles distintos e alinhados, rejeita qualquer ambiguidade.
     */
    private AccessibilityNodeInfo findSecondButtonBelowCode(UiScan scan) {
        Rect anchor=scan.fabricAnchor;
        if(anchor==null || anchor.isEmpty())return null;
        int w=getResources().getDisplayMetrics().widthPixels;
        int h=getResources().getDisplayMetrics().heightPixels;
        float dp=getResources().getDisplayMetrics().density;
        class Candidate {
            AccessibilityNodeInfo node; Rect bounds;
            Candidate(AccessibilityNodeInfo n,Rect b){node=n;bounds=b;}
        }
        List<Candidate> candidates=new ArrayList<>();
        for(int i=0;i<scan.controls.size();i++) {
            Rect r=scan.controlRects.get(i);
            if(r.centerY()<anchor.bottom+8*dp || r.centerY()>h*.85f ||
                    r.width()<34*dp || r.width()>w*.38f ||
                    r.height()<32*dp || r.height()>130*dp)continue;
            String t=tag(scan.controls.get(i));
            if(badButtonLabel(t))continue;
            boolean duplicate=false;
            for(Candidate cand:candidates) {
                if(Math.abs(r.centerX()-cand.bounds.centerX())<18*dp &&
                        Math.abs(r.centerY()-cand.bounds.centerY())<18*dp){
                    duplicate=true;break;
                }
            }
            if(!duplicate)candidates.add(new Candidate(scan.controls.get(i),r));
        }
        // Normalmente camera+teclado ou camera+teclado+quantidade.
        // Um terceiro botao de quantidade pode estar rotulado e foi excluido acima;
        // nesse caso ficarao somente camera e teclado.
        List<Candidate> row=new ArrayList<>();
        int bestCount=0;
        for(Candidate c:candidates) {
            List<Candidate> cluster=new ArrayList<>();
            for(Candidate d:candidates)
                if(Math.abs(c.bounds.centerY()-d.bounds.centerY())<24*dp)cluster.add(d);
            if(cluster.size()>bestCount && cluster.size()<=3) {
                row=cluster; bestCount=cluster.size();
            }
        }
        if(row.size()!=2 && row.size()!=3)return null;
        row.sort(Comparator.comparingInt(x->x.bounds.centerX()));
        // Segurança: se 3 botoes, ultimo precisa ser Quantidade, mas esse foi
        // excluido na selecao. Rejeitamos a linha de tres sem rotulo confiavel.
        if(row.size()==3)return null;
        int delta=row.get(1).bounds.centerX()-row.get(0).bounds.centerX();
        if(delta<w*.12f || delta>w*.50f)return null;
        return row.get(1).node;
    }
}
