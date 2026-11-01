package br.com.seridoplast.mlogbridge;
public final class ShizukuUpdatePolicyTest {
    private static void require(boolean ok){if(!ok)throw new AssertionError("Regra da atualização Shizuku falhou.");}
    public static void main(String[] args){
        String url="https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk";
        String file="shizuku-v13.6.0.r1086.2650830c-release.apk";
        require(ShizukuUpdatePolicy.isStableTag("v13.6.0"));
        require(!ShizukuUpdatePolicy.isStableTag("v13.6.0-rc1"));
        require(ShizukuUpdatePolicy.officialApk("v13.6.0",file,url));
        require(!ShizukuUpdatePolicy.officialApk("v13.5.4",file,url));
        require(!ShizukuUpdatePolicy.officialApk("v13.6.0",file,"https://evil.example/"+file));
        require(!ShizukuUpdatePolicy.officialApk("v13.6.0","shizuku-debug.apk",url));
        require(!ShizukuUpdatePolicy.newerThan("v13.6.0","13.6.0.r1086"));
        require(!ShizukuUpdatePolicy.newerThan("v13.6.0","13.7.0"));
        require(ShizukuUpdatePolicy.newerThan("v13.6.0","13.5.4.r100"));
        System.out.println("ShizukuUpdatePolicyTest OK");
    }
}
