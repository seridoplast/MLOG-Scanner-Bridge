package br.com.seridoplast.mlogbridge;

public final class BridgeRolloutPolicyTest {
    private static void check(boolean ok) {if(!ok)throw new AssertionError("rollout");}
    public static void main(String[] args) {
        check(BridgeRolloutPolicy.percentage(null)==0);
        check(BridgeRolloutPolicy.percentage("release notes") == 0);
        check(BridgeRolloutPolicy.percentage("rollout=10\nOutro texto") == 10);
        check(BridgeRolloutPolicy.percentage("rollout=100") == 100);
        check(BridgeRolloutPolicy.percentage("rollout=101") == 0);
        check(!BridgeRolloutPolicy.eligible("12345678",0));
        check(BridgeRolloutPolicy.eligible("12345678",100));
        int cohort=BridgeRolloutPolicy.cohort("123456789abcdef0");
        check(cohort>=0 && cohort<100);
        check(cohort==BridgeRolloutPolicy.cohort("123456789abcdef0"));
        check(!BridgeRolloutPolicy.eligible(null,10));
        System.out.println("OK - rollout seguro");
    }
}
