package br.com.seridoplast.mlogbridge;

public final class PmInstallOutputTest {
    public static void main(String[] args) {
        check("123".equals(PmInstallOutput.createdSession("Success: created install session [123]")));
        check("2147483647".equals(PmInstallOutput.createdSession("Success: created install session [2147483647]")));
        check(PmInstallOutput.createdSession("Failure [INSTALL_FAILED_USER_RESTRICTED]") == null);
        check(PmInstallOutput.createdSession("Success: created install session [0]") == null);
        check(PmInstallOutput.createdSession("Success: created install session [2147483648]") == null);
        check(PmInstallOutput.createdSession("Success: created install session [-1]") == null);
        check(PmInstallOutput.success("Success: streamed 2200 bytes\n"));
        check(!PmInstallOutput.success("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"));
        check(PmInstallOutput.brief("\n\r").equals("sem detalhes do instalador"));
        check(PmInstallOutput.brief("x".repeat(400)).length() == 280);
        System.out.println("PmInstallOutput: 10 verificacoes OK");
    }
    private static void check(boolean result) { if (!result) throw new AssertionError("Resposta pm invalida"); }
}
