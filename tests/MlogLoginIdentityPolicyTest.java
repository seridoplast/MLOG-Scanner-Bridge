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
    check("ARTHURJ".equals(MlogLoginIdentityPolicy.footerUsername("ARTHURJ",26,667,96,682,373,730)));
    check("ARTHURJ".equals(MlogLoginIdentityPolicy.footerUsername("Usuário: ARTHURJ",26,667,96,682,373,685)));
    check(MlogLoginIdentityPolicy.footerUsername("OFFLINE",286,667,355,682,373,730)==null);
    check(MlogLoginIdentityPolicy.footerUsername("ARTHURJ",26,100,96,120,373,730)==null);
    check(MlogLoginIdentityPolicy.footerUsername("123456",26,667,96,682,373,730)==null);
    check(MlogLoginIdentityPolicy.footerUsername("SERIDOPLAST-CRUZETA",116,667,260,682,373,730)==null);
    System.out.println("LoginIdentityPolicy OK");
  }
  private static void check(boolean ok){if(!ok)throw new AssertionError();}
}
