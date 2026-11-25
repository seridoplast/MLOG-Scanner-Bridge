package br.com.seridoplast.mlogbridge;
import java.util.Set;
public final class AdbRecoveryPolicyTest {
    private static void expect(boolean result){if(!result)throw new AssertionError("policy");}
    public static void main(String[] args){
        Set<String> allowed=Set.of("12:34:56:ab:cd:ef");
        expect(AdbRecoveryPolicy.mayEnable(true,true,true,"12:34:56:AB:CD:EF",allowed));
        expect(!AdbRecoveryPolicy.mayEnable(false,true,true,"12:34:56:ab:cd:ef",allowed));
        expect(!AdbRecoveryPolicy.mayEnable(true,false,true,"12:34:56:ab:cd:ef",allowed));
        expect(!AdbRecoveryPolicy.mayEnable(true,true,false,"12:34:56:ab:cd:ef",allowed));
        expect(!AdbRecoveryPolicy.mayEnable(true,true,true,"12:34:56:ab:cd:00",allowed));
        expect(!AdbRecoveryPolicy.mayEnable(true,true,true,"02:00:00:00:00:00",Set.of("02:00:00:00:00:00")));
        expect(!AdbRecoveryPolicy.mayEnable(true,true,true,null,allowed));
        expect(!AdbRecoveryPolicy.mayEnable(true,true,true,"12:34:56:ab:cd:ef",Set.of()));
        System.out.println("AdbRecoveryPolicyTest OK");
    }
}
