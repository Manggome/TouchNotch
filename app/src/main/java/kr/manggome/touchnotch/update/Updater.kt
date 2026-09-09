package kr.manggome.touchnotch.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.manggome.touchnotch.BuildConfig
import org.json.JSONObject

/** GitHub 릴리스 정보 */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val apkUrl: String?,
    val apkSize: Long,
    val htmlUrl: String,
) {
    /** 태그에서 뽑아낸 버전 문자열 (v1.0.0.12 → 1.0.0.12) */
    val version: String get() = tag.trimStart('v', 'V')
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class ReadyToInstall(val file: File, val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

object Updater {

    private const val TAG = "Updater"
    private const val TIMEOUT_MS = 15_000

    val repo: String get() = BuildConfig.GITHUB_REPO
    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val releasesPageUrl: String get() = "https://github.com/$repo/releases"

    /** GitHub 최신 릴리스를 가져온다 */
    suspend fun fetchLatest(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$repo/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "TouchNotch-Android")
            }
            try {
                if (conn.responseCode == 404) {
                    error("아직 배포된 릴리스가 없습니다")
                }
                if (conn.responseCode !in 200..299) {
                    error("GitHub 응답 오류 (${conn.responseCode})")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseRelease(JSONObject(body))
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        var apkSize = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").ifBlank { null }
                    apkSize = asset.optLong("size")
                    break
                }
            }
        }
        val tag = json.optString("tag_name")
        return ReleaseInfo(
            tag = tag,
            name = json.optString("name").ifBlank { tag },
            notes = json.optString("body").trim(),
            apkUrl = apkUrl,
            apkSize = apkSize,
            htmlUrl = json.optString("html_url").ifBlank { releasesPageUrl },
        )
    }

    /**
     * 버전 비교. 숫자 구간만 뽑아 앞에서부터 비교한다.
     * "1.0.0.12" vs "1.0.0.9" → 12 > 9 이므로 새 버전.
     */
    fun isNewer(remote: String, local: String = currentVersion): Boolean {
        val r = remote.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toLongOrNull() ?: 0L }
        val l = local.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toLongOrNull() ?: 0L }
        if (r.isEmpty()) return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0L }
            val lv = l.getOrElse(i) { 0L }
            if (rv != lv) return rv > lv
        }
        return false
    }

    /** APK 를 캐시에 내려받는다 */
    suspend fun download(
        context: Context,
        release: ReleaseInfo,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val apkUrl = release.apkUrl ?: error("릴리스에 APK 파일이 없습니다")
            val dir = File(context.cacheDir, "updates").apply {
                deleteRecursively()
                mkdirs()
            }
            val target = File(dir, "TouchNotch-${release.version}.apk")

            var conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "TouchNotch-Android")
            }
            // GitHub 다운로드는 다른 호스트로 리다이렉트되므로 직접 한 번 더 따라간다
            if (conn.responseCode in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                conn = (URL(location).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("User-Agent", "TouchNotch-Android")
                }
            }
            try {
                if (conn.responseCode !in 200..299) {
                    error("다운로드 실패 (${conn.responseCode})")
                }
                val total = if (release.apkSize > 0) release.apkSize else conn.contentLengthLong
                var read = 0L
                var lastPercent = -1
                conn.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (target.length() < 1024) error("내려받은 파일이 손상됐습니다")
            target
        }.onFailure { Log.w(TAG, "APK 다운로드 실패", it) }
    }

    /** 설치 화면을 띄운다 */
    fun install(context: Context, apk: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
