package br.com.seridoplast.mlogbridge;
/** Never put real operator passwords in repo or public CI logs. */
public final class AutoBipsAccessTest {
    public static void main(String[] args) {
        if(AutoBipsAccess.valid("wrong-password")) throw new AssertionError("unexpected password accepted");
        if(AutoBipsAccess.valid("")) throw new AssertionError("empty password accepted");
        if(AutoBipsAccess.valid(null)) throw new AssertionError("null password accepted");
        // Optional positive test from an out-of-repository environment secret.
        String secret=System.getenv("BRIDGE_BIPS_TEST_PASSWORD");
        if(secret!=null && !secret.isEmpty() && !AutoBipsAccess.valid(secret))
            throw new AssertionError("configured password rejected");
        System.out.println("AutoBipsAccessTest OK");
    }
}
