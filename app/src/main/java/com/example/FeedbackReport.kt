package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * A short local trail of what the app did, for feedback reports. Only fixed event names and fixed details written
 * in this code (a page name, an exception class and code lines) are kept: never a typed text, amount, workplace,
 * name, email or key. The last [MAX] events stay on the device and leave it only inside a report the user sends.
 */
object DiagnosticLog {
    private const val PREFS = "kis_diagnostics"
    const val MAX = 80
    private val events = ArrayDeque<JSONObject>()
    private var prefs: android.content.SharedPreferences? = null

    @Synchronized fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching { JSONArray(prefs?.getString("events", "[]")) }.getOrNull()?.let { a -> for (i in 0 until a.length()) events.addLast(a.getJSONObject(i)) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record("crash", crashDetail(error), commit = true) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun event(name: String, detail: String = "") = runCatching { record(name, detail, commit = false) }

    @Synchronized private fun record(name: String, detail: String, commit: Boolean) {
        events.addLast(JSONObject().put("at", System.currentTimeMillis()).put("e", name).apply { if (detail.isNotEmpty()) put("d", detail) })
        while (events.size > MAX) events.removeFirst()
        val edit = prefs?.edit()?.putString("events", JSONArray(events.toList()).toString()) ?: return
        if (commit) edit.commit() else edit.apply()
    }

    /** The class and the app's own code lines only: an exception message can carry user text. */
    fun crashDetail(error: Throwable): String = (listOf(error.javaClass.name) + error.stackTrace
        .filter { it.className.startsWith("com.example") }.take(6).map { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" })
        .joinToString(" < ")

    @Synchronized fun snapshot(): JSONArray = JSONArray(events.toList())
}

/**
 * Feedback goes straight to the owner's mailbox through a Google Apps Script web app (the owner's own account sends
 * it, docs/feedback-relay.md), so the app holds no mail password and no server of its own. Until [URL] is
 * set, the form falls back to the mail-app draft.
 */
object FeedbackReport {
    const val URL = ""
    const val TOKEN = "salary-feedback-v1"
    private const val MAX_IMAGE_SIDE = 1600

    val enabled get() = URL.isNotEmpty()

    data class Device(val manufacturer: String, val model: String, val android: String, val sdk: Int, val locale: String, val timeZone: String,
                      val widthDp: Int, val heightDp: Int, val fontScale: Float, val darkSystem: Boolean)

    fun device(context: Context): Device {
        val conf = context.resources.configuration
        return Device(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Locale.getDefault().toLanguageTag(), TimeZone.getDefault().id,
            conf.screenWidthDp, conf.screenHeightDp, conf.fontScale,
            (conf.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
    }

    /** Everything but the screenshot. [state] is a fixed allowlist of counts and switches (see FeedbackForm). */
    fun build(kind: String, message: String, versionName: String, versionCode: Long, device: Device, state: Map<String, Any>, events: JSONArray, sentAt: Long): JSONObject =
        JSONObject()
            .put("token", TOKEN)
            .put("kind", kind)
            .put("message", message.trim())
            .put("sentAt", sentAt)
            .put("app", JSONObject().put("platform", "android").put("version", versionName).put("code", versionCode)
                .put("distribution", BuildConfig.LOCAL_DISTRIBUTION).put("buildType", BuildConfig.BUILD_TYPE))
            .put("device", JSONObject().put("manufacturer", device.manufacturer).put("model", device.model).put("android", device.android)
                .put("sdk", device.sdk).put("locale", device.locale).put("timeZone", device.timeZone)
                .put("screenDp", "${device.widthDp}x${device.heightDp}").put("fontScale", device.fontScale.toDouble()).put("systemDark", device.darkSystem))
            .put("state", JSONObject(state))
            .put("events", events)

    @Suppress("DEPRECATION") fun version(context: Context): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return (info.versionName ?: "") to (if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
    }

    /** The picked image, shrunk to at most [MAX_IMAGE_SIDE] px and re-encoded (which also drops its location data). */
    fun screenshot(context: Context, uri: Uri): JSONObject? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val scale = MAX_IMAGE_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
        JSONObject().put("name", "screenshot.jpg").put("mime", "image/jpeg").put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }.getOrNull()

    /** True only when the relay answered that the mail went out. */
    suspend fun send(report: JSONObject, client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()): Boolean = withContext(Dispatchers.IO) {
        // text/plain keeps the request "simple" for Apps Script; it answers with a redirect that OkHttp follows.
        val request = Request.Builder().url(URL).post(report.toString().toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
        runCatching { client.newCall(request).execute().use { it.isSuccessful && accepted(it.body?.string().orEmpty()) } }.getOrDefault(false)
    }

    fun accepted(response: String) = runCatching { JSONObject(response).optBoolean("ok") }.getOrDefault(false)
}
