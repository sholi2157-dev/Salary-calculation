package com.example

import com.example.data.WorkEntry
import com.example.ui.kis.EntryDraft
import com.example.ui.kis.EntryMode
import com.example.ui.kis.WorkerDraft
import com.example.ui.kis.orderPlaces
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The add page: a shift is the user's own unless "קבוצה" is on; the same tabs then come with the people who worked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class V2PersonalGroupTest {
    private val clock = EntryDraft(category = "מסעדה", rate = "50", start = "09:00", end = "13:00")

    @Test fun theGroupButtonKeepsTheTabAndAddsPeople() {
        val g = clock.withTeam(true)
        assertTrue(g.team); assertEquals(EntryMode.GROUP, g.mode); assertEquals(EntryMode.CLOCK, g.tab); assertEquals("09:00", g.start)
        val byHours = g.withTab(EntryMode.HOURS)
        assertEquals(EntryMode.GROUP, byHours.mode); assertNull(byHours.start); assertEquals(EntryMode.HOURS, byHours.tab); assertTrue(byHours.hours.isNotEmpty())
        val back = byHours.withTab(EntryMode.CLOCK)
        assertEquals(EntryMode.GROUP, back.mode); assertEquals(EntryMode.CLOCK, back.tab); assertNotNull(back.start)
        val job = g.withTab(EntryMode.JOB)
        assertEquals(EntryMode.JOB, job.mode); assertTrue(job.team)
    }

    @Test fun aGroupNeedsPeopleAndLeavingItDropsThem() {
        val g = clock.withTeam(true)
        assertTrue(g.check().errors.contains("הוסף לפחות אדם אחד לקבוצה"))
        val withDani = g.copy(workers = listOf(WorkerDraft("דני", "4")))
        assertTrue(withDani.check().errors.toString(), withDani.check().canSave)
        assertTrue(withDani.toEntry().isGroupShift)
        val own = withDani.withTeam(false)
        assertFalse(own.team); assertEquals(EntryMode.CLOCK, own.mode); assertTrue(own.workers.isEmpty())
        assertFalse(own.toEntry().isGroupShift)
        // A job of the user's own has no team.
        val soloJob = EntryDraft(mode = EntryMode.JOB, category = "שיפוצים", amount = "300")
        assertTrue(soloJob.check().canSave); assertFalse(soloJob.toEntry().isGroupShift)
    }

    @Test fun aSavedGroupShiftOpensAsAGroup() {
        val e = WorkEntry(category = "מסעדה", date = 1_760_000_000_000L, isTimeRange = false, hours = 4.0, hourlyRate = 50.0, totalEarnings = 200.0,
            isGroupShift = true, groupWorkersJson = """[{"name":"נועה","hours":4.0,"isPaid":false}]""", id = 3)
        val d = EntryDraft.from(e)
        assertTrue(d.team); assertEquals(EntryMode.HOURS, d.tab)
        assertFalse(EntryDraft.from(e.copy(isGroupShift = false, groupWorkersJson = "")).team)
        assertEquals(d, EntryDraft.fromJson(d.toJson()))
        // A draft saved before 2.2 (no "team") opens as a group when it was one.
        val old = org.json.JSONObject(d.toJson()).apply { remove("team") }.toString()
        assertTrue(EntryDraft.fromJson(old).team)
    }

    @Test fun placesFollowTheUsersOrderAndNewOnesComeLast() {
        val places = listOf("א", "ב", "ג", "ד")
        assertEquals(listOf("ג", "א", "ב", "ד"), orderPlaces(places, listOf("ג", "א"), { it }))
        assertEquals(places, orderPlaces(places, emptyList(), { it }))
        assertEquals(listOf("ב", "א", "ג", "ד"), orderPlaces(places, listOf("ישן", "ב"), { it }))
    }
}
