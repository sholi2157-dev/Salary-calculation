package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.WorkDao
import com.example.data.WorkEntry
import com.example.data.WorkPay
import com.example.domain.V2Plan
import com.example.domain.V2Rules
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Stage C settings. Kept on this device (like the theme) and deliberately not in the backup's local preferences:
 * older versions reject backups that carry preference names they do not know.
 */
class PlanSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kis_plan", Context.MODE_PRIVATE)
    var autoConfirm: Boolean
        get() = prefs.getBoolean("autoConfirm", true)
        set(v) { prefs.edit().putBoolean("autoConfirm", v).apply() }
    var notifyMonthly: Boolean
        get() = prefs.getBoolean("notifyMonthly", false)
        set(v) { prefs.edit().putBoolean("notifyMonthly", v).apply() }
    var notifyRate: Boolean
        get() = prefs.getBoolean("notifyRate", false)
        set(v) { prefs.edit().putBoolean("notifyRate", v).apply() }
    var quiet: Boolean
        get() = prefs.getBoolean("quiet", true)
        set(v) { prefs.edit().putBoolean("quiet", v).apply() }
    var quietFrom: String
        get() = prefs.getString("quietFrom", "22:00") ?: "22:00"
        set(v) { prefs.edit().putString("quietFrom", v).apply() }
    var quietTo: String
        get() = prefs.getString("quietTo", "07:00") ?: "07:00"
        set(v) { prefs.edit().putString("quietTo", v).apply() }
    var lastMonthly: String
        get() = prefs.getString("lastMonthly", "") ?: ""
        set(v) { prefs.edit().putString("lastMonthly", v).apply() }
    var lastRate: Long
        get() = prefs.getLong("lastRate", 0L)
        set(v) { prefs.edit().putLong("lastRate", v).apply() }

    /** The reminders that were actually sent, newest first (at most [RECENT_MAX]), for "התראות אחרונות". */
    data class Sent(val kind: String, val title: String, val text: String, val time: Long, val month: String)
    val recent: List<Sent>
        get() = runCatching {
            val a = org.json.JSONArray(prefs.getString("recent", "[]"))
            (0 until a.length()).map { i -> a.getJSONObject(i).let { Sent(it.getString("kind"), it.getString("title"), it.getString("text"), it.getLong("time"), it.optString("month")) } }
        }.getOrDefault(emptyList())
    fun addRecent(sent: Sent) {
        val all = (listOf(sent) + recent).take(RECENT_MAX)
        val a = org.json.JSONArray().apply { all.forEach { put(org.json.JSONObject().put("kind", it.kind).put("title", it.title).put("text", it.text).put("time", it.time).put("month", it.month)) } }
        prefs.edit().putString("recent", a.toString()).apply()
    }
    /** When the bell sheet was last opened: anything sent after it shows a dot on the bell. */
    var seenAt: Long
        get() = prefs.getLong("seenAt", 0L)
        set(v) { prefs.edit().putLong("seenAt", v).apply() }
    val unread: Boolean get() = recent.any { it.time > seenAt }
    companion object { const val RECENT_MAX = 20 }
}

object PlanJobs {
    const val CHANNEL = "plan_reminders"
    const val ACTION_MONTH = "com.example.ACTION_OPEN_HISTORY"
    const val ACTION_RATES = "com.example.ACTION_COMPLETE_RATES"
    private const val DAILY = "kis-plan-daily"
    private const val AFTER_QUIET = "kis-plan-after-quiet"
    private const val WEEK = 7L * 24 * 60 * 60 * 1000
    /** How far back plans are shown and recorded. */
    const val LOOK_BACK_DAYS = 62

    /** One idempotent daily job (and a catch-up on every app start/resume). */
    fun schedule(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PlanWorker>(1, TimeUnit.DAYS).build())
        }
    }

    /** Records every finished occurrence that is not held. Safe to run twice at once: the unique key decides. */
    suspend fun catchUp(dao: WorkDao, settings: PlanSettings, now: Long = System.currentTimeMillis()): List<Long> {
        if (!settings.autoConfirm) return emptyList()
        val series = dao.getSeries()
        if (series.isEmpty()) return emptyList()
        // The same window the screens show: an old restored plan never floods the history with years of days.
        val today = V2Plan.dateOf(now)
        val from = maxOf(series.minOf { it.fromDate }, V2Rules.civilFromDays(V2Rules.daysFromCivil(today) - LOOK_BACK_DAYS))
        val occurrences = V2Plan.occurrences(series, dao.getExceptions(), dao.confirmedKeys().toSet(), from, today, now)
        return dao.confirmOccurrences(V2Plan.toConfirm(occurrences).map { V2Plan.toEntry(it, auto = true) })
    }

    /** [month] is "YYYY-MM" for the monthly summary (the month it is about). */
    data class Note(val kind: String, val title: String, val text: String, val month: String = "")

    /** What is due now (pure; [run] sends and records it). */
    fun due(entries: List<WorkEntry>, settings: PlanSettings, now: Long): List<Note> {
        val out = mutableListOf<Note>()
        val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }
        val lastMonth = String.format(java.util.Locale.ROOT, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        if (settings.notifyMonthly && settings.lastMonthly != lastMonth) {
            val rows = entries.filter { V2Plan.dateOf(it.date).startsWith(lastMonth) }
            if (rows.isNotEmpty()) {
                val money = WorkPay.totals(rows).entries.joinToString(" · ") { (c, v) -> com.example.data.WorkMoney.format(v, c) }
                out += Note("monthly", "הסיכום של ${com.example.ui.kis.KisFormat.MONTHS[cal.get(Calendar.MONTH)]} מוכן",
                    "${rows.size} משמרות · ${com.example.data.WorkShareText.hours(rows.sumOf { it.hours })} שעות" + if (money.isNotEmpty()) " · $money" else "", lastMonth)
            }
        }
        val missing = WorkPay.pending(entries).filter { it.date <= now }
        if (settings.notifyRate && missing.isNotEmpty() && now - settings.lastRate >= WEEK) {
            out += Note("rate", if (missing.size == 1) "משמרת אחת מחכה לתעריף" else "${missing.size} משמרות מחכות לתעריף", "אפשר להשלים את כולן יחד מההיסטוריה")
        }
        return out
    }

    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** The whole daily job: record finished plans, then send what is due unless it is quiet time (then retry after it). */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Int {
        // Plans live in the on-device (guest) data; with a signed-in account the app shows other data, so stay idle.
        if (BuildConfig.ACCOUNTS_ENABLED && runCatching { com.example.api.AuthManager.currentUser.value != null }.getOrDefault(false)) return 0
        val dao = com.example.data.WorkAccountScope(null).database(context).workDao()
        val settings = PlanSettings(context)
        catchUp(dao, settings, now)
        val notes = due(dao.getEntriesList(), settings, now)
        if (notes.isEmpty() || !canNotify(context)) return 0
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val wait = V2Rules.quietDelay(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE), settings.quiet, settings.quietFrom, settings.quietTo)
        if (wait != null) {
            // Never dropped: run again right when quiet time ends.
            val end = Calendar.getInstance().apply { timeInMillis = now; val m = V2Rules.toMinutes(wait); set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60); set(Calendar.SECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(AFTER_QUIET, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<PlanWorker>().setInitialDelay(end - now, TimeUnit.MILLISECONDS).build())
            }
            return 0
        }
        channel(context)
        notes.forEach { n ->
            val action = if (n.kind == "monthly") ACTION_MONTH else ACTION_RATES
            // SINGLE_TOP: an open app receives it in onNewIntent and keeps its drafts instead of being recreated.
            val open = PendingIntent.getActivity(context, n.kind.hashCode(), Intent(context, MainActivity::class.java).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val note = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_kis).setColor(0xFF5145E8.toInt()).setContentTitle(n.title)
                .setContentText(n.text).setContentIntent(open).setAutoCancel(true).build()
            runCatching { NotificationManagerCompat.from(context).notify(if (n.kind == "monthly") 2001 else 2002, note) }
            settings.addRecent(PlanSettings.Sent(n.kind, n.title, n.text, now, n.month))
            if (n.kind == "monthly") settings.lastMonthly = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }
                .let { String.format(java.util.Locale.ROOT, "%04d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1) }
            else settings.lastRate = now
        }
        return notes.size
    }

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "תזכורות וסיכומים", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "סיכום חודשי ותזכורת למשמרות בלי תעריף"
            })
        }
    }
}

class PlanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        PlanJobs.run(applicationContext); Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PlanWorker", "Daily plan job failed", e)
        // A lasting failure is not retried forever; tomorrow's run tries again.
        if (runAttemptCount >= 3) Result.failure() else Result.retry()
    }
}
