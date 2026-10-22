package com.example

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ShiftSeries
import com.example.data.WorkAccountScope
import com.example.data.WorkEntry
import com.example.domain.V2Plan
import com.example.domain.V2Rules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * 2.0.4 notifications, with synthetic data only: the monthly summary on the 30th (the last day of a shorter month) at
 * 20:00 about that month; a planned shift that ended ("נרשמה" with "לא עבדתי", or "עבדת?"); the buttons do what the
 * app's own buttons do; quiet hours hold everything back; nothing is told twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationJobsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db by lazy { WorkAccountScope(null).database(app) }
    private val dao get() = db.workDao()
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis
    private fun settings() = PlanSettings(app).apply { quiet = false }

    @Before fun clean() {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        app.getSharedPreferences("kis_plan", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun monthlySummaryIsOnThe30thOrTheLastDay() {
        assertEquals(at(2026, 10, 30, 20), PlanJobs.nextMonthlyAt(at(2026, 10, 22)))
        assertEquals(at(2026, 11, 30, 20), PlanJobs.nextMonthlyAt(at(2026, 10, 30, 20, 1)))
        assertEquals(at(2027, 2, 28, 20), PlanJobs.nextMonthlyAt(at(2027, 2, 1)))
        assertEquals(at(2028, 2, 29, 20), PlanJobs.nextMonthlyAt(at(2028, 2, 10)))
        // Due at 20:00 on the 30th, about this month; not before; not twice.
        assertNull(PlanJobs.monthlyDue(at(2026, 10, 30, 19, 59), "2026-09"))
        assertEquals("2026-10", PlanJobs.monthlyDue(at(2026, 10, 30, 20, 0), "2026-09"))
        assertEquals("2026-10", PlanJobs.monthlyDue(at(2026, 10, 31, 9), "2026-09"))
        assertNull(PlanJobs.monthlyDue(at(2026, 10, 31, 9), "2026-10"))
        // A phone that was off on the 30th still gets it in the first 3 days of the next month, and never later.
        assertEquals("2026-10", PlanJobs.monthlyDue(at(2026, 11, 2, 9), "2026-09"))
        assertNull(PlanJobs.monthlyDue(at(2026, 11, 2, 9), "2026-10"))
        assertNull(PlanJobs.monthlyDue(at(2026, 11, 5, 9), "2026-09"))
        assertEquals("2027-02", PlanJobs.monthlyDue(at(2027, 2, 28, 20), "2027-01"))
    }

    @Test fun monthlyNoteIsAboutDoneShiftsOfThatMonth() {
        val s = settings()
        val now = at(2026, 10, 30, 20, 5)
        val rows = listOf(
            WorkEntry(id = 1, category = "מסעדה", date = at(2026, 10, 3, 17), hours = 5.0, hourlyRate = 50.0, totalEarnings = 250.0, currency = "₪"),
            WorkEntry(id = 2, category = "שיעורים", date = at(2026, 10, 12, 9), hours = 2.0, hourlyRate = 30.0, totalEarnings = 60.0, currency = "$"),
            // Later today and next month: not part of the month yet.
            WorkEntry(id = 3, category = "מסעדה", date = at(2026, 10, 30, 22), hours = 5.0, hourlyRate = 50.0, totalEarnings = 250.0, currency = "₪"),
            WorkEntry(id = 4, category = "מסעדה", date = at(2026, 9, 29, 17), hours = 5.0, hourlyRate = 50.0, totalEarnings = 250.0, currency = "₪"))
        val note = PlanJobs.due(rows, s, now).single()
        assertEquals("monthly", note.kind); assertEquals("2026-10", note.month)
        assertEquals("אוקטובר שלך בכיס מוכן", note.title)
        assertTrue(note.text, note.text.startsWith("2 משמרות · 7 שעות · ₪250 · $60"))
        // An empty month sends nothing; switched off sends nothing.
        assertTrue(PlanJobs.due(rows.filter { it.id == 4 }, s, now).isEmpty())
        s.notifyMonthly = false
        assertTrue(PlanJobs.due(rows, s, now).isEmpty())
    }

    @Test fun anEndedPlanDayIsToldOnceAndOnlyWhileFresh() {
        val s = settings()
        val now = at(2026, 10, 22, 22, 30)
        fun row(id: Int, date: String, start: String, end: String, auto: Boolean = true) = WorkEntry(id = id, category = "מסעדה", date = V2Plan.startMillis(date, start),
            isTimeRange = true, startTime = start, endTime = end, hours = 5.0, hourlyRate = 50.0, totalEarnings = 250.0, currency = "₪", seriesKey = "S@$date", autoConfirmed = auto)
        val fresh = row(1, "2026-10-22", "17:00", "22:00")
        val old = row(2, "2026-10-20", "17:00", "22:00").copy(seriesKey = "S@2026-10-20")
        val manual = row(3, "2026-10-21", "17:00", "22:00", auto = false).copy(seriesKey = "S@2026-10-21")
        val notes = PlanJobs.planNotes(listOf(fresh, old, manual), emptyList(), s, now)
        assertEquals(1, notes.size)
        assertEquals("נרשמה משמרת · מסעדה", notes[0].title)
        assertEquals(1, notes[0].entryId)
        assertTrue(notes[0].text, notes[0].text.contains("17:00–22:00") && notes[0].text.endsWith("₪250"))
        s.markNotified(listOf("S@2026-10-22"))
        assertTrue(PlanJobs.planNotes(listOf(fresh), emptyList(), s, now).isEmpty())
        // Auto-record off: the ended day asks "עבדת?"; a held day (the user said wait) is not asked about.
        val o = V2Plan.Occurrence("T@2026-10-22", "T", "שיעורים", "2026-10-22", "18:00", "20:00", 40.0, "₪", false, false, V2Plan.Status.AWAITING)
        val asked = PlanJobs.planNotes(emptyList(), listOf(o, o.copy(key = "T@x", hold = true)), s, now).single()
        assertEquals("המשמרת הסתיימה · שיעורים", asked.title); assertNull(asked.entryId); assertTrue(asked.text.endsWith("עבדת?"))
        // Many at once become one note; switched off, none.
        val many = (1..4).map { o.copy(key = "M@$it") }
        assertEquals("plans", PlanJobs.planNotes(emptyList(), many, s, now).single().kind)
        s.notifyPlans = false
        assertTrue(PlanJobs.planNotes(emptyList(), many, s, now).isEmpty())
    }

    @Test fun nextWakeIsTheNextPlanEndOrTheMonthlySummary() {
        val s = settings()
        val now = at(2026, 10, 22, 12)
        val o = V2Plan.Occurrence("S@2026-10-22", "S", "מסעדה", "2026-10-22", "17:00", "22:00", 50.0, "₪", false, false, V2Plan.Status.PLANNED)
        assertEquals(at(2026, 10, 22, 22, 1), PlanJobs.nextWake(listOf(o), s, now))
        assertEquals(at(2026, 10, 30, 20), PlanJobs.nextWake(emptyList(), s, now))
        s.notifyMonthly = false
        assertNull(PlanJobs.nextWake(emptyList(), s, now))
    }

    /** The real job on a synthetic plan: records the day, posts "נרשמה", and "לא עבדתי" removes it and skips the day for good. */
    @Test fun theJobPostsAndTheButtonsWork() = runBlocking {
        val now = System.currentTimeMillis()
        val today = V2Plan.dateOf(now)
        val day = V2Rules.daysFromCivil(today)
        // A plan that ended an hour ago (yesterday 23:00–00:00 would cross midnight; keep it simple: a short one ending now − 1h).
        val end = Calendar.getInstance().apply { timeInMillis = now - 60 * 60_000L }
        val start = (end.clone() as Calendar).apply { add(Calendar.MINUTE, -30) }
        fun hhmm(c: Calendar) = String.format(java.util.Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val date = V2Plan.dateOf(start.timeInMillis)
        dao.upsertSeries(ShiftSeries("N", "בדיקה", V2Plan.mask((0..6).toSet()), hhmm(start), hhmm(end), 40.0, "₪", date, V2Rules.civilFromDays(day + 3)))
        settings()
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val sent = PlanJobs.run(app, now)
        assertTrue("one plan note (and no monthly yet)", sent >= 1)
        val key = "N@$date"
        val row = dao.getEntriesList().single { it.seriesKey == key }
        val manager = app.getSystemService(NotificationManager::class.java)
        val posted = Shadows.shadowOf(manager).allNotifications
        val note = posted.single { it.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() == "נרשמה משמרת · בדיקה" }
        assertEquals("לא עבדתי", note.actions.single().title.toString())
        assertTrue(PlanSettings(app).recent.any { it.kind == "plan" })
        // Run again: nothing new to tell.
        assertEquals(0, PlanJobs.run(app, now + 60_000L))
        // "לא עבדתי": the shift goes, the day is skipped and is not recorded again.
        PlanActionReceiver.apply(app, PlanActionReceiver.ACTION_NOT_WORKED, key, row.id)
        assertTrue(dao.getEntriesList().none { it.seriesKey == key })
        assertTrue(dao.getExceptions().single { it.seriesId == "N" && it.date == date }.cancelled)
        PlanJobs.catchUp(dao, PlanSettings(app), now + 120_000L)
        assertTrue(dao.getEntriesList().none { it.seriesKey == key })
        // "עבדתי" with auto-record off records it once, as planned.
        dao.upsertException(dao.getExceptions().single { it.seriesId == "N" && it.date == date }.copy(cancelled = false, hold = true))
        PlanActionReceiver.apply(app, PlanActionReceiver.ACTION_WORKED, key, -1)
        PlanActionReceiver.apply(app, PlanActionReceiver.ACTION_WORKED, key, -1)
        val recorded = dao.getEntriesList().filter { it.seriesKey == key }
        assertEquals(1, recorded.size); assertFalse(recorded[0].autoConfirmed); assertEquals(20.0, recorded[0].totalEarnings, 0.0)
    }

    @Test fun quietHoursHoldTheNotesBack() = runBlocking {
        val s = PlanSettings(app).apply { quiet = true; quietFrom = "00:00"; quietTo = "23:59" }
        assertNotNull(PlanJobs.quietEnd(s, System.currentTimeMillis()))
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        dao.insertEntry(WorkEntry(category = "מסעדה", date = System.currentTimeMillis() - 3_600_000L, hours = 1.0, hourlyRate = 0.0, totalEarnings = 0.0, payKnown = false))
        s.notifyRate = true
        assertEquals(0, PlanJobs.run(app))
        assertTrue(Shadows.shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }
}
