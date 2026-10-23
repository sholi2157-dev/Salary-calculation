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
import kotlinx.coroutines.launch

/**
 * Stage C settings. Kept on this device (like the theme) and deliberately not in the backup's local preferences:
 * older versions reject backups that carry preference names they do not know.
 */
class PlanSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kis_plan", Context.MODE_PRIVATE)
    var autoConfirm: Boolean
        get() = prefs.getBoolean("autoConfirm", true)
        set(v) { prefs.edit().putBoolean("autoConfirm", v).apply() }
    /** On by default since 2.0.4 (the owner wants it to arrive); it still needs the phone's notification permission. */
    var notifyMonthly: Boolean
        get() = prefs.getBoolean("notifyMonthly", true)
        set(v) { prefs.edit().putBoolean("notifyMonthly", v).apply() }
    var notifyRate: Boolean
        get() = prefs.getBoolean("notifyRate", false)
        set(v) { prefs.edit().putBoolean("notifyRate", v).apply() }
    /** A weekly-plan shift that just ended: "נרשמה" (with "לא עבדתי") or, without auto-record, "עבדת?". */
    var notifyPlans: Boolean
        get() = prefs.getBoolean("notifyPlans", true)
        set(v) { prefs.edit().putBoolean("notifyPlans", v).apply() }
    /** A new version on the update channel, once per version. */
    var notifyUpdates: Boolean
        get() = prefs.getBoolean("notifyUpdates", true)
        set(v) { prefs.edit().putBoolean("notifyUpdates", v).apply() }
    var quiet: Boolean
        get() = prefs.getBoolean("quiet", true)
        set(v) { prefs.edit().putBoolean("quiet", v).apply() }
    var quietFrom: String
        get() = prefs.getString("quietFrom", "22:00") ?: "22:00"
        set(v) { prefs.edit().putString("quietFrom", v).apply() }
    var quietTo: String
        get() = prefs.getString("quietTo", "07:00") ?: "07:00"
        set(v) { prefs.edit().putString("quietTo", v).apply() }
    /** "YYYY-MM" of the last monthly summary sent. */
    var lastMonthly: String
        get() = prefs.getString("lastMonthly", "") ?: ""
        set(v) { prefs.edit().putString("lastMonthly", v).apply() }
    var lastRate: Long
        get() = prefs.getLong("lastRate", 0L)
        set(v) { prefs.edit().putLong("lastRate", v).apply() }
    /** The newest version a notification was shown for. */
    var notifiedUpdate: Long
        get() = prefs.getLong("notifiedUpdate", 0L)
        set(v) { prefs.edit().putLong("notifiedUpdate", v).apply() }
    /** The one-time "רוצה לדעת מה חדש?" question before Android's permission dialog was shown. */
    var permissionAsked: Boolean
        get() = prefs.getBoolean("permissionAsked", false)
        set(v) { prefs.edit().putBoolean("permissionAsked", v).apply() }
    /** Plan days already told about (by a notification or by the app's own "נרשמה" message), newest last. */
    val notifiedKeys: Set<String> get() = prefs.getString("notifiedKeys", "")!!.split('\n').filter { it.isNotEmpty() }.toSet()
    fun markNotified(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val all = (prefs.getString("notifiedKeys", "")!!.split('\n').filter { it.isNotEmpty() } + keys).distinct().takeLast(200)
        prefs.edit().putString("notifiedKeys", all.joinToString("\n")).apply()
    }

    /** The reminders that were actually sent, newest first (at most [RECENT_MAX]), for "התראות אחרונות". */
    /** [opened]: a monthly summary whose recap was watched (until then it stays new on the bell). */
    data class Sent(val kind: String, val title: String, val text: String, val time: Long, val month: String, val opened: Boolean = false)
    val recent: List<Sent>
        get() = runCatching {
            val a = org.json.JSONArray(prefs.getString("recent", "[]"))
            (0 until a.length()).map { i -> a.getJSONObject(i).let { Sent(it.getString("kind"), it.getString("title"), it.getString("text"), it.getLong("time"), it.optString("month"), it.optBoolean("opened")) } }
        }.getOrDefault(emptyList())
    private fun saveRecent(all: List<Sent>) {
        val a = org.json.JSONArray().apply { all.take(RECENT_MAX).forEach { put(org.json.JSONObject().put("kind", it.kind).put("title", it.title).put("text", it.text).put("time", it.time).put("month", it.month).put("opened", it.opened)) } }
        prefs.edit().putString("recent", a.toString()).apply()
    }
    fun addRecent(sent: Sent) = saveRecent(listOf(sent) + recent)
    /** The bell's list: a monthly summary of a recap that has not opened (or never will, like before the first one) stays out. */
    fun notes(today: String = com.example.domain.V2Summary.today()): List<Sent> =
        recent.filter { it.kind != "monthly" || it.month.isEmpty() || com.example.domain.V2Summary.recapOpen(it.month, today) }
    /** A recap was watched: its summary is no longer new on the bell. */
    fun markOpened(scope: String) {
        val all = recent
        if (all.any { it.kind == "monthly" && it.month == scope && !it.opened }) saveRecent(all.map { if (it.kind == "monthly" && it.month == scope) it.copy(opened = true) else it })
    }
    /** This person's own seed for the story's words (made once, kept on this device). */
    val voiceSeed: String
        get() = prefs.getString("voiceSeed", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").take(8).also { prefs.edit().putString("voiceSeed", it).apply() }
    /** When the bell sheet was last opened: anything sent after it shows a dot on the bell. */
    var seenAt: Long
        get() = prefs.getLong("seenAt", 0L)
        set(v) { prefs.edit().putLong("seenAt", v).apply() }
    /** Anything new since the bell was opened, and a monthly summary until its recap is actually watched. */
    val unread: Boolean get() = notes().any { it.time > seenAt || (it.kind == "monthly" && !it.opened) }
    companion object { const val RECENT_MAX = 20 }
}

object PlanJobs {
    const val CHANNEL = "plan_reminders"
    const val ACTION_MONTH = "com.example.ACTION_OPEN_HISTORY"
    const val ACTION_RATES = "com.example.ACTION_COMPLETE_RATES"
    const val ACTION_PLANS = "com.example.ACTION_OPEN_PLANS"
    /** "YYYY-MM": the month a monthly-summary notification is about. */
    const val EXTRA_MONTH = "month"
    /** The monthly summary arrives on the 30th (the last day of a shorter month) at 20:00, about that month. */
    const val MONTHLY_DAY = 30
    const val MONTHLY_HOUR = 20
    private const val DAILY = "kis-plan-daily"
    private const val AFTER_QUIET = "kis-plan-after-quiet"
    private const val NEXT = "kis-plan-next"
    private const val WEEK = 7L * 24 * 60 * 60 * 1000
    /** A plan day is told about only shortly after it ended (a phone that was off for days gets no pile of old news). */
    private const val PLAN_WINDOW = 16L * 60 * 60 * 1000
    /** More plan days than this at once become one note. */
    private const val PLAN_SINGLE_MAX = 3
    const val MONTHLY_ID = 2001
    const val RATE_ID = 2002
    const val PLANS_ID = 2004
    fun planNotificationId(key: String) = 3000 + (key.hashCode() and 0x3FFF)
    /** How far back plans are shown and recorded. */
    const val LOOK_BACK_DAYS = 62

    /** One idempotent daily job, plus a wake-up at the next plan end or monthly summary (and a catch-up on every app start/resume). */
    fun schedule(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PlanWorker>(1, TimeUnit.DAYS).build())
        }
        scheduleNextAsync(context)
    }

    fun scheduleNextAsync(context: Context) {
        val app = context.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { scheduleNext(app) } }
    }

    /** The next moment something may be due: a planned shift's end (a minute after it) or the monthly summary. */
    suspend fun scheduleNext(context: Context, now: Long = System.currentTimeMillis()) {
        val dao = com.example.data.WorkAccountScope(null).database(context).workDao()
        val settings = PlanSettings(context)
        val series = dao.getSeries()
        val at = nextWake(if (series.isEmpty()) emptyList() else V2Plan.occurrences(series, dao.getExceptions(), dao.confirmedKeys().toSet(),
            V2Plan.dateOf(now - 86_400_000L), V2Plan.dateOf(now + 8 * 86_400_000L), now), settings, now) ?: return
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(NEXT, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PlanWorker>().setInitialDelay((at - now).coerceAtLeast(0), TimeUnit.MILLISECONDS).build())
        }
    }

    /** Pure: the earliest plan end still ahead (+1 minute) and the next monthly summary, whichever comes first. */
    fun nextWake(occurrences: List<V2Plan.Occurrence>, settings: PlanSettings, now: Long): Long? {
        val planEnd = occurrences.filter { it.status == V2Plan.Status.PLANNED }.map { V2Plan.endMillis(it.date, it.start, it.end) + 60_000L }
            .filter { it > now }.minOrNull()
        val monthly = if (settings.notifyMonthly) nextMonthlyAt(now) else null
        return listOfNotNull(planEnd, monthly).minOrNull()
    }

    private fun ym(cal: Calendar) = String.format(java.util.Locale.ROOT, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)

    /** The monthly summary moment of the month that [cal] is in: day 30 (or the month's last day) at [MONTHLY_HOUR]. */
    fun monthlyMoment(cal: Calendar): Long = (cal.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, MONTHLY_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_MONTH, minOf(MONTHLY_DAY, getActualMaximum(Calendar.DAY_OF_MONTH)))
    }.timeInMillis

    fun nextMonthlyAt(now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val thisMonth = monthlyMoment(cal)
        return if (now < thisMonth) thisMonth else monthlyMoment(cal.apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, 1) })
    }

    /**
     * The month whose summary is due now ("YYYY-MM"), or null: only while its recap is new (the 30th and 4 more days,
     * V2Summary.recapNew; never before the first one), so a phone that was off on the 30th still gets it, and never later.
     */
    fun monthlyDue(now: Long, lastSent: String): String? {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val month = com.example.domain.V2Summary.recapNew(V2Plan.dateOf(now)).month ?: return null
        if (month <= lastSent || (month == ym(cal) && now < monthlyMoment(cal))) return null
        return month
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

    /**
     * [month] is "YYYY-MM" for the monthly summary (the month it is about). A plan note carries its day [key] and,
     * when the day was recorded by itself, the [entryId] that "לא עבדתי" removes.
     */
    data class Note(val kind: String, val title: String, val text: String, val month: String = "", val key: String = "", val entryId: Int? = null,
                    val keys: List<String> = emptyList())

    private fun money(v: Double, currency: String) = com.example.ui.kis.KisFormat.money(v, currency)

    /** What is due now (pure; [run] sends and records it). */
    fun due(entries: List<WorkEntry>, settings: PlanSettings, now: Long): List<Note> {
        val out = mutableListOf<Note>()
        val month = if (settings.notifyMonthly) monthlyDue(now, settings.lastMonthly) else null
        if (month != null) {
            // Done shifts only (a shift later today or a planned one is not part of the month yet).
            val rows = entries.filter { it.date <= now && V2Plan.dateOf(it.date).startsWith(month) }
            if (rows.isNotEmpty()) {
                val totals = WorkPay.totals(rows).entries.sortedByDescending { it.value }.joinToString(" · ") { (c, v) -> money(v, c) }
                val name = com.example.ui.kis.KisFormat.MONTHS[month.substring(5).toInt() - 1]
                val count = if (rows.size == 1) "משמרת אחת" else "${rows.size} משמרות"
                out += Note("monthly", "$name שלך בכיס מוכן", "$count · ${com.example.data.WorkShareText.hours(rows.sumOf { it.hours })} שעות" +
                    (if (totals.isNotEmpty()) " · $totals" else "") + ". נגיעה לסיכום המלא", month)
            }
        }
        val missing = WorkPay.pending(entries).filter { it.date <= now }
        if (settings.notifyRate && missing.isNotEmpty() && now - settings.lastRate >= WEEK) {
            out += Note("rate", if (missing.size == 1) "משמרת אחת מחכה לתעריף" else "${missing.size} משמרות מחכות לתעריף", "אפשר להשלים את כולן יחד מההיסטוריה")
        }
        return out
    }

    private fun span(start: String, end: String) = "\u2066$start–$end\u2069"

    /**
     * Plan days that ended in the last [PLAN_WINDOW] and were not told about yet: recorded by themselves ("נרשמה", with
     * "לא עבדתי"), or waiting for the user when auto-record is off ("עבדת?"). Pure.
     */
    fun planNotes(entries: List<WorkEntry>, awaiting: List<V2Plan.Occurrence>, settings: PlanSettings, now: Long): List<Note> {
        if (!settings.notifyPlans) return emptyList()
        val told = settings.notifiedKeys
        fun recent(end: Long) = end <= now && end > now - PLAN_WINDOW
        val recorded = entries.filter { e ->
            val key = e.seriesKey
            key != null && e.autoConfirmed && key !in told && e.startTime != null && e.endTime != null &&
                recent(V2Plan.endMillis(V2Plan.dateOf(e.date), e.startTime, e.endTime))
        }.map { e ->
            Note("plan", "נרשמה משמרת · ${e.category}", "${com.example.ui.kis.dayLabel(e.date)} ${span(e.startTime!!, e.endTime!!)} · " +
                (if (e.payKnown) money(e.totalEarnings, e.currency) else "תעריף חסר"), key = e.seriesKey!!, entryId = e.id)
        }
        val waiting = awaiting.filter { it.status == V2Plan.Status.AWAITING && !it.hold && it.key !in told && recent(V2Plan.endMillis(it.date, it.start, it.end)) }
            .map { o -> Note("plan", "המשמרת הסתיימה · ${o.category}", "${com.example.ui.kis.dayLabel(V2Plan.startMillis(o.date, o.start))} ${span(o.start, o.end)} · עבדת?", key = o.key) }
        val all = recorded + waiting
        if (all.size <= PLAN_SINGLE_MAX) return all
        return listOf(Note("plans", "${all.size} משמרות קבועות הסתיימו",
            if (waiting.isEmpty()) "הן נרשמו לבד. אפשר לבדוק אותן בהיסטוריה" else "אפשר לאשר אותן במסך הבית", keys = all.map { it.key }))
    }

    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Quiet hours: the end of the current quiet time, or null when it is not quiet now. */
    fun quietEnd(settings: PlanSettings, now: Long): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val wait = V2Rules.quietDelay(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE), settings.quiet, settings.quietFrom, settings.quietTo) ?: return null
        return Calendar.getInstance().apply { timeInMillis = now; val m = V2Rules.toMinutes(wait); set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60); set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0); if (timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    }

    /** The whole job: record finished plans, then send what is due unless it is quiet time (then retry after it). */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Int {
        // Plans live in the on-device (guest) data; with a signed-in account the app shows other data, so stay idle.
        if (BuildConfig.ACCOUNTS_ENABLED && runCatching { com.example.api.AuthManager.currentUser.value != null }.getOrDefault(false)) return 0
        val dao = com.example.data.WorkAccountScope(null).database(context).workDao()
        val settings = PlanSettings(context)
        catchUp(dao, settings, now)
        val entries = dao.getEntriesList()
        val series = dao.getSeries()
        val awaiting = if (series.isEmpty() || settings.autoConfirm) emptyList() else V2Plan.occurrences(series, dao.getExceptions(), dao.confirmedKeys().toSet(),
            V2Plan.dateOf(now - PLAN_WINDOW), V2Plan.dateOf(now), now)
        val notes = due(entries, settings, now) + planNotes(entries, awaiting, settings, now)
        try {
            if (notes.isEmpty() || !canNotify(context)) return 0
            val end = quietEnd(settings, now)
            if (end != null) {
                // Never dropped: run again right when quiet time ends.
                runCatching {
                    WorkManager.getInstance(context).enqueueUniqueWork(AFTER_QUIET, ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<PlanWorker>().setInitialDelay(end - now, TimeUnit.MILLISECONDS).build())
                }
                return 0
            }
            notes.forEach { send(context, it, settings, now) }
            return notes.size
        } finally {
            scheduleNext(context, now)
        }
    }

    private fun open(context: Context, action: String, request: Int, month: String = ""): PendingIntent =
        // SINGLE_TOP: an open app receives it in onNewIntent and keeps its drafts instead of being recreated.
        PendingIntent.getActivity(context, request, Intent(context, MainActivity::class.java).setAction(action).putExtra(EXTRA_MONTH, month)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(context: Context, action: String, n: Note, id: Int): PendingIntent =
        PendingIntent.getBroadcast(context, (action + n.key).hashCode(), Intent(context, PlanActionReceiver::class.java).setAction(action)
            .putExtra(PlanActionReceiver.EXTRA_KEY, n.key).putExtra(PlanActionReceiver.EXTRA_ENTRY, n.entryId ?: -1).putExtra(PlanActionReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun send(context: Context, n: Note, settings: PlanSettings, now: Long) {
        channel(context)
        val id = when (n.kind) { "monthly" -> MONTHLY_ID; "rate" -> RATE_ID; "plans" -> PLANS_ID; else -> planNotificationId(n.key) }
        val tap = when (n.kind) {
            "monthly" -> open(context, ACTION_MONTH, id, n.month)
            "rate" -> open(context, ACTION_RATES, id)
            else -> open(context, ACTION_PLANS, id)
        }
        // Amounts stay off the lock screen: there only the title shows.
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_kis).setColor(0xFF5145E8.toInt()).setContentTitle(n.title).build()
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_kis).setColor(0xFF5145E8.toInt()).setContentTitle(n.title)
            .setContentText(n.text).setStyle(NotificationCompat.BigTextStyle().bigText(n.text)).setContentIntent(tap).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
        if (n.kind == "plan") {
            if (n.entryId == null) builder.addAction(0, "עבדתי", action(context, PlanActionReceiver.ACTION_WORKED, n, id))
            builder.addAction(0, "לא עבדתי", action(context, PlanActionReceiver.ACTION_NOT_WORKED, n, id))
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
        settings.addRecent(PlanSettings.Sent(n.kind, n.title, n.text, now, n.month))
        when (n.kind) {
            "monthly" -> settings.lastMonthly = n.month
            "rate" -> settings.lastRate = now
            "plan" -> settings.markNotified(listOf(n.key))
            else -> settings.markNotified(n.keys)
        }
    }

    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "תזכורות וסיכומים", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "הסיכום החודשי, משמרת קבועה שהסתיימה ותזכורת למשמרות בלי תעריף"
            })
        }
    }
}

/** "עבדתי" / "לא עבדתי" straight from a plan notification, without opening the app. */
class PlanActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val entryId = intent.getIntExtra(EXTRA_ENTRY, -1)
        val notificationId = intent.getIntExtra(EXTRA_ID, 0)
        val action = intent.action ?: return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                apply(context.applicationContext, action, key, entryId)
                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (e: Exception) {
                android.util.Log.e("PlanActionReceiver", "Plan action failed", e)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_WORKED = "com.example.ACTION_PLAN_WORKED"
        const val ACTION_NOT_WORKED = "com.example.ACTION_PLAN_NOT_WORKED"
        const val EXTRA_KEY = "key"
        const val EXTRA_ENTRY = "entryId"
        const val EXTRA_ID = "notificationId"

        /** The same changes the app's own buttons make; a day recorded twice or a shift edited meanwhile is never touched. */
        suspend fun apply(context: Context, action: String, key: String, entryId: Int) {
            val dao = com.example.data.WorkAccountScope(null).database(context).workDao()
            val seriesId = key.substringBeforeLast('@'); val date = key.substringAfterLast('@')
            when (action) {
                ACTION_NOT_WORKED -> {
                    // A recorded day goes (only if it is still the untouched automatic row), and the day is marked skipped.
                    if (entryId > 0) dao.undoAutoConfirm(listOf(entryId))
                    if (entryId <= 0 || dao.getEntriesList().none { it.seriesKey == key }) dao.markPlanDays(listOf(key), skipped = true)
                }
                ACTION_WORKED -> {
                    val series = dao.getSeries().filter { it.id == seriesId }
                    val o = V2Plan.occurrences(series, dao.getExceptions(), dao.confirmedKeys().toSet(), date, date, System.currentTimeMillis())
                        .firstOrNull { it.key == key } ?: return
                    dao.confirmOccurrences(listOf(V2Plan.toEntry(o, auto = false)))
                }
            }
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
