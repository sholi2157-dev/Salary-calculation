package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Stable channel only. No tokens, Firebase, or silent installation. */
object WorkUpdates {
    const val BASE = "https://github.com/sholi2157-dev/Salary-calculation/releases/"
    const val MANIFEST = BASE + "latest/download/release.json"
    data class Release(val code: Long, val name: String, val apk: String, val sha256: String, val notes: String)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()
    fun parse(json: String): Release {
        val obj = JSONObject(json)
        val code = obj.getLong("versionCode")
        val name = obj.getString("versionName")
        val apk = obj.getString("apkUrl")
        val hash = obj.getString("sha256").lowercase()
        require(code > 0 && name.isNotBlank() && name.length <= 100)
        val uri = java.net.URI(apk)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.rawUserInfo == null && uri.port == -1 &&
            uri.path.startsWith("/sholi2157-dev/Salary-calculation/releases/download/") && uri.path.endsWith(".apk"))
        require(hash.matches(Regex("[0-9a-f]{64}")))
        return Release(code, name, apk, hash, obj.optString("releaseNotes", "").take(8000))
    }
    @Suppress("DEPRECATION")
    fun installedVersion(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
    suspend fun check(context: Context): Release? = checkWithClient(context, client)
    internal suspend fun checkWithClient(context: Context, transport: OkHttpClient): Release? = withContext(Dispatchers.IO) {
        transport.newCall(Request.Builder().url(MANIFEST).build()).execute().use { response ->
            if (response.code == 404) return@withContext null // No approved first release yet.
            check(response.isSuccessful)
            val bytes = checkNotNull(response.body).byteStream().use { it.readBytesLimited() }
            val body = bytes.toString(Charsets.UTF_8)
            parse(body).takeIf { it.code > installedVersion(context) }
        }
    }
    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
        while (true) { val n = read(buffer); if (n < 0) break; check(out.size() + n <= 65536); out.write(buffer, 0, n) }; return out.toByteArray()
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Suppress("DEPRECATION")
    private fun certificates(info: android.content.pm.PackageInfo): Set<String> =
        if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.map { hash(it.toByteArray()) }?.toSet() ?: emptySet()
        else info.signatures?.map { hash(it.toByteArray()) }?.toSet() ?: emptySet()
    /** [onProgress] gets 0..1 while the file arrives (only when the server says how big it is), then the file is verified. */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit = {}): File = downloadWithProgress(context, release, client, onProgress)
    /**
     * Kept with exactly this signature: the update gate runs the new test package against older installed builds
     * (back to RC16), which have only this one.
     */
    internal suspend fun downloadWithClient(context: Context, release: Release, transport: OkHttpClient): File = downloadWithProgress(context, release, transport) {}
    @Suppress("DEPRECATION")
    internal suspend fun downloadWithProgress(context: Context, release: Release, transport: OkHttpClient, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "salary-update-${release.code}.apk")
        val partial = File(context.cacheDir, "salary-update-${release.code}.pending.apk")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            transport.newCall(Request.Builder().url(release.apk).build()).execute().use { response ->
                check(response.isSuccessful)
                val body = checkNotNull(response.body)
                val length = body.contentLength()
                body.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L; var shown = -1
                    while (true) { val size = input.read(buffer); if (size < 0) break
                        // Leaving the screen cancels the download.
                        ensureActive()
                        total += size; check(total <= 150L * 1024 * 1024)
                        digest.update(buffer, 0, size); output.write(buffer, 0, size)
                        if (length > 0) { val p = (total * 100 / length).toInt().coerceIn(0, 100); if (p != shown) { shown = p; onProgress(p / 100f) } }
                    }
                } }
            }
            check(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == release.sha256) { "Checksum mismatch" }
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val archive = checkNotNull(context.packageManager.getPackageArchiveInfo(partial.path, flags))
            val installed = context.packageManager.getPackageInfo(context.packageName, flags)
            check(archive.packageName == context.packageName)
            val version = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
            check(version == release.code && version > installedVersion(context))
            val currentSigners = certificates(installed)
            check(currentSigners.isNotEmpty() && certificates(archive) == currentSigners) { "Signing identity mismatch" }
            check(partial.renameTo(file))
            file
        } finally { partial.delete() }
    }
    fun install(context: Context, file: File) {
        context.startActivity(installerIntent(context, file))
    }
    internal fun installerIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/**
 * The newest release the app has seen, kept on the device so Home's bell can show it (with a dot) even offline,
 * until it is installed. Checked on every return to the app, at most once an hour.
 */
object WorkUpdateNotice {
    private fun prefs(context: Context) = context.getSharedPreferences("release_updates", Context.MODE_PRIVATE)
    fun cached(context: Context): WorkUpdates.Release? = prefs(context).getString("available", null)
        ?.let { runCatching { WorkUpdates.parse(it) }.getOrNull() }?.takeIf { it.code > WorkUpdates.installedVersion(context) }
    private fun save(context: Context, release: WorkUpdates.Release?) = prefs(context).edit().apply {
        if (release == null) remove("available")
        else putString("available", JSONObject().put("versionCode", release.code).put("versionName", release.name).put("apkUrl", release.apk)
            .put("sha256", release.sha256).put("releaseNotes", release.notes).toString())
    }.apply()
    /** A fresh check (network), or the cached result when it was checked within the hour or the check fails. */
    suspend fun refresh(context: Context, force: Boolean = false): WorkUpdates.Release? {
        if (!BuildConfig.LOCAL_DISTRIBUTION) return null
        val now = System.currentTimeMillis()
        if (!force && now - prefs(context).getLong("lastBellCheck", 0) < 60 * 60 * 1000L) return cached(context)
        return try {
            WorkUpdates.check(context).also { save(context, it); prefs(context).edit().putLong("lastBellCheck", now).apply() }
        } catch (_: Exception) { cached(context) }
    }
}

@Composable
fun WorkUpdateSettings(automatic: Boolean = false) {
    if (!BuildConfig.LOCAL_DISTRIBUTION) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("release_updates", Context.MODE_PRIVATE) }
    var candidate by remember { mutableStateOf<WorkUpdates.Release?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun check(manual: Boolean) {
        if (busy) return
        val now = System.currentTimeMillis()
        if (!manual && now - preferences.getLong("lastCheck", 0) < 24 * 60 * 60 * 1000L) return
        preferences.edit().putLong("lastCheck", now).apply()
        busy = true
        scope.launch {
            try {
                val update = WorkUpdates.check(context)
                if (manual || update?.code != preferences.getLong("dismissed", -1)) candidate = update
                if (manual && update == null) Toast.makeText(context, "אין עדכון יציב חדש", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                if (manual) Toast.makeText(context, "לא ניתן לבדוק כעת. הנתונים והשימוש המקומי זמינים", Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }
    LaunchedEffect(automatic) { if (automatic) check(false) }
    if (!automatic) TextButton(onClick = { check(true) }, enabled = !busy) {
        Text(if (busy) "בודק…" else "בדוק עדכונים (${BuildConfig.VERSION_NAME})")
    }
    candidate?.let { release ->
        // "אחר כך" on the daily check is remembered for this version; the bell and Settings still offer it.
        com.example.ui.kis.UpdateSheet(release, onDismiss = { candidate = null },
            onLater = { preferences.edit().putLong("dismissed", release.code).apply(); candidate = null })
    }
}
