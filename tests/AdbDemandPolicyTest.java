package br.com.seridoplast.mlogbridge;

public final class AdbDemandPolicyTest {
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    public static void main(String[] args){
        check(!AdbDemandPolicy.needsTransport(true,false,false),"healthy agent: no ADB polling or roaming recovery");
        check(AdbDemandPolicy.needsTransport(false,false,false),"agent dead: bootstrap");
        check(AdbDemandPolicy.needsTransport(true,true,false),"explicit consent provisioning");
        check(AdbDemandPolicy.needsTransport(true,false,true),"APK demand");
        check(AdbDemandPolicy.earlyLoss(1000,16000),"early OEM termination");
        check(!AdbDemandPolicy.earlyLoss(1000,16001),"unrelated later death");
        check(!AdbDemandPolicy.earlyLoss(-1,1000),"not parked");
        check(!AdbDemandPolicy.earlyLoss(1000,999),"clock regression");
        System.out.println("ADB demand policy: OK");
    }
}
