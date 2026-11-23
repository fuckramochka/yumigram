package org.telegram.messenger.yumigram.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.BulletinFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * High-performance, reliable Auto-Update Manager for Yumigram (Юмиграм).
 * Connects directly to GitHub Releases API (fuckramochka/yumigram),
 * checks for green builds / releases, downloads the optimized arm64-v8a APK,
 * and seamlessly invokes the Android Package Installer.
 */
public class YumiUpdateManager {

    private static final String TAG = "YumiUpdateManager";
    public static final String CURRENT_VERSION = "v1.0.25";
    public static final String GITHUB_REPO = "fuckramochka/yumigram";
    public static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile boolean isDownloading = false;
    private static volatile UpdateInfo cachedUpdate = null;

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
                int n = i < newParts.length && !newParts[i].isEmpty() ? Integer.parseInt(newParts[i]) : 0;
                int c = i < currParts.length && !currParts[i].isEmpty() ? Integer.parseInt(currParts[i]) : 0;
                if (n > c) return true;
                if (n < c) return false;
            }
            return false;
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

    public static boolean isDownloading() {
        return isDownloading;
    }

    /**
     * Checks for the latest release on GitHub in the background.
     */
    public static void checkUpdate(boolean manual, CheckCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "Yumigram-Android/" + CURRENT_VERSION);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("HTTP " + code);
                    });
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

                JSONArray assets = root.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name");
                        if (assetName.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url");
                            apkSize = asset.optLong("size", 0);
                            break;
                        }
                    }
                }

                if (TextUtils.isEmpty(apkUrl)) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("No APK asset found in release");
                    });
                    return;
                }

                UpdateInfo info = new UpdateInfo(tagName, name, body, apkUrl, apkSize, publishedAt);
                cachedUpdate = info;
                boolean hasUpdate = info.isNewerThan(CURRENT_VERSION);

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(info, hasUpdate);
                });

            } catch (Exception e) {
                FileLog.e(TAG, e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getLocalizedMessage());
                });
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
            try {
                File updatesDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "yumi_updates");
                if (!updatesDir.exists()) {
                    updatesDir.mkdirs();
                }

                File destination = new File(updatesDir, "Yumigram-" + update.tagName + ".apk");
                File tempFile = new File(updatesDir, "Yumigram-" + update.tagName + ".apk.tmp");

                URL currentUrl = new URL(update.apkUrl);
                HttpURLConnection conn = null;
                for (int redirect = 0; redirect < 5; redirect++) {
                    conn = (HttpURLConnection) currentUrl.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "Yumigram-Android/" + CURRENT_VERSION);
                    conn.setConnectTimeout(25000);
                    conn.setReadTimeout(30000);

                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308 || code == 303) {
                        String redirectUrl = conn.getHeaderField("Location");
                        currentUrl = new URL(redirectUrl);
                    } else {
                        break;
                    }
                }

                if (conn == null) {
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
                }

                isDownloading = false;

                if (tempFile.renameTo(destination)) {
                    destination.setReadable(true, false);
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(destination);
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Failed to rename downloaded APK");
                    });
                }

            } catch (Exception e) {
                isDownloading = false;
                FileLog.e(TAG, e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getLocalizedMessage());
                });
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
