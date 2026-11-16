package br.com.seridoplast.mlogbridge;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
public final class DebugUpdateAccessTest {
    public static void main(String[] args) throws Exception {
        if(DebugUpdateAccess.allowed()) throw new AssertionError("default bloqueado");
        if(DebugUpdateAccess.unlock("senha-invalida")) throw new AssertionError("nao autorizar outro valor");
        if(DebugUpdateAccess.allowed()) throw new AssertionError("apos senha invalida, bloqueado");
        byte[] salt="salt-only-test".getBytes(StandardCharsets.UTF_8);
        PBEKeySpec spec=new PBEKeySpec("exemplo".toCharArray(),salt,1000,256);
        byte[] expected=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        if(!DebugUpdateAccess.verify("exemplo",salt,expected,1000))throw new AssertionError("senha de teste valida");
        if(DebugUpdateAccess.verify("invalida",salt,expected,1000))throw new AssertionError("senha de teste invalida");
        DebugUpdateAccess.lock();
        System.out.println("DebugUpdateAccessTest OK");
    }
}
