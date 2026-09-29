package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
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
    private fun installedVersion(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(MANIFEST).build()).execute().use { response ->
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
    @Suppress("DEPRECATION")
    suspend fun download(context: Context, release: Release): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "salary-update-${release.code}.apk")
        val partial = File(context.cacheDir, "salary-update-${release.code}.pending.apk")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(Request.Builder().url(release.apk).build()).execute().use { response ->
                check(response.isSuccessful)
                checkNotNull(response.body).byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) { val size = input.read(buffer); if (size < 0) break
                        total += size; check(total <= 150L * 1024 * 1024)
                        digest.update(buffer, 0, size); output.write(buffer, 0, size)
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
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
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
        fun dismiss() { if (!busy) { preferences.edit().putLong("dismissed", release.code).apply(); candidate = null } }
        AlertDialog(onDismissRequest = { dismiss() }, title = { Text("גרסה ${release.name} זמינה") },
            text = { Text(if (busy) "מוריד ומאמת את העדכון…" else release.notes) },
            dismissButton = { TextButton(onClick = { dismiss() }, enabled = !busy) { Text("מאוחר יותר") } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "אפשר התקנה ממקור זה, חזור ולחץ עדכון", Toast.LENGTH_LONG).show()
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                } else {
                    busy = true
                    scope.launch {
                        try { WorkUpdates.install(context, WorkUpdates.download(context, release)); candidate = null }
                        catch (_: Exception) { Toast.makeText(context, "העדכון לא הותקן. ההורדה או האימות נכשלו; האפליקציה הקיימת נשמרה", Toast.LENGTH_LONG).show() }
                        finally { busy = false }
                    }
                }
            }) { Text("עדכון") } })
    }
}
