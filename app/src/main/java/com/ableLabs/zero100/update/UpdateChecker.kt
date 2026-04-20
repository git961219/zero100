package com.ableLabs.zero100.update

import android.content.Context
import android.content.Intent
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
        // GitHub API -- 실제 레포 만들면 여기만 수정
        const val GITHUB_OWNER = "git961219"
        const val GITHUB_REPO = "zero100"
        const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    /**
     * GitHub Releases에서 최신 버전 확인
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode != 200) return@withContext null

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val tagName = json.getString("tag_name") // "v1.0.1"
            val versionName = tagName.removePrefix("v")
            val releaseNotes = json.optString("body", "")

            // APK 다운로드 URL 찾기
            val assets = json.getJSONArray("assets")
            var downloadUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (downloadUrl.isEmpty()) return@withContext null

            // 현재 버전과 비교
            val currentVersion = getCurrentVersion()
            val isNewer = compareVersions(versionName, currentVersion) > 0

            UpdateInfo(versionName, downloadUrl, releaseNotes, isNewer)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * APK 다운로드 및 설치 인텐트
     */
    suspend fun downloadAndInstall(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connect()

            val totalSize = conn.contentLength
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

            // 설치 인텐트
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
     * 버전 비교: "1.0.1" vs "1.0.0" -> 1 (더 새로움)
     */
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
