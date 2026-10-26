package br.com.seridoplast.mlogbridge;

public final class GitHubReleaseVersionTest {
    public static void main(String[] args) {
        check(GitHubReleaseVersion.versionCode("v3.2.3") == 1030203);
        check(GitHubReleaseVersion.versionCode("v3.2.2") == 1030202);
        check(GitHubReleaseVersion.versionCode("v3.3.0") == 1030300);
        check(GitHubReleaseVersion.versionCode("v4.0.0") == 1040000);
        check(GitHubReleaseVersion.versionCode("v3.2.3") > 100000);
        check(GitHubReleaseVersion.versionCode("bridge-build-45") == -1);
        check(GitHubReleaseVersion.versionCode("v3.2") == -1);
        check(GitHubReleaseVersion.versionCode("v3.2.003") == -1);
        check(GitHubReleaseVersion.versionCode("v3.100.1") == -1);
        check(GitHubReleaseVersion.versionCode("v3.2.3-beta") == -1);
        check(GitHubReleaseVersion.versionCode(null) == -1);
        System.out.println("GitHubReleaseVersion: 11 verificacoes OK");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Versao invalida"); }
}
