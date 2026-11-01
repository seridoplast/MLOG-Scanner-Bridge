package br.com.seridoplast.mlogbridge;

import java.util.Locale;

/** Pure safety rules, independently testable without Android. */
final class ShizukuUpdatePolicy {
    private static final String PREFIX="https://github.com/RikkaApps/Shizuku/releases/download/";
    private ShizukuUpdatePolicy(){}
    static boolean isStableTag(String tag){
        return tag!=null && tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+");
    }
    static boolean officialApk(String tag,String name,String url){
        if(!isStableTag(tag) || name==null || url==null)return false;
        String lower=name.toLowerCase(Locale.ROOT);
        return lower.startsWith("shizuku-") && lower.endsWith("-release.apk") &&
            !lower.contains("debug") && !lower.contains("unsigned") &&
            url.startsWith(PREFIX+tag+"/") && url.endsWith("/"+name);
    }
    /** No downgrade, including a higher installed major/minor version. */
    static boolean newerThan(String tag,String installedName){
        if(!isStableTag(tag) || installedName==null)return false;
        String[] parts=installedName.split("\\.");
        if(parts.length<3)return true; // Let signed APK versionCode decide unknown formats.
        int[] candidate=tuple(tag.substring(1).split("\\."));
        int[] installed=tuple(parts);
        if(candidate==null || installed==null)return true;
        for(int i=0;i<3;i++){
            if(candidate[i]>installed[i])return true;
            if(candidate[i]<installed[i])return false;
        }
        return false;
    }
    private static int[] tuple(String[] values){
        try{
            return new int[]{Integer.parseInt(values[0]),Integer.parseInt(values[1]),Integer.parseInt(values[2])};
        }catch(NumberFormatException ex){return null;}
    }
}
