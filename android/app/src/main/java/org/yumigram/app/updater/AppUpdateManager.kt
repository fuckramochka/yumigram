package org.yumigram.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long
)

object AppUpdateManager {
    const val CURRENT_VERSION = "v1.0.8"
    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/fuckramochka/yumigram/releases/latest"

    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_LATEST_RELEASE_API)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "YumigramAndroid")
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val tagName = json.optString("tag_name", "")
                val releaseTitle = json.optString("name", "Оновлення $tagName")
                val releaseNotes = json.optString("body", "Покращення стабільності та нові функції.")

                val assets = json.optJSONArray("assets")
                var downloadUrl = ""
                var apkSize = 0L

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith("release.apk", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            if (name.contains("release", ignoreCase = true)) {
                                break // prefer release apk
                            }
                        }
                    }
                }

                if (tagName.isNotEmpty() && tagName != CURRENT_VERSION && downloadUrl.isNotEmpty()) {
                    return@withContext UpdateInfo(
                        tagName = tagName,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl,
                        apkSize = apkSize
                    )
                }
            }
            null
        } catch (e: Throwable) {
            android.util.Log.e("YumigramUpdate", "checkForUpdates failed", e)
            null
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val apkFile = File(updatesDir, "yumigram-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "YumigramAndroid")
            }

            val totalBytes = conn.contentLength
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                            onProgress(progress)
                        }
                    }
                }
            }

            onProgress(1.0f)

            withContext(Dispatchers.Main) {
                try {
                    val apkUri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(intent)
                } catch (e: Throwable) {
                    onError("Не вдалося запустити інсталятор: ${e.localizedMessage}")
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("YumigramUpdate", "downloadAndInstallApk error", e)
            withContext(Dispatchers.Main) {
                onError("Помилка завантаження: ${e.localizedMessage}")
            }
        }
    }
}
