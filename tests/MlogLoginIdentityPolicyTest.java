package br.com.seridoplast.mlogbridge;
public final class MlogLoginIdentityPolicyTest {
  public static void main(String[] args) {
    check(MlogLoginIdentityPolicy.userHint("Usuário"));
    check(MlogLoginIdentityPolicy.userHint("nome de LOGIN"));
    check(MlogLoginIdentityPolicy.passwordHint("Senha de acesso"));
    check("JOAO.123".equals(MlogLoginIdentityPolicy.username(" JOAO.123 ")));
    check(MlogLoginIdentityPolicy.username("1234567890123456789012345678901234567890123456789012345678900")==null);
    check(MlogLoginIdentityPolicy.username("joao silva")==null);
    check(MlogLoginIdentityPolicy.authenticatedScreen("separacao"));
    check(!MlogLoginIdentityPolicy.authenticatedScreen("desconhecida"));
    System.out.println("LoginIdentityPolicy OK");
  }
  private static void check(boolean ok){if(!ok)throw new AssertionError();}
}
