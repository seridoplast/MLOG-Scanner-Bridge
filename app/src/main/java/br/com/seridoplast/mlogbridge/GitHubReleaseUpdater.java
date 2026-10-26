package br.com.seridoplast.mlogbridge;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/** Manual-only updater for public GitHub Releases. Does not install or grant permissions silently. */
public final class GitHubReleaseUpdater {
    private static final String REPOSITORY = "seridoplast/MLOG-Scanner-Bridge";
    private static final String API = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    private static final String RELEASE_ASSET_PREFIX = "https://github.com/" + REPOSITORY + "/releases/download/";
    private static final String LEGACY_APK_NAME = "app-release.apk";
    // Matches the verified corporate release certificate in .github/workflows/gerar-apk.yml.
    private static final String TRUSTED_CERT = "8151376de33abdee4c98cda4c88c102a8b9165a0db7edc18b1ad0403a7222fae";
    private static final long MAX_BYTES = 120L * 1024L * 1024L;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private GitHubReleaseUpdater() { }

    public interface Callback {
        void status(String text);
        void upToDate(String text);
        void ready(File apk, String version);
        void error(String message);
    }

    private static void postStatus(Callback cb, String text) { UI.post(() -> cb.status(text)); }

    public static void checkAndDownload(Activity activity, Callback callback) {
        final android.content.Context context = activity.getApplicationContext();
        new Thread(() -> {
            File temp = null;
            try {
                postStatus(callback, "Consultando o GitHub Releases...");
                JSONObject release = new JSONObject(new String(fetchJson(API), "UTF-8"));
                String tag = release.optString("tag_name", "");
                long availableCode = GitHubReleaseVersion.versionCode(tag);
                if (availableCode < 0) throw new IOException("Release sem identificação de build válida: " + tag);
                PackageManager pm = context.getPackageManager();
                PackageInfo installed = pm.getPackageInfo(context.getPackageName(), 0);
                long currentCode = code(installed);
                if (availableCode <= currentCode) {
                    UI.post(() -> callback.upToDate("Você já está atualizado. Build instalado: " + currentCode + "."));
                    return;
                }
                JSONArray assets = release.optJSONArray("assets");
                JSONObject apkAsset = null;
                if (assets != null) for (int index = 0; index < assets.length(); index++) {
                    JSONObject asset = assets.optJSONObject(index);
                    if (asset != null && (LEGACY_APK_NAME.equals(asset.optString("name")) ||
                            ("MLOG-Scanner-Bridge-" + tag + ".apk").equals(asset.optString("name")))) {
                        apkAsset = asset; break;
                    }
                }
                if (apkAsset == null) throw new IOException("Release encontrada, mas sem APK da versão " + tag + ".");
                String apkName = apkAsset.optString("name", "");
                String downloadUrl = apkAsset.optString("browser_download_url", "");
                if (!downloadUrl.startsWith(RELEASE_ASSET_PREFIX) || !downloadUrl.endsWith("/" + apkName)) {
                    throw new IOException("Endereço do APK não corresponde ao repositório esperado.");
                }
                String digest = apkAsset.optString("digest", "");
                if (!digest.matches("sha256:[A-Fa-f0-9]{64}")) {
                    throw new IOException("Release sem hash SHA-256 válido. Não é seguro instalar.");
                }
                String expectedHash = digest.substring("sha256:".length()).toLowerCase(Locale.ROOT);
                postStatus(callback, "Nova build " + availableCode + ". Baixando APK assinado...");
                File dir = new File(context.getFilesDir(), "updates");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Não foi possível criar a pasta de atualizações.");
                temp = new File(dir, "github-download-temp.apk");
                String actualHash = downloadApk(downloadUrl, temp);
                if (!expectedHash.equals(actualHash)) throw new IOException("Hash do APK divergente; arquivo rejeitado.");
                verifyApk(pm, context.getPackageName(), temp, availableCode, tag.substring(1));
                File completed = new File(dir, "bridge-github.apk");
                if (completed.exists() && !completed.delete()) throw new IOException("Não foi possível substituir o APK de atualização anterior.");
                if (!temp.renameTo(completed)) throw new IOException("Não foi possível finalizar o download.");
                temp = null;
                File trusted = completed;
                UI.post(() -> callback.ready(trusted, tag));
            } catch (Exception error) {
                String message = error.getMessage();
                UI.post(() -> callback.error(message == null ? "Não foi possível consultar o GitHub." : message));
            } finally {
                if (temp != null) temp.delete();
            }
        }, "bridge-github-update").start();
    }

    private static long code(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static byte[] fetchJson(String url) throws Exception {
        HttpURLConnection conn = connect(url);
        try {
            if (conn.getResponseCode() != 200) {
                int result = conn.getResponseCode();
                if (result == 404) throw new IOException("GitHub Releases indisponível. O repositório pode ser privado ou ainda não ter uma versão publicada.");
                if (result == 403) throw new IOException("GitHub limitou a consulta. Tente novamente mais tarde.");
                throw new IOException("GitHub respondeu HTTP " + result + ".");
            }
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (output.size() + n > 512 * 1024) throw new IOException("Resposta do GitHub excedeu o limite.");
                    output.write(buffer, 0, n);
                }
                return output.toByteArray();
            }
        } finally { conn.disconnect(); }
    }

    private static String downloadApk(String url, File output) throws Exception {
        HttpURLConnection conn = connect(url);
        try {
            if (conn.getResponseCode() != 200) throw new IOException("Falha no download: HTTP " + conn.getResponseCode());
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            long size = 0L;
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(output)) {
                byte[] buffer = new byte[16384]; int n;
                while ((n = in.read(buffer)) != -1) {
                    size += n;
                    if (size > MAX_BYTES) throw new IOException("APK excedeu o tamanho permitido.");
                    hash.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                }
            }
            if (size < 50 * 1024) throw new IOException("Arquivo baixado não parece ser um APK válido.");
            return hex(hash.digest());
        } finally { conn.disconnect(); }
    }

    private static HttpURLConnection connect(String url) throws Exception {
        URL next = new URL(url);
        for (int attempt = 0; attempt < 6; attempt++) {
            if (!"https".equalsIgnoreCase(next.getProtocol()) || !allowedHost(next.getHost())) {
                throw new IOException("Redirecionamento de download não permitido.");
            }
            HttpURLConnection conn = (HttpURLConnection) next.openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Seridoplast-MLOG-Bridge-Updater");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            int status = conn.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = conn.getHeaderField("Location");
                if (location == null) { conn.disconnect(); throw new IOException("Redirecionamento sem endereço."); }
                URL redirected = new URL(next, location);
                conn.disconnect();
                next = redirected;
                continue;
            }
            return conn;
        }
        throw new IOException("Muitos redirecionamentos no download.");
    }

    private static boolean allowedHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("github.com") || h.equals("api.github.com") || h.equals("objects.githubusercontent.com")
                || h.endsWith(".githubusercontent.com");
    }

    private static void verifyApk(PackageManager pm, String packageName, File file,
                                  long expectedVersion, String expectedVersionName) throws Exception {
        final int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (info == null) throw new IOException("APK não reconhecido pelo Android.");
        if (!packageName.equals(info.packageName)) throw new IOException("O APK não pertence ao Scanner Bridge.");
        if (code(info) != expectedVersion) throw new IOException("O versionCode do APK não corresponde à Release.");
        if (!expectedVersionName.equals(info.versionName))
            throw new IOException("O versionName do APK não corresponde à Release.");
        Signature[] sigs;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) throw new IOException("APK sem assinatura verificável.");
            sigs = info.signingInfo.getApkContentsSigners();
        } else sigs = info.signatures;
        if (sigs == null || sigs.length != 1) throw new IOException("Assinatura inesperada no APK.");
        String cert = hex(MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray()));
        if (!TRUSTED_CERT.equalsIgnoreCase(cert)) throw new IOException("Assinatura não corresponde à chave corporativa do Bridge.");
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
}
