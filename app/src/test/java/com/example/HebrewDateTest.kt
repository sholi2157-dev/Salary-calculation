package com.example

import com.example.data.HebrewDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/** The Hebrew date and the weekly portion match the shared fixture (same file as web-tests/fixtures/hebrew-dates.json). */
@RunWith(RobolectricTestRunner::class)
class HebrewDateTest {
    private val doc = JSONObject(javaClass.classLoader!!.getResource("hebrew-dates.json").readText())
    private fun millis(iso: String): Long { val (y, m, d) = iso.split("-").map { it.toInt() }
        return Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }.timeInMillis }

    @Test fun daysMatchTheFixture() {
        val days = doc.getJSONArray("days")
        for (i in 0 until days.length()) { val r = days.getJSONArray(i)
            assertEquals(r.getString(0), r.getString(1), HebrewDate.label(millis(r.getString(0)), withYear = false))
            assertEquals(r.getString(0), r.getString(2), HebrewDate.label(millis(r.getString(0)), withYear = true)) }
    }

    @Test fun weeksMatchTheFixture() {
        val weeks: JSONArray = doc.getJSONArray("weeks")
        for (i in 0 until weeks.length()) { val r = weeks.getJSONArray(i)
            assertEquals(r.getString(0), if (r.isNull(1)) null else r.getString(1), HebrewDate.portion(millis(r.getString(0)), israel = false))
            assertEquals(r.getString(0), if (r.isNull(2)) null else r.getString(2), HebrewDate.portion(millis(r.getString(0)), israel = true)) }
    }

    @Test fun ownerExample() {
        assertEquals("כ״ח חשוון (8/11)", HebrewDate.label(millis("2026-11-08"), withYear = false))
        assertEquals("ט״ו", HebrewDate.gematria(15)); assertEquals("תשפ״ז", HebrewDate.gematria(5787))
    }
}
