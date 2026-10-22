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

    /** Mantem a normalizacao legada da leitura operacional. */
    public static String barcode(Intent i) {
        String raw=barcodeRaw(i);
        return raw==null || raw.trim().isEmpty() ? null : raw.trim();
    }

    /** Para diagnostico e tratador opt-in: mantem sufixos CR, LF, Tab etc. */
    public static String barcodeRaw(Intent i) {
        if (i == null) return null;
        final String[] keys = {
            "CmxCode","ScanCode","code","scanCode","scannerdata","barcode",
            "scan_result","scanResult","data","EXTRA_DATA","barcode_data",
            "com.service.scanner.data","ScanResult","BARCODE","decode_data"
        };
        for (String k : keys) {
            try {
                Object v = i.getExtras() == null ? null : i.getExtras().get(k);
                if (v instanceof String && !((String)v).isEmpty()) return (String)v;
                if (v instanceof byte[] && ((byte[])v).length > 0)
                    return new String((byte[])v, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignore) { }
        }
        return null;
    }

    private static boolean isFormatKey(String key){
        if(key==null)return false;
        String k=key.toLowerCase(java.util.Locale.ROOT);
        return k.contains("symbology") || k.contains("codetype") ||
            k.contains("barcodeformat") || k.contains("barcodetype") ||
            k.contains("decodetype") || k.contains("decode_type") ||
            k.contains("labeltype") || k.contains("code_format") ||
            k.equals("scantype") || k.equals("scan_type") ||
            k.equals("scanformat") || k.equals("scan_format") ||
            k.equals("scanner_type") ||
            k.equals("type");
    }
    public static String type(Intent i){
        if(i==null || i.getExtras()==null)return BarcodeFormatLabel.UNKNOWN;
        // Ordem prioriza o nome legivel frente a possiveis IDs numericos.
        String[] keys={"CmxCodeTypeName","symbologyName","barcodeFormatName",
            "codeTypeName","ScanCodeTypeName","CmxCodeType","ScanCodeType",
            "codeType","scannerType","barcodeType","barcode_type",
            "symbology","labelType","decode_type","barcodeFormat"};
        for(String k:keys){
            try{
                Object v=i.getExtras().get(k);
                if(v!=null && !v.toString().trim().isEmpty())return BarcodeFormatLabel.normalize(v.toString());
            }catch(RuntimeException ignored){}
        }
        for(String k:i.getExtras().keySet()){
            if(!isFormatKey(k))continue;
            try{
                Object v=i.getExtras().get(k);
                if(v!=null && !v.toString().trim().isEmpty())return BarcodeFormatLabel.normalize(v.toString());
            }catch(RuntimeException ignored){}
        }
        return BarcodeFormatLabel.UNKNOWN;
    }
    /** Lista apenas nomes de campos: nunca revela o codigo escaneado no log. */
    public static String receivedFields(Intent i){
        if(i==null || i.getExtras()==null)return "Nenhum campo de broadcast";
        java.util.List<String> keys=new java.util.ArrayList<>(i.getExtras().keySet());
        java.util.Collections.sort(keys);
        if(keys.size()>20)keys=keys.subList(0,20);
        return keys.isEmpty()?"Nenhum campo de broadcast":android.text.TextUtils.join(", ",keys);
    }
}
