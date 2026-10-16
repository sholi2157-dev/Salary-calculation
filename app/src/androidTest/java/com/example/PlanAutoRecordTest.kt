package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.ShiftSeries
import com.example.data.WorkAccountScope
import com.example.domain.V2Plan
import com.example.domain.V2Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Stage C on the signed Release, after the update gate (runs last, synthetic plan only): a weekly plan whose
 * days already ended is recorded exactly once even when the app and the daily job record at the same moment,
 * and the planned days show on Home. Everything it adds is removed again.
 */
class PlanAutoRecordTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun endedPlanIsRecordedOnceAndPlannedDaysShow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = WorkAccountScope(null).database(context).workDao()
        val today = V2Plan.dateOf(System.currentTimeMillis())
        val day = V2Rules.daysFromCivil(today)
        val id = "device-check-" + System.nanoTime()
        val series = ShiftSeries(id, "בדיקת סדרה", V2Plan.mask((0..6).toSet()), "06:00", "06:30", 40.0, "₪",
            V2Rules.civilFromDays(day - 2), V2Rules.civilFromDays(day + 5))
        val before = runBlocking { dao.getEntriesList().size }
        try {
            runBlocking {
                dao.upsertSeries(series)
                val settings = PlanSettings(context)
                (1..4).map { async(Dispatchers.IO) { PlanJobs.catchUp(dao, settings) } }.awaitAll()
                PlanJobs.run(context)
                val mine = dao.getEntriesList().filter { it.seriesKey?.startsWith("$id@") == true }
                // The two days before today ended for sure; each is recorded once with its planned pay.
                assertEquals(mine.size, mine.map { it.seriesKey }.distinct().size)
                listOf(day - 2, day - 1).forEach { d -> assertTrue(mine.any { it.seriesKey == "$id@${V2Rules.civilFromDays(d)}" }) }
                mine.forEach { assertTrue(it.autoConfirmed); assertEquals(20.0, it.totalEarnings, 0.0); assertTrue(it.payKnown) }
            }
            ui.activityRule.scenario.recreate()
            ui.waitForIdle()
            ui.waitUntil(5_000) { ui.onAllNodesWithTag("next_up").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("next_up").performScrollTo().assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                java.io.File(context.getExternalFilesDir(null), "v2-plan-home.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            runBlocking {
                val mine = dao.getEntriesList().filter { it.seriesKey?.startsWith("$id@") == true }
                mine.forEach { dao.deleteEntry(it) }
                dao.deleteSeries(id)
                assertEquals(before, dao.getEntriesList().size)
            }
        }
    }
}
