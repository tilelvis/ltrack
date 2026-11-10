package com.loantracker.app.services

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.loantracker.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updater backed by GitHub Releases.
 *
 * - Checks `releases/latest` of [BuildConfig.GITHUB_REPO] whenever the app is opened (throttled).
 * - Downloads the release APK and hands it to the Android installer. Android only accepts it as an
 *   update if it is signed with the same certificate as the installed app.
 * - Works for public repositories (the GitHub API is called without a token).
 */
class UpdateController private constructor(private val app: Context) {

    data class UpdateInfo(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
        val pageUrl: String
    )

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val progress: Float) : State
        data class Ready(val info: UpdateInfo, val file: File) : State
        data class Failed(val message: String, val info: UpdateInfo?) : State
    }

    var state: State by mutableStateOf<State>(State.Idle)
        private set

    val currentVersion: String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull() ?: "0"

    private val prefs = app.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    /** Called every time the app comes to the foreground; silent unless an update exists. */
    fun autoCheck() {
        when (state) {
            is State.Checking, is State.Available, is State.Downloading, is State.Ready -> return
            else -> Unit
        }
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < THROTTLE_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        job?.cancel()
        job = scope.launch {
            try {
                val info = fetchLatest()
                if (info != null &&
                    isNewer(info.version, currentVersion) &&
                    prefs.getString(KEY_SKIPPED, null) != info.version
                ) {
                    state = State.Available(info)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // Offline or rate limited: stay quiet on automatic checks.
            }
        }
    }

    /** Manual check from Settings: always reports a result. */
    fun checkNow() {
        if (state is State.Checking || state is State.Downloading) return
        state = State.Checking
        job?.cancel()
        job = scope.launch {
            state = try {
                val info = fetchLatest()
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (info != null && isNewer(info.version, currentVersion)) State.Available(info)
                else State.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Failed("Couldn't check for updates. ${e.message ?: "Check your connection."}", null)
            }
        }
    }

    fun startDownload(info: UpdateInfo) {
        if (state is State.Downloading) return
        state = State.Downloading(info, 0f)
        job?.cancel()
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    downloadApk(info) { p -> state = State.Downloading(info, p) }
                }
                state = State.Ready(info, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = State.Failed("Download failed. ${e.message ?: "Check your connection."}", info)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        state = State.Idle
    }

    fun dismiss() {
        state = State.Idle
    }

    fun skip(info: UpdateInfo) {
        prefs.edit().putString(KEY_SKIPPED, info.version).apply()
        state = State.Idle
    }

    /** Clears a stale "up to date" / error message when Settings is opened. */
    fun clearStatus() {
        if (state is State.UpToDate || state is State.Failed) state = State.Idle
    }

    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            // One-time permission: "Allow from this source". The user returns and taps Install.
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(intent) } catch (ignored: ActivityNotFoundException) { }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (ignored: ActivityNotFoundException) {
            state = State.Failed("No installer found on this device.", null)
        }
    }

    // ------------------------------------------------------------------ network

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LoanTracker-Android")
        }
        try {
            val code = conn.responseCode
            if (code == 404) throw IOException("No public release found (is the repository public?)")
            if (code != 200) throw IOException("GitHub returned HTTP $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

            var apkUrl: String? = null
            var size = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true) &&
                        (apkUrl == null || name.contains("release", ignoreCase = true))
                    ) {
                        apkUrl = a.optString("browser_download_url")
                        size = a.optLong("size")
                    }
                }
            }
            if (apkUrl.isNullOrBlank()) return@withContext null

            val body = json.optString("body").substringBefore("\n---").trim()
            UpdateInfo(
                version = json.optString("tag_name").removePrefix("v").removePrefix("V"),
                notes = body.take(900),
                apkUrl = apkUrl,
                sizeBytes = size,
                pageUrl = json.optString("html_url")
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadApk(info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val dir = File(app.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val out = File(dir, "loan-tracker-${info.version}.apk")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LoanTracker-Android")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (info.sizeBytes > 0 && out.length() != info.sizeBytes) {
            out.delete()
            throw IOException("Incomplete download")
        }
        return out
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED = "skipped_version"
        private const val THROTTLE_MS = 30 * 60 * 1000L

        @Volatile private var instance: UpdateController? = null

        fun get(context: Context): UpdateController =
            instance ?: synchronized(this) {
                instance ?: UpdateController(context.applicationContext).also { instance = it }
            }

        /** True when [remote] (e.g. "2.8.0" or "v2.8.0") is a higher version than [local]. */
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String): List<Int> =
                v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
                    .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            val r = parts(remote)
            val l = parts(local)
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
