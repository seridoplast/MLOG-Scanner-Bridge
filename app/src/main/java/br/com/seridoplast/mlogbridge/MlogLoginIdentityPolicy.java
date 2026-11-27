package br.com.seridoplast.mlogbridge;

import java.util.Locale;
import java.util.regex.Pattern;
import java.text.Normalizer;

/** Identifica somente o usuario, nunca credenciais. Exige login e depois tela autenticada. */
final class MlogLoginIdentityPolicy {
    private static final Pattern USER = Pattern.compile("[\\p{L}0-9][\\p{L}0-9._@-]{1,59}");
    private MlogLoginIdentityPolicy() {}
    static String normalizeLabel(String label) {
        if(label==null)return "";
        return Normalizer.normalize(label,Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).trim();
    }
    static boolean userHint(String label) {
        String s=normalizeLabel(label);
        return s.contains("usuario") || s.contains("login") ||
            s.contains("username") || s.contains("matricula") || s.contains("e-mail") || s.contains("email");
    }
    static boolean passwordHint(String label) {
        String s=normalizeLabel(label);
        return s.contains("senha") || s.contains("password") || s.contains("pin") || s.contains("token");
    }
    static String username(String value) {
        if(value==null)return null;
        String s=value.trim();
        if(s.length()<2 || s.length()>60 || s.indexOf(' ')>=0 ||
            !USER.matcher(s).matches() || passwordHint(s))return null;
        return s;
    }
    // Footer of an authenticated MLOG screen: left-most label near the bottom.
    static String footerUsername(String text,int left,int top,int right,int bottom,int width,int height) {
        if(width<=0 || height<=0 || left<0 || top<0 || right<=left || bottom<=top ||
           left>width*.22 || right>width*.42 || (top+bottom)/2.0<height*.86 ||
           bottom>height || bottom-top>height*.10)return null;
        if(text==null)return null;
        String value=text.trim().replaceFirst("(?i)^(usu[aá]rio|operador)\\s*:\\s*", "");
        String user=username(value);
        if(user==null || !user.matches(".*\\p{L}.*"))return null;
        String label=normalizeLabel(user);
        if(label.equals("offline") || label.equals("online") || label.equals("mlog") ||
           label.equals("voltar") || label.equals("usuario") || label.equals("operador"))return null;
        return user;
    }
    static boolean authenticatedScreen(String screen) {
        return "atividades".equals(screen) || "pedidos".equals(screen) ||
            "separacao".equals(screen) || "notas".equals(screen) ||
            "consulta".equals(screen) || "conferencia_venda".equals(screen) ||
            "conferencia_produto".equals(screen) || "conferencia_inicio".equals(screen) ||
            "conferencia_entrada_produto".equals(screen);
    }
}
