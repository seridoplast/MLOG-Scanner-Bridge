package br.com.seridoplast.mlogbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Install only when screen off or an unrelated foreground activity is visible and scans are idle. */
public final class BridgeIdleInstallWorker extends Worker {
    public BridgeIdleInstallWorker(@NonNull Context context,@NonNull WorkerParameters params) {super(context,params);}
    // Old automatic GitHub installations are disabled; fleet jobs use the idle guard below.
    @NonNull @Override public Result doWork(){return Result.success();}
    /** Conservative idle guard for corporate updates. */
    static boolean isCollectorIdle(Context ctx){
        SharedPreferences sp=ctx.getSharedPreferences(ScanCoordinator.PREFS,Context.MODE_PRIVATE);
        long lastScan=sp.getLong("bridge_last_adb_scan_wall",0L);
        long now=System.currentTimeMillis();
        if(lastScan>0 && (now<lastScan || now-lastScan<2*60*1000L))return false;
        PowerManager pm=(PowerManager)ctx.getSystemService(Context.POWER_SERVICE);
        boolean interactive=pm!=null && pm.isInteractive();
        if(interactive){
            MlogFocusAccessibilityService svc=MlogFocusAccessibilityService.current();
            if(svc==null)return false;
            AccessibilityNodeInfo root=svc.getRootInActiveWindow();
            if(root==null || root.getPackageName()==null ||
                ScanCoordinator.TARGET.contentEquals(root.getPackageName()))return false;
            String foreground=root.getPackageName().toString();
            if(foreground.equals("com.android.systemui") || foreground.contains("inputmethod"))return false;
        }
        return true;
    }
}
