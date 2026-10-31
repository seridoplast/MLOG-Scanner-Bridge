package br.com.seridoplast.mlogbridge;

public final class BridgeReleaseManualPolicyTest {
  public static void main(String[] args) {
    if(BridgeUpdateChannel.isAutoChannel(false,true)) throw new AssertionError("pre-release automatica");
    if(!BridgeUpdateChannel.isAutoChannel(false,false)) throw new AssertionError("estavel bloqueada");
    if(GitHubReleaseVersion.versionCode("v3.6.0")!=1030600L) throw new AssertionError("versionCode 3.6.0");
    if(GitHubReleaseVersion.versionCode("v3.5.9")>=GitHubReleaseVersion.versionCode("v3.6.0"))
      throw new AssertionError("versionCode nao cresce");
    if(DebugUpdateAccess.allowed())throw new AssertionError("senha nao deve comecar autorizada");
    if(DebugUpdateAccess.unlock("senha incorreta"))throw new AssertionError("aceitou senha errada");
    DebugUpdateAccess.lock();
    System.out.println("BridgeReleaseManualPolicyTest OK");
  }
}
