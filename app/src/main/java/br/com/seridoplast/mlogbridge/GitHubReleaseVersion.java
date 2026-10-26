package br.com.seridoplast.mlogbridge;

/** Identifica Releases por tag vMAJOR.MINOR.PATCH, igual ao versionName do APK.
 * Calcula um versionCode estavel, independente do numero da execucao CI.
 */
public final class GitHubReleaseVersion {
    private GitHubReleaseVersion() { }

    public static long versionCode(String tag) {
        if (tag == null || !tag.matches("v(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)")) return -1L;
        try {
            String[] fields = tag.substring(1).split("\\.");
            long major=Long.parseLong(fields[0]);
            long minor=Long.parseLong(fields[1]);
            long patch=Long.parseLong(fields[2]);
            // 99 limites explicitos para evitar colisao entre componentes.
            if (major > 99 || minor > 99 || patch > 99) return -1L;
            return 1_000_000L + major * 10_000L + minor * 100L + patch;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}
