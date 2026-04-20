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
        private const val TAG = "Zero100OTA"
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking: $API_URL")
            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "Zero100-App")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true

            val code = conn.responseCode
            Log.d(TAG, "Response: $code")

            if (code != 200) {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                Log.w(TAG, "Failed: $code, body: ${errorBody?.take(200)}")
                conn.disconnect()
                return@withContext null
            }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            Log.d(TAG, "Response length: ${response.length}")

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
                Log.w(TAG, "No APK in assets")
                return@withContext null
            }

            val currentVersion = getCurrentVersion()
            val isNewer = compareVersions(versionName, currentVersion) > 0
            Log.d(TAG, "Current=$currentVersion Latest=$versionName isNewer=$isNewer")

            UpdateInfo(versionName, downloadUrl, releaseNotes, isNewer)
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    suspend fun downloadAndInstall(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading: $downloadUrl")

            // GitHub download URL은 redirect함 — 수동 따라가기
            var currentUrl = URL(downloadUrl)
            var conn: HttpURLConnection
            var redirects = 0
            while (true) {
                conn = currentUrl.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Zero100-App")
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.connect()

                val code = conn.responseCode
                if (code in 301..303 || code == 307 || code == 308) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location == null || redirects++ > 5) break
                    currentUrl = URL(location)
                    Log.d(TAG, "Redirect $redirects -> ${location.take(80)}")
                } else {
                    break
                }
            }

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
            Log.d(TAG, "Downloaded: ${apkFile.length()} bytes")

            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    fun getCurrentVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) { "0.0.0" }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(p1.size, p2.size)) {
            val a = p1.getOrElse(i) { 0 }
            val b = p2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}
