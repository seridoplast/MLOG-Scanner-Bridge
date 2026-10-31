package br.com.seridoplast.mlogbridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Stable 0..99 device cohort, no identifier is transmitted. */
public final class BridgeRolloutPolicy {
    private BridgeRolloutPolicy() { }
    public static int percentage(String releaseBody) {
        if (releaseBody == null) return 0;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?m)^rollout=(100|[1-9]?[0-9])\\s*$").matcher(releaseBody);
        if (!m.find()) return 0; // Unapproved releases never auto-install.
        return Integer.parseInt(m.group(1));
    }
    public static int cohort(String androidId) {
        if(androidId==null || androidId.length()<8)return 100;
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(("bridge-rollout-v1:"+androidId)
                .getBytes(StandardCharsets.UTF_8));
            long value=((long)(hash[0]&255)<<24)|((long)(hash[1]&255)<<16)
                |((long)(hash[2]&255)<<8)|(hash[3]&255);
            return (int)(value%100);
        } catch(Exception ex) {return 100;}
    }
    public static boolean eligible(String androidId,int rolloutPercent) {
        return rolloutPercent>=100 || (rolloutPercent>0 && cohort(androidId)<rolloutPercent);
    }
}
