package com.example

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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

/** Android feedback has a deliberately small payload; no app state, device identifiers or attachments. */
object FeedbackReport {
    const val URL = "https://script.google.com/macros/s/AKfycbwDfq5iA_2NSGYj57js0uwAuoSjErBWN-mB7jkjse4HKS476TPyqEIKAqitrVD47PmR/exec"
    const val TOKEN = "salary-feedback-v1"
    const val RECIPIENT = "israelshimon1313-vzptcg@mail.grokbot.com"
    const val MAX_MESSAGE = 4000
    const val MAX_CONTACT = 254
    val enabled get() = URL.isNotEmpty()

    // Preserve multiline input, but remove C0/C1 controls (including CR) and invisible format controls.
    fun clean(value: String, limit: Int): String = value.filter {
        it == '\n' || (!Character.isISOControl(it) && Character.getType(it) != Character.FORMAT.toInt())
    }.take(limit)

    fun build(kind: String, message: String, versionName: String, versionCode: Long,
              language: String, sentAt: Long, contactEmail: String = ""): JSONObject = JSONObject()
        .put("schema", "android-feedback-v1")
        .put("kind", clean(kind, 20).replace("\n", ""))
        .put("message", clean(message, MAX_MESSAGE).trim())
        .put("sentAt", sentAt)
        .put("screen", "settings/feedback")
        .put("language", clean(language, 40).replace("\n", ""))
        .put("app", JSONObject().put("platform", "android").put("version", clean(versionName, 80))
            .put("code", versionCode).put("buildType", BuildConfig.BUILD_TYPE))
        .apply { clean(contactEmail, MAX_CONTACT).replace("\n", "").trim().takeIf { it.isNotEmpty() }?.let { put("contactEmail", it) } }

    @Suppress("DEPRECATION") fun version(context: Context): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return (info.versionName ?: "") to (if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
    }

    /** True only when the relay answered that the mail went out. */
    suspend fun send(report: JSONObject, client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()): Boolean = withContext(Dispatchers.IO) {
        // text/plain keeps the request "simple" for Apps Script; it answers with a redirect that OkHttp follows.
        val request = Request.Builder().url(URL).post(JSONObject(report.toString()).put("token", TOKEN).toString().toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
        runCatching { client.newCall(request).execute().use { it.isSuccessful && accepted(it.body?.string().orEmpty()) } }.getOrDefault(false)
    }

    fun accepted(response: String) = runCatching { JSONObject(response).optBoolean("ok") }.getOrDefault(false)
}
