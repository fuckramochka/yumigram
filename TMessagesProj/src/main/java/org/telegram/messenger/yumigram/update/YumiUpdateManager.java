package org.telegram.messenger.yumigram.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * High-performance, reliable Auto-Update Manager for Yumigram (Юмиграм).
 * Connects directly to GitHub Releases API (fuckramochka/yumigram),
 * checks for green builds / releases, downloads the optimized arm64-v8a APK,
 * and seamlessly invokes the Android Package Installer.
 */
public class YumiUpdateManager {

    private static final String TAG = "YumiUpdateManager";
    public static final String CURRENT_VERSION = "v" + BuildConfig.YUMI_VERSION;
    public static final String GITHUB_REPO = "fuckramochka/yumigram";
    public static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String PREFS_NAME = "yumi_update_manager";
    private static final String KEY_LAST_AUTO_CHECK = "last_auto_check";
    private static final String KEY_LAST_AUTO_CHECK_SUCCESS = "last_auto_check_success";
    private static final String KEY_LAST_NOTIFIED_TAG = "last_notified_tag";
    private static final long AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final long AUTO_RETRY_INTERVAL_MS = 15L * 60L * 1000L;
    private static volatile boolean isDownloading = false;
    private static volatile UpdateInfo cachedUpdate = null;
    private static volatile boolean cachedHasUpdate;
    private static boolean isChecking;
    private static final ArrayList<CheckCallback> pendingCheckCallbacks = new ArrayList<>();

    public static class UpdateInfo {
        public final String tagName;
        public final String name;
        public final String changelog;
        public final String apkUrl;
        public final long apkSize;
        public final String publishedAt;

        public UpdateInfo(String tagName, String name, String changelog, String apkUrl, long apkSize, String publishedAt) {
            this.tagName = tagName;
            this.name = name;
            this.changelog = changelog;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.publishedAt = publishedAt;
        }

        public boolean isNewerThan(String currentVersion) {
            if (TextUtils.isEmpty(tagName) || TextUtils.isEmpty(currentVersion)) {
                return false;
            }
            String cleanNew = tagName.replaceAll("[^0-9.]", "");
            String cleanCurr = currentVersion.replaceAll("[^0-9.]", "");
            String[] newParts = cleanNew.split("\\.");
            String[] currParts = cleanCurr.split("\\.");

            int max = Math.max(newParts.length, currParts.length);
            for (int i = 0; i < max; i++) {
                long n = parseVersionPart(newParts, i);
                long c = parseVersionPart(currParts, i);
                if (n > c) return true;
                if (n < c) return false;
            }
            return false;
        }

        private static long parseVersionPart(String[] parts, int index) {
            if (index >= parts.length || parts[index].isEmpty()) return 0;
            try {
                return Long.parseLong(parts[index]);
            } catch (NumberFormatException ignored) {
                return Long.MAX_VALUE;
            }
        }
    }

    @FunctionalInterface
    public interface CheckCallback {
        void onSuccess(UpdateInfo update, boolean hasUpdate);
        default void onError(String error) {}
    }

    public interface DownloadCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onSuccess(File apkFile);
        default void onError(String error) {}
    }

    public static UpdateInfo getCachedUpdate() {
        return cachedUpdate;
    }

    /** Returns true once per release tag so resuming the app cannot repeat the same bulletin. */
    public static boolean shouldNotifyUpdate(String tagName) {
        if (TextUtils.isEmpty(tagName) || ApplicationLoader.applicationContext == null) return false;
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (tagName.equals(preferences.getString(KEY_LAST_NOTIFIED_TAG, null))) return false;
        preferences.edit().putString(KEY_LAST_NOTIFIED_TAG, tagName).apply();
        return true;
    }

    public static boolean isDownloading() {
        return isDownloading;
    }

    /**
     * Checks for the latest release on GitHub in the background.
     */
    public static void checkUpdate(boolean manual, CheckCallback callback) {
        synchronized (pendingCheckCallbacks) {
            if (!manual && ApplicationLoader.applicationContext != null) {
                final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                final long now = System.currentTimeMillis();
                final long lastAttempt = preferences.getLong(KEY_LAST_AUTO_CHECK, 0);
                final long lastSuccess = preferences.getLong(KEY_LAST_AUTO_CHECK_SUCCESS, 0);
                if (now - lastAttempt < AUTO_RETRY_INTERVAL_MS || now - lastSuccess < AUTO_CHECK_INTERVAL_MS) {
                    if (cachedUpdate != null && callback != null) {
                        final UpdateInfo update = cachedUpdate;
                        final boolean hasUpdate = cachedHasUpdate;
                        mainHandler.post(() -> callback.onSuccess(update, hasUpdate));
                    }
                    return;
                }
            }
            if (ApplicationLoader.applicationContext != null) {
                ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putLong(KEY_LAST_AUTO_CHECK, System.currentTimeMillis()).apply();
            }
            if (callback != null) pendingCheckCallbacks.add(callback);
            if (isChecking) return;
            isChecking = true;
        }
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GITHUB_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "Yumigram-Android/" + CURRENT_VERSION);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    finishCheck(null, false, "HTTP " + code);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
                reader.close();

                JSONObject root = new JSONObject(jsonBuilder.toString());
                String tagName = root.optString("tag_name");
                String name = root.optString("name", tagName);
                String body = root.optString("body", "");
                String publishedAt = root.optString("published_at", "");

                String apkUrl = null;
                long apkSize = 0;
                String fallbackApkUrl = null;
                long fallbackApkSize = 0;

                JSONArray assets = root.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name");
                        if (assetName.endsWith(".apk")) {
                            final String candidateUrl = asset.optString("browser_download_url");
                            final long candidateSize = asset.optLong("size", 0);
                            if (assetName.equalsIgnoreCase("Yumigram-arm64-v8a.apk")) {
                                apkUrl = candidateUrl;
                                apkSize = candidateSize;
                                break;
                            }
                            if (fallbackApkUrl == null) {
                                fallbackApkUrl = candidateUrl;
                                fallbackApkSize = candidateSize;
                            }
                        }
                    }
                }
                if (apkUrl == null) {
                    apkUrl = fallbackApkUrl;
                    apkSize = fallbackApkSize;
                }

                if (TextUtils.isEmpty(apkUrl)) {
                    finishCheck(null, false, "No APK asset found in release");
                    return;
                }

                UpdateInfo info = new UpdateInfo(tagName, name, body, apkUrl, apkSize, publishedAt);
                cachedUpdate = info;
                boolean hasUpdate = info.isNewerThan(CURRENT_VERSION);
                cachedHasUpdate = hasUpdate;
                finishCheck(info, hasUpdate, null);

            } catch (Exception e) {
                FileLog.e(TAG, e);
                finishCheck(null, false, e.getLocalizedMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void finishCheck(UpdateInfo update, boolean hasUpdate, String error) {
        final ArrayList<CheckCallback> callbacks;
        synchronized (pendingCheckCallbacks) {
            isChecking = false;
            callbacks = new ArrayList<>(pendingCheckCallbacks);
            pendingCheckCallbacks.clear();
        }
        if (error == null && update != null && ApplicationLoader.applicationContext != null) {
            ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_AUTO_CHECK_SUCCESS, System.currentTimeMillis()).apply();
        }
        mainHandler.post(() -> {
            for (CheckCallback callback : callbacks) {
                if (error == null && update != null) callback.onSuccess(update, hasUpdate);
                else callback.onError(error == null ? "Update check failed" : error);
            }
        });
    }

    /**
     * Downloads the APK file to cache and notifies progress.
     */
    public static void downloadApk(UpdateInfo update, DownloadCallback callback) {
        if (isDownloading) {
            return;
        }
        isDownloading = true;

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                File updatesDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "yumi_updates");
                if (!updatesDir.exists()) {
                    if (!updatesDir.mkdirs() && !updatesDir.isDirectory()) {
                        throw new Exception("Could not create update cache directory");
                    }
                }

                final String safeTag = update.tagName.replaceAll("[^A-Za-z0-9._-]", "_");
                File destination = new File(updatesDir, "Yumigram-" + safeTag + ".apk");
                File tempFile = new File(updatesDir, "Yumigram-" + safeTag + ".apk.tmp");

                URL currentUrl = new URL(update.apkUrl);
                boolean connected = false;
                for (int redirect = 0; redirect < 5; redirect++) {
                    conn = (HttpURLConnection) currentUrl.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("User-Agent", "Yumigram-Android/" + CURRENT_VERSION);
                    conn.setConnectTimeout(25000);
                    conn.setReadTimeout(30000);

                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308 || code == 303) {
                        String redirectUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (TextUtils.isEmpty(redirectUrl)) {
                            throw new Exception("Update download redirect has no destination");
                        }
                        if (redirect == 4) {
                            throw new Exception("APK download exceeded the redirect limit");
                        }
                        currentUrl = new URL(redirectUrl);
                    } else {
                        if (code != HttpURLConnection.HTTP_OK) {
                            throw new Exception("APK download failed (HTTP " + code + ")");
                        }
                        connected = true;
                        break;
                    }
                }

                if (conn == null || !connected) {
                    throw new Exception("Unable to establish connection");
                }

                long totalBytes = conn.getContentLengthLong();
                if (totalBytes <= 0) {
                    totalBytes = update.apkSize;
                }

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {

                    byte[] buffer = new byte[65536];
                    long downloaded = 0;
                    int bytesRead;
                    int lastPercent = -1;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloaded += bytesRead;

                        if (totalBytes > 0) {
                            int percent = (int) ((downloaded * 100) / totalBytes);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                long d = downloaded;
                                long t = totalBytes;
                                mainHandler.post(() -> {
                                    if (callback != null) callback.onProgress(percent, d, t);
                                });
                            }
                        }
                    }
                    out.getFD().sync();
                    if (totalBytes > 0 && downloaded != totalBytes) {
                        throw new Exception("APK download was incomplete");
                    }
                }

                final String packageName = ApplicationLoader.getApplicationId();
                final PackageInfo downloadedPackage = ApplicationLoader.applicationContext.getPackageManager()
                        .getPackageArchiveInfo(tempFile.getAbsolutePath(), 0);
                if (downloadedPackage == null || !packageName.equals(downloadedPackage.packageName)) {
                    throw new Exception("Downloaded APK is not a Yumigram package");
                }
                final PackageInfo installedPackage = ApplicationLoader.applicationContext.getPackageManager()
                        .getPackageInfo(packageName, 0);
                if (downloadedPackage.versionCode <= installedPackage.versionCode) {
                    throw new Exception("Downloaded APK is not newer than the installed version");
                }

                if (destination.exists() && !destination.delete()) {
                    throw new Exception("Could not replace cached APK");
                }
                if (tempFile.renameTo(destination)) {
                    destination.setReadable(true, false);
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(destination);
                    });
                } else {
                    tempFile.delete();
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Failed to rename downloaded APK");
                    });
                }

            } catch (Exception e) {
                isDownloading = false;
                FileLog.e(TAG, e);
                try {
                    File updatesDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "yumi_updates");
                    File tempFile = new File(updatesDir, "Yumigram-" + update.tagName.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk.tmp");
                    if (tempFile.exists()) tempFile.delete();
                } catch (Exception ignored) {
                }
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getLocalizedMessage());
                });
            } finally {
                if (conn != null) conn.disconnect();
                isDownloading = false;
            }
        });
    }

    /**
     * Prompts the user to install the downloaded APK using Android's system package installer.
     */
    public static void installApk(Activity activity, File apkFile) {
        if (activity == null || apkFile == null || !apkFile.exists()) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
                final Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ApplicationLoader.getApplicationId()));
                permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(permissionIntent);
                return;
            }

            Uri contentUri = FileProvider.getUriForFile(
                    activity,
                    ApplicationLoader.getApplicationId() + ".provider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(installIntent);
        } catch (Exception e) {
            FileLog.e(TAG, e);
        }
    }
}
