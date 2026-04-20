package com.ableLabs.zero100.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isNewer: Boolean
)

class UpdateChecker(private val context: Context) {

    companion object {
        const val GITHUB_OWNER = "git961219"
        const val GITHUB_REPO = "zero100"
        const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val TAG = "UpdateChecker"
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection(URL(API_URL))
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            val code = conn.responseCode
            Log.d(TAG, "API response code: $code")

            if (code != 200) {
                Log.w(TAG, "API failed: $code")
                return@withContext null
            }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(response)

            val tagName = json.getString("tag_name")
            val versionName = tagName.removePrefix("v")
            val releaseNotes = json.optString("body", "")

            val assets = json.getJSONArray("assets")
            var downloadUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (downloadUrl.isEmpty()) {
                Log.w(TAG, "No APK asset found")
                return@withContext null
            }

            val currentVersion = getCurrentVersion()
            val isNewer = compareVersions(versionName, currentVersion) > 0
            Log.d(TAG, "Current: $currentVersion, Latest: $versionName, isNewer: $isNewer")

            UpdateInfo(versionName, downloadUrl, releaseNotes, isNewer)
        } catch (e: Exception) {
            Log.e(TAG, "Check failed: ${e.message}", e)
            null
        }
    }

    suspend fun downloadAndInstall(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading: $downloadUrl")
            // GitHub redirect를 수동으로 따라가기
            val conn = openConnection(URL(downloadUrl))

            val totalSize = conn.contentLength
            Log.d(TAG, "Download size: $totalSize")
            val apkFile = File(context.cacheDir, "zero100_update.apk")

            conn.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalSize > 0) {
                            onProgress(((downloaded * 100) / totalSize).toInt())
                        }
                    }
                }
            }
            conn.disconnect()

            Log.d(TAG, "Download complete: ${apkFile.length()} bytes")

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            false
        }
    }

    fun getCurrentVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    /**
     * HTTP 연결 열기 — redirect를 수동으로 따라감 (GitHub 302 대응)
     */
    private fun openConnection(url: URL): HttpURLConnection {
        var currentUrl = url
        var redirectCount = 0
        while (redirectCount < 5) {
            val conn = currentUrl.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.connect()

            val code = conn.responseCode
            if (code in 301..303 || code == 307 || code == 308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null) break
                currentUrl = URL(location)
                redirectCount++
                Log.d(TAG, "Redirect $redirectCount -> $location")
            } else {
                return conn
            }
        }
        // fallback: 기본 방식
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        return conn
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
