package br.com.seridoplast.mlogbridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local operator gate for MANUAL prerelease installation. Never stores the password. */
final class DebugUpdateAccess {
    private static final int ITERATIONS = 150000;
    private static final byte[] SALT = hex("37eeaa1831cf937e424b7b19b8d1366ac98806b9");
    private static final byte[] VERIFIER = hex("5153be7600b7c29a1281fd375f4425019b7e6f0c9198f74607b9e1877c41e38d");
    private static final long VALID_NS = 120L * 1_000_000_000L;
    private static volatile long authorizationExpiry = 0L;
    private DebugUpdateAccess() { }

    static boolean unlock(String candidate) {
        authorizationExpiry = 0L;
        if (!verify(candidate, SALT, VERIFIER, ITERATIONS)) return false;
        authorizationExpiry = System.nanoTime() + VALID_NS;
        return true;
    }

    static boolean allowed() {
        long expires = authorizationExpiry;
        return expires != 0L && System.nanoTime() - expires < 0;
    }

    static void lock() { authorizationExpiry = 0L; }

    static boolean verify(String candidate, byte[] salt, byte[] verifier, int iterations) {
        if (candidate == null || candidate.isEmpty() || salt == null || verifier == null || iterations < 1) return false;
        PBEKeySpec spec = new PBEKeySpec(candidate.toCharArray(), salt, iterations, verifier.length * 8);
        try {
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(actual, verifier);
        } catch (Exception ex) { return false; }
        finally { spec.clearPassword(); }
    }

    private static byte[] hex(String str) {
        byte[] result = new byte[str.length()/2];
        for (int i=0;i<result.length;i++) result[i] = (byte)Integer.parseInt(str.substring(i*2, i*2+2),16);
        return result;
    }
}
