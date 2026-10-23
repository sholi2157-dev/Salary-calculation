package com.example

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Counts installations, not people: each device sends one random identifier once, on the first launch of a version
 * that has this code (a fresh install or an update). Only the identifier, the version name and new/update are sent;
 * never a name, account, device detail or any work record. Distributed builds only.
 */
object InstallCount {
    private const val WORK = "kis-install-count"
    private const val PREFS = "kis_install_count"
    private const val PROJECT = "workshiftsapp"
    private const val COLLECTION = "installs"

    fun schedule(context: Context) {
        if (!BuildConfig.LOCAL_DISTRIBUTION || BuildConfig.INSTALL_COUNT_KEY.isEmpty() || sent(context)) return
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<InstallCountWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build())
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun sent(context: Context) = prefs(context).getBoolean("sent", false)

    /** A random 32-character identifier, kept on the device so a retry can never count it twice. */
    fun id(context: Context): String = prefs(context).getString("id", null) ?: UUID.randomUUID().toString().replace("-", "")
        .also { prefs(context).edit().putString("id", it).apply() }

    /** "new" when this version is the first one installed on the device, "update" when it replaced an earlier one. */
    fun kind(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (info.firstInstallTime == info.lastUpdateTime) "new" else "update"
    }.getOrDefault("update")

    /** One create-only write; the server sets the time. firestore.rules accepts exactly these fields. */
    fun body(id: String, version: String, kind: String): String = JSONObject().put("writes", JSONArray().put(JSONObject()
        .put("update", JSONObject()
            .put("name", "projects/$PROJECT/databases/(default)/documents/$COLLECTION/$id")
            .put("fields", JSONObject()
                .put("version", JSONObject().put("stringValue", version))
                .put("kind", JSONObject().put("stringValue", kind))))
        .put("currentDocument", JSONObject().put("exists", false))
        .put("updateTransforms", JSONArray().put(JSONObject().put("fieldPath", "at").put("setToServerValue", "REQUEST_TIME")))))
        .toString()

    /** True once the device is counted (also when an earlier attempt already arrived but its answer was lost). */
    fun accepted(status: Int, response: String): Boolean =
        status in 200..299 || (status in listOf(400, 409) && (response.contains("ALREADY_EXISTS") || response.contains("FAILED_PRECONDITION")))

    suspend fun send(context: Context, client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()): Boolean = withContext(Dispatchers.IO) {
        if (sent(context)) return@withContext true
        val request = Request.Builder()
            .url("https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents:commit?key=${BuildConfig.INSTALL_COUNT_KEY}")
            .post(body(id(context), BuildConfig.VERSION_NAME, kind(context)).toRequestBody("application/json".toMediaType())).build()
        val ok = client.newCall(request).execute().use { accepted(it.code, it.body?.string().orEmpty()) }
        if (ok) prefs(context).edit().putBoolean("sent", true).apply()
        ok
    }
}

class InstallCountWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (InstallCount.send(applicationContext)) Result.success() else if (runAttemptCount < 12) Result.retry() else Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < 12) Result.retry() else Result.success()
    }
}
