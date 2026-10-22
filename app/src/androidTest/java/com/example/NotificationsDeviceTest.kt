package com.example

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.ShiftSeries
import com.example.data.WorkAccountScope
import com.example.data.WorkEntry
import com.example.domain.V2Plan
import com.example.domain.V2Rules
import com.example.ui.WorkUpdates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Calendar

/**
 * 2.0.4 on the signed Release, after the update gate (synthetic data only, removed again): the notifications really
 * reach the phone's notification shade — the monthly summary, a weekly-plan shift that ended (and its "לא עבדתי" button
 * really removes it), and a new version. Screenshots of the opened shade are kept for review.
 */
class NotificationsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val dao = WorkAccountScope(null).database(context).workDao()

    private fun active(title: String) = manager.activeNotifications.firstOrNull { it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() == title }
    private fun waitFor(what: String, ms: Long = 8_000, check: () -> Boolean) {
        val until = System.currentTimeMillis() + ms
        while (!check()) { assertTrue("timed out: $what", System.currentTimeMillis() < until); Thread.sleep(150) }
    }
    private fun shade(name: String) {
        automation.executeShellCommand("cmd statusbar expand-notifications").close()
        Thread.sleep(1_500)
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        automation.executeShellCommand("cmd statusbar collapse").close()
        Thread.sleep(500)
    }

    @Test fun notificationsReachTheShadeAndTheirButtonsWork() = runBlocking {
        assertTrue("the gate grants the notification permission", PlanJobs.canNotify(context))
        val settings = PlanSettings(context)
        val saved = listOf(settings.quiet, settings.notifyMonthly, settings.notifyPlans, settings.notifyUpdates, settings.autoConfirm)
        val savedMonthly = settings.lastMonthly
        val savedUpdate = settings.notifiedUpdate
        val before = dao.getEntriesList().size
        val seriesId = "notify-check-" + System.nanoTime()
        val addedIds = mutableListOf<Int>()
        try {
            settings.quiet = false; settings.notifyMonthly = true; settings.notifyPlans = true; settings.notifyUpdates = true

            // 1. The monthly summary, as it arrives on the 30th at 20:00 (here: a moment past this month's).
            val moment = PlanJobs.monthlyMoment(Calendar.getInstance())
            addedIds += dao.insertEntry(WorkEntry(category = "בדיקת התראה", date = moment - 86_400_000L, isTimeRange = false, hours = 3.0, hourlyRate = 40.0,
                totalEarnings = 120.0)).toInt()
            settings.lastMonthly = ""
            // A moment in the future must not record anyone's real plan days ahead of time.
            settings.autoConfirm = false
            PlanJobs.run(context, moment + 60_000L)
            settings.autoConfirm = true
            val monthTitle = "${com.example.ui.kis.KisFormat.MONTHS[Calendar.getInstance().apply { timeInMillis = moment }.get(Calendar.MONTH)]} שלך בכיס מוכן"
            waitFor("monthly summary") { active(monthTitle) != null }
            shade("v2-notify-monthly")
            manager.cancel(PlanJobs.MONTHLY_ID)

            // 2. A weekly-plan shift that ended an hour ago: recorded, told once, and "לא עבדתי" removes it.
            val now = System.currentTimeMillis()
            val end = Calendar.getInstance().apply { timeInMillis = now - 60 * 60_000L }
            val start = (end.clone() as Calendar).apply { add(Calendar.MINUTE, -30) }
            fun hhmm(c: Calendar) = String.format(java.util.Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            val date = V2Plan.dateOf(start.timeInMillis)
            dao.upsertSeries(ShiftSeries(seriesId, "בדיקת התראה", V2Plan.mask((0..6).toSet()), hhmm(start), hhmm(end), 40.0, "₪", date,
                V2Rules.civilFromDays(V2Rules.daysFromCivil(date))))
            PlanJobs.run(context, now)
            val key = "$seriesId@$date"
            waitFor("plan note") { active("נרשמה משמרת · בדיקת התראה") != null }
            assertTrue(dao.getEntriesList().any { it.seriesKey == key })
            shade("v2-notify-plan")
            val note = active("נרשמה משמרת · בדיקת התראה")!!.notification
            assertEquals("לא עבדתי", note.actions.single().title.toString())
            note.actions.single().actionIntent.send()
            waitFor("'לא עבדתי' removes the recorded day") { runBlocking { dao.getEntriesList().none { it.seriesKey == key } } }
            waitFor("the note goes away") { active("נרשמה משמרת · בדיקת התראה") == null }
            assertTrue(dao.getExceptions().single { it.seriesId == seriesId && it.date == date }.cancelled)

            // 3. A new version (a synthetic, never-downloaded one): once, with the update button.
            val fake = WorkUpdates.Release(WorkUpdates.installedVersion(context) + 1000, "9.9.9-בדיקה",
                "https://github.com/sholi2157-dev/Salary-calculation/releases/download/never/none.apk", "0".repeat(64), "בדיקה של התראת גרסה חדשה")
            settings.notifiedUpdate = 0
            assertTrue(AppUpdateJobs.notifyIfNew(context, fake, System.currentTimeMillis()))
            waitFor("update note") { active("גרסה 9.9.9-בדיקה זמינה") != null }
            shade("v2-notify-update")
            assertFalse("never twice for the same version", AppUpdateJobs.notifyIfNew(context, fake, System.currentTimeMillis()))
            manager.cancel(AppUpdateJobs.NOTIFICATION_ID)
        } finally {
            dao.getEntriesList().filter { it.id in addedIds || it.seriesKey?.startsWith("$seriesId@") == true }.forEach { dao.deleteEntry(it) }
            dao.deleteSeries(seriesId)
            settings.quiet = saved[0]; settings.notifyMonthly = saved[1]; settings.notifyPlans = saved[2]; settings.notifyUpdates = saved[3]; settings.autoConfirm = saved[4]
            settings.lastMonthly = savedMonthly; settings.notifiedUpdate = savedUpdate
            manager.cancelAll()
            assertEquals(before, dao.getEntriesList().size)
        }
    }
}
