package br.com.seridoplast.mlogbridge;

public final class GitHubReleaseVersionTest {
    public static void main(String[] args) {
        check(GitHubReleaseVersion.versionCode("bridge-build-1") == 100001);
        check(GitHubReleaseVersion.versionCode("bridge-build-345") == 100345);
        check(GitHubReleaseVersion.versionCode("bridge-build-0") == -1);
        check(GitHubReleaseVersion.versionCode("bridge-build-0002") == -1);
        check(GitHubReleaseVersion.versionCode("v3.2.2") == -1);
        check(GitHubReleaseVersion.versionCode("bridge-build-2147483647") == -1);
        check(GitHubReleaseVersion.versionCode(null) == -1);
        System.out.println("GitHubReleaseVersion: 7 verificacoes OK");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Versao invalida"); }
}
