package br.com.seridoplast.mlogbridge;

/** Maps the CI release tag to the versionCode set by app/build.gradle. */
public final class GitHubReleaseVersion {
    private GitHubReleaseVersion() { }

    public static long versionCode(String tag) {
        if (tag == null || !tag.matches("bridge-build-[1-9][0-9]*")) return -1L;
        try {
            long run = Long.parseLong(tag.substring("bridge-build-".length()));
            if (run > Integer.MAX_VALUE - 100000L) return -1L;
            return 100000L + run;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}
