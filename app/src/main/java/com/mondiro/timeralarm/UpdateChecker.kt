package com.mondiro.timeralarm

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Tự cập nhật qua GitHub Releases của repo public satthupc00/Timer_Alarm.
 * Mỗi bản phát hành có tag "v<versionName>" và kèm 1 file .apk (do GitHub Actions tạo).
 */
object UpdateChecker {
    private const val LATEST_URL = "https://api.github.com/repos/satthupc00/Timer_Alarm/releases/latest"

    data class Release(val version: String, val notes: String, val apkUrl: String)

    sealed class Result {
        data class Available(val release: Release) : Result()
        object Latest : Result()
        data class Error(val message: String) : Result()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun check(callback: (Result) -> Unit) {
        executor.execute {
            val result = try {
                val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Mondiro-TimerAlarm")
                }
                when (val code = conn.responseCode) {
                    200 -> {
                        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        val version = json.optString("tag_name").removePrefix("v").trim()
                        val assets = json.optJSONArray("assets")
                        var apk = ""
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val a = assets.getJSONObject(i)
                                if (a.optString("name").endsWith(".apk")) {
                                    apk = a.optString("browser_download_url"); break
                                }
                            }
                        }
                        if (apk.isNotEmpty() && isNewer(version, BuildConfig.VERSION_NAME)) {
                            Result.Available(Release(version, json.optString("body"), apk))
                        } else {
                            Result.Latest
                        }
                    }
                    404 -> Result.Latest // chưa có bản phát hành nào
                    else -> Result.Error("HTTP $code")
                }
            } catch (e: Exception) {
                Result.Error(e.message ?: e.javaClass.simpleName)
            }
            main.post { callback(result) }
        }
    }

    /** So sánh "1.10.2" với "1.9" theo từng số. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Tải APK về cache, báo % qua onProgress, xong thì trả file. */
    fun download(activity: Activity, release: Release, onProgress: (Int) -> Unit, onDone: (File?, String?) -> Unit) {
        executor.execute {
            try {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "Mondiro-TimerAlarm-v${release.version}.apk")
                val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mondiro-TimerAlarm")
                }
                if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                var done = 0L
                var lastPct = -1
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; main.post { onProgress(pct) } }
                            }
                        }
                    }
                }
                main.post { onDone(out, null) }
            } catch (e: Exception) {
                main.post { onDone(null, e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** true nếu đã mở trình cài; false nếu phải đi cấp quyền "cài app không rõ nguồn" trước. */
    fun install(activity: Activity, apk: File): Boolean {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return false
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return true
    }
}
