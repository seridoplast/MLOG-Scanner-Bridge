package br.com.seridoplast.mlogbridge;

public final class ConfirmationReleasePolicyTest {
  static void check(boolean x,String label){if(!x)throw new AssertionError(label);}
  public static void main(String[] args){
    check(!ConfirmationReleasePolicy.shouldRelease(2500,false,true,false,true),
          "nao liberar por tempo se o mesmo codigo continua no campo");
    check(!ConfirmationReleasePolicy.shouldRelease(150,false,false,true,true),
          "outro campo editavel nao comprova confirmacao");
    check(!ConfirmationReleasePolicy.shouldRelease(15,false,false,false,true),
          "nao liberar durante a transicao inicial");
    check(ConfirmationReleasePolicy.shouldRelease(115,false,true,false,false),
          "campo limpo ou substituido apos confirmacao");
    check(ConfirmationReleasePolicy.shouldRelease(90,false,false,false,true),
          "campo da leitura desapareceu");
    check(ConfirmationReleasePolicy.shouldRelease(100,true,true,true,true),
          "a tela do pedido mudou");
    System.out.println("ConfirmationReleasePolicyTest OK");
  }
}
