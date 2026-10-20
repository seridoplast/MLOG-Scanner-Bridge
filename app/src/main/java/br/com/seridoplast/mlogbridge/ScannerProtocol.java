package br.com.seridoplast.mlogbridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import java.util.LinkedHashMap;
import java.util.Map;

/** Protocolo extraído por análise estática do CMX Scanner 1.3 do próprio usuário.
 *  Os comandos ainda devem ser validados no modelo físico de coletor.
 */
public final class ScannerProtocol {
    private static final String TAG = "MLOG-Bridge";
    private static final String ENGINE_PKG = "com.hikrobotics.pdaservice";
    private static final String ENGINE_RECEIVER = "com.pda.service.broadcast.ServiceControlReceiver";
    public static final String ACTION_CMX = "com.cmx.scanner.DATA";
    public static final String ACTION_HIK = "com.hikvision.ACTION_INPUT";
    public static final String ACTION_GENERIC = "com.service.scanner.data";
    public static final String[] ACTIONS = { ACTION_CMX, ACTION_HIK, ACTION_GENERIC };

    // Valores comprovados na lista CmxScanner.SIMBOLOGIAS do APK.
    public static final LinkedHashMap<String,Integer> FORMATS = new LinkedHashMap<>();
    static {
        FORMATS.put("EAN / UPC", 8);
        FORMATS.put("Code 128", 2);
        FORMATS.put("Code 39", 1);
        FORMATS.put("ITF-25", 16);
        FORMATS.put("Code 93", 32);
        FORMATS.put("Codabar", 4);
        FORMATS.put("QR Code", 8192);
        FORMATS.put("Data Matrix", 16384);
        FORMATS.put("PDF417", 524288);
    }

    private ScannerProtocol() {}

    private static void command(Context context, String action, String extraKey, Object value) {
        Intent i = new Intent(action);
        i.setComponent(new ComponentName(ENGINE_PKG, ENGINE_RECEIVER));
        if (extraKey != null) {
            if (value instanceof Boolean) i.putExtra(extraKey, (Boolean) value);
            else if (value instanceof Integer) i.putExtra(extraKey, (Integer) value);
            else if (value instanceof String) i.putExtra(extraKey, (String) value);
        }
        try { context.sendBroadcast(i); }
        catch (Exception e) { Log.w(TAG, "Comando recusado " + action, e); }
    }

    /** Esse modo habilita entrega por broadcast: evita depender de Keyboard Wedge.
     *  O hardware pode continuar em modo misto se o firmware ignorar comandos.
     */
    public static void configureBroadcast(Context c) {
        command(c,"com.service.scanner.start", null, null);
        command(c,"com.service.scanner.config.broad.action","broad_action",ACTION_CMX);
        command(c,"com.service.scanner.config.broad.code","broad_code","CmxCode");
        command(c,"com.service.scanner.config.broad.code.type","broad_code_type","CmxCodeType");
        command(c,"com.service.scanner.focus.spot.output","focusSpotOutput",false);
        command(c,"com.service.scanner.endsymbol","endSymbol","NOTHING");
        command(c,"com.service.scanner.broadcast.output","broadcastOutput",true);
    }

    public static void configureFormats(Context c, int mask) {
        command(c,"com.service.scanner.codetype","scanCodeTypes",mask);
    }

    public static void trigger(Context c) {
        command(c,"com.service.scanner.start.scanning",null,null);
    }
    public static void stop(Context c) {
        command(c,"com.service.scanner.stop.scanning",null,null);
    }

    public static String barcode(Intent i) {
        if (i == null) return null;
        final String[] keys = {
            "CmxCode","ScanCode","code","scanCode","scannerdata","barcode",
            "scan_result","scanResult","data","EXTRA_DATA","barcode_data",
            "com.service.scanner.data","ScanResult","BARCODE","decode_data"
        };
        for (String k : keys) {
            try {
                Object v = i.getExtras() == null ? null : i.getExtras().get(k);
                if (v instanceof String && !((String)v).trim().isEmpty()) return ((String)v).trim();
                if (v instanceof byte[] && ((byte[])v).length > 0)
                    return new String((byte[])v, java.nio.charset.StandardCharsets.UTF_8).trim();
            } catch (Exception ignore) { }
        }
        return null;
    }

    public static String type(Intent i) {
        if (i == null) return "";
        for (String k : new String[]{"CmxCodeType","ScanCodeType","CmxCodeTypeName","codeType","scannerType"}) {
            try { String v=i.getStringExtra(k); if(v!=null) return v; }
            catch(Exception ignore){}
        }
        return "";
    }
}
