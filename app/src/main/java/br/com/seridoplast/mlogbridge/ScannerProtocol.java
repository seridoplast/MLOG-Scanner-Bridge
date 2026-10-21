package br.com.seridoplast.mlogbridge;

import android.content.Intent;

/** Recepcao passiva dos broadcasts do scanner CMX/Hikrobotics.
 *  NUNCA envia comandos de configuracao para o leitor fisico.
 */
public final class ScannerProtocol {
    // Escuta passiva. Nenhum comando de configuracao, trigger ou parada.
    // O CMX Scanner original continua sendo a unica fonte de configuracoes.
    public static final String ACTION_CMX = "com.cmx.scanner.DATA";
    public static final String ACTION_HIK = "com.hikvision.ACTION_INPUT";
    public static final String ACTION_GENERIC = "com.service.scanner.data";
    public static final String[] ACTIONS = {ACTION_CMX,ACTION_HIK,ACTION_GENERIC};

    private ScannerProtocol() {}

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
