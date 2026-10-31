package br.com.seridoplast.mlogbridge;
public final class BridgeUpdateChannelTest {
    public static void main(String[] args) {
        if(BridgeUpdateChannel.isAutoChannel(false,true)) throw new AssertionError("prerelease nunca e automatica");
        if(BridgeUpdateChannel.mayAutoInstall(false,true,100)) throw new AssertionError("pre-release proibida ate com rollout 100");
        if(BridgeUpdateChannel.mayAutoInstall(true,false,100)) throw new AssertionError("draft proibido");
        if(BridgeUpdateChannel.mayAutoInstall(false,false,0)) throw new AssertionError("sem aprovacao rollout");
        if(!BridgeUpdateChannel.mayAutoInstall(false,false,100)) throw new AssertionError("estavel aprovada");
        System.out.println("BridgeUpdateChannelTest OK");
    }
}
