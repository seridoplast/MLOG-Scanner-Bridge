package br.com.seridoplast.mlogbridge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the platform package manager's output without trusting arbitrary shell text. */
final class PmInstallOutput {
    private static final Pattern CREATED = Pattern.compile("created install session \\[(\\d{1,10})\\]");
    private PmInstallOutput() { }

    static String createdSession(String output) {
        Matcher m = CREATED.matcher(output == null ? "" : output);
        if (!m.find() || !output.contains("Success")) return null;
        try {
            long id = Long.parseLong(m.group(1));
            return id > 0 && id <= Integer.MAX_VALUE ? Long.toString(id) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static boolean success(String output) {
        return output != null && output.trim().startsWith("Success");
    }

    static String brief(String output) {
        if (output == null) return "sem detalhes do instalador";
        String cleaned = output.replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.length() > 280 ? cleaned.substring(0, 280) :
            (cleaned.isEmpty() ? "sem detalhes do instalador" : cleaned);
    }
}
