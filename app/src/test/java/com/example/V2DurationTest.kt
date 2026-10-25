package com.example

import com.example.data.WorkEntry
import com.example.data.WorkPay
import com.example.ui.kis.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class V2DurationTest {
    private fun draft(hours: String) = EntryDraft(mode = EntryMode.HOURS, category = "עבודה", hours = hours, rate = "60")
    @Test fun durationDigitsEditMinutesWithoutChangingHoursAndAllow24Hours() {
        assertEquals("1340", DurationInput.digits("13:40"))
        val changed = typeTime("1340", 3, "5", 24)
        assertEquals("13:45", DurationInput.parseDigits(changed.first))
        assertEquals("24:00", DurationInput.parseDigits("2400"))
        assertNull(DurationInput.parseDigits("2401"))
        assertNull(parseTime("2400"))
    }
    /** The add page's typing rules: a full time takes more digits only on its last digit; an impossible hour is refused. */
    @Test fun aFullTimeChangesOnlyItsLastDigitAndAnImpossibleHourIsRefused() {
        assertEquals("1432" to 4, typeTime("····", 0, "1432"))
        assertEquals("1435" to 4, typeTime("1432", 4, "5"))
        assertEquals("1437" to 4, typeTime("1432", 4, "57"))
        assertEquals("1·32" to 1, typeTime("1432", 2, "⌫"))
        assertNull(typeTimeOrNull("2030", 1, "5"))
        assertEquals("2030" to 1, typeTime("2030", 1, "5"))
        assertEquals("0930" to 2, typeTime("1430", 0, "9"))
        assertEquals("2030" to 1, typeTime("1530", 0, "2"))
        assertEquals("1407" to 4, typeTime("1432", 2, "7"))
        assertEquals("2400", typeTime("····", 0, "2400", 24).first)
    }
    @Test fun manualMinutesAreNotDecimalFractions() {
        val d = draft(DurationInput.edit("1.40"))
        assertEquals("1:40", d.hours)
        assertEquals(100, d.check().minutes)
        assertEquals(100.0, d.check().total!!, 0.000001)
        assertEquals(100.0, d.toEntry().totalEarnings, 0.000001)
        assertEquals(100.0 / 60, d.toEntry().hours, 0.000001)
        assertEquals(40, draft(DurationInput.edit("0,40")).check().minutes)
        for (bad in listOf("1:75", "1:4", "24:01", "0:00", "1::40")) assertFalse(bad, draft(bad).check().canSave)
        assertTrue(draft("24:00").check().canSave)
    }
    @Test fun modeSwitchesKeepMinutesWithoutRoundingOrStaleHours() {
        val clock = draft("").copy(mode = EntryMode.CLOCK, start = "09:00", end = "10:55", breakMin = 15)
        val manual = clock.withMode(EntryMode.HOURS)
        assertEquals("1:40", manual.hours)
        assertEquals(100.0, manual.toEntry().totalEarnings, 0.0)
        val group = manual.withMode(EntryMode.GROUP)
        assertTrue(group.groupByHours)
        assertEquals(100, group.check().minutes)
        assertEquals(100, group.withMode(EntryMode.CLOCK).check().minutes)
        assertEquals("2:40", clock.copy(hours = "8", end = "11:55").withMode(EntryMode.HOURS).hours)
    }
    @Test fun oldDecimalsAndUnrelatedEditsKeepExactValues() {
        assertEquals(1.4, DurationInput.hours("1.40")!!, 0.0)
        assertEquals("1:24", DurationInput.display("1.40"))
        val e = WorkEntry(category = "עבודה", date = 1760000000000, isTimeRange = false, hours = 1.234567, hourlyRate = 33.3333, totalEarnings = 42.12345)
        val edited = EntryDraft.from(e).copy(notes = "הערה").toEntry(e)
        assertEquals(e.hours, edited.hours, 0.0)
        assertEquals(e.totalEarnings, edited.totalEarnings, 0.0)
        assertEquals(EntryDraft.from(e).hours, EntryDraft.fromJson(EntryDraft.from(e).toJson()).hours)
    }
    @Test fun workerDurationsAndImportedPrecisionArePreserved() {
        val d = draft("1:40").copy(mode = EntryMode.GROUP, start = null, workers = listOf(WorkerDraft("דני", "1:40")))
        assertTrue(d.check().canSave)
        assertEquals(100.0 / 60, WorkPay.workers(d.toEntry())[0].hours, 0.000001)
        assertFalse(d.copy(workers = listOf(WorkerDraft("דני", "1:75"))).check().canSave)
        val e = d.toEntry().copy(groupWorkersJson = """[{"name":"דני","hours":1.234567,"custom":"keep"}]""")
        val edited = EntryDraft.from(e).let { it.copy(workers = it.workers.map { w -> w.copy(name = "דן") }) }.toEntry(e)
        assertEquals(1.234567, WorkPay.workers(edited)[0].hours, 0.0)
        assertEquals("keep", WorkPay.workers(edited)[0].source.getString("custom"))
    }
}
