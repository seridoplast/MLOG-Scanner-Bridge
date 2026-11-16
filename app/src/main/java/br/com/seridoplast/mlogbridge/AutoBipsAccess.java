package br.com.seridoplast.mlogbridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Convenience gate only, not a server-side security control. */
final class AutoBipsAccess {
    private static final String SHA256="e10ac3c0e5939f0bf585883131633db5f56004b8711324ac54fabaac9c949c0b";
    private AutoBipsAccess(){}
    static boolean valid(String candidate){
        if(candidate==null)return false;
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(
                ("MLOG-AUTO-BIPS-v344:"+candidate).getBytes(StandardCharsets.UTF_8));
            byte[] stored=hex(SHA256);
            return MessageDigest.isEqual(digest,stored);
        }catch(NoSuchAlgorithmException ex){return false;}
    }
    private static byte[] hex(String value){
        byte[] out=new byte[value.length()/2];
        for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);
        return out;
    }
}
