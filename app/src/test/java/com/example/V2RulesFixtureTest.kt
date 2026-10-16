package com.example

import com.example.domain.V2Rules
import com.example.domain.V2Rules.BonusTarget
import com.example.domain.V2Rules.Draft
import com.example.domain.V2Rules.Mode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Asserts the Kotlin rules against cases generated from the approved 2.0 prototype engine
 * (scripts/v2/make-rule-fixtures.cjs). Web tests use the same file, so the platforms cannot drift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class V2RulesFixtureTest {
    private val cases: JSONObject by lazy {
        JSONObject(javaClass.classLoader!!.getResourceAsStream("v2-rules.json").bufferedReader().readText())
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else getDouble(key)
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    @Test fun roundedEndMatchesPrototype() {
        val list = cases.getJSONArray("roundedEnd").objects()
        assertTrue(list.isNotEmpty())
        list.forEach { assertEquals(it.toString(), it.getString("expected"), V2Rules.roundedEnd(it.getInt("nowMin"))) }
    }

    @Test fun spanAndOvernightMatchPrototype() {
        cases.getJSONArray("span").objects().forEach {
            val r = V2Rules.span(it.getString("start"), it.getString("end"), it.getInt("breakMin"))
            assertEquals(it.toString(), it.getInt("minutes"), r.minutes)
            assertEquals(it.toString(), it.getBoolean("overnight"), r.overnight)
            assertEquals(it.toString(), it.getBoolean("invalid"), r.invalid)
            assertEquals(it.toString(), it.getBoolean("long"), r.long)
        }
    }

    @Test fun payEvaluationMatchesPrototypeForEveryEntryType() {
        val list = cases.getJSONArray("pay").objects()
        assertTrue(list.size >= 15)
        list.forEach { c ->
            val i = c.getJSONObject("input")
            val mode = Mode.valueOf(i.getString("mode").uppercase())
            val draft = Draft(
                mode = mode,
                start = i.optStringOrNull("start"),
                end = i.optStringOrNull("end"),
                breakMinutes = i.optInt("breakMin", 0),
                hours = i.optStringOrNull("hours") ?: "",
                rate = i.optDoubleOrNull("rate"),
                rateUnknown = i.optBoolean("rateUnknown", false),
                bonus = i.optDouble("bonus", 0.0),
                bonusTarget = BonusTarget.valueOf(i.optString("bonusTarget", "me").uppercase()),
                amount = i.optStringOrNull("amount") ?: "",
                amountUnknown = i.optBoolean("amountUnknown", false),
            )
            val r = V2Rules.evaluate(draft)
            val label = c.toString()
            assertEquals(label, c.optDoubleOrNull("total"), r.total)
            assertEquals(label, c.getDouble("bonus"), r.bonus, 0.0)
            assertEquals(label, if (c.isNull("minutes")) null else c.getInt("minutes"), r.minutes)
            assertEquals(label, c.getBoolean("hasErrors"), !r.canSave)
        }
    }

    @Test fun unknownPayIsNeverZero() {
        val r = V2Rules.evaluate(Draft(mode = Mode.CLOCK, start = "18:00", end = "01:00", rateUnknown = true, bonus = 40.0))
        assertEquals(null, r.total)
        assertTrue(r.canSave)
    }

    @Test fun quietHoursDelayInsteadOfDropping() {
        cases.getJSONArray("quiet").objects().forEach {
            val enabled = !it.optBoolean("off", false)
            val got = V2Rules.quietDelay(it.getInt("atMin"), enabled, it.getString("from"), it.getString("to"))
            assertEquals(it.toString(), it.optStringOrNull("deliverAt"), got)
        }
    }

    @Test fun seriesDatesMatchPrototype() {
        cases.getJSONArray("seriesDates").objects().forEach {
            val days = it.getJSONArray("days").let { a -> (0 until a.length()).map(a::getInt).toSet() }
            val expected = it.getJSONArray("dates").let { a -> (0 until a.length()).map(a::getString) }
            assertEquals(it.toString(), expected, V2Rules.seriesDates(days, it.getString("from"), it.getString("until")))
        }
    }

    @Test fun calendarArithmeticRoundTrips() {
        assertEquals(4, V2Rules.weekday("1970-01-01"))
        assertEquals(4, V2Rules.weekday("2026-10-15"))
        for (d in listOf("1970-01-01", "2000-02-29", "2024-02-29", "2026-12-31", "2027-03-01", "2100-03-01")) {
            assertEquals(d, V2Rules.civilFromDays(V2Rules.daysFromCivil(d)))
        }
    }

    @Test fun bulkRateNeverTouchesKnownAmountsOrJobs() {
        val c = cases.getJSONArray("bulkRate").getJSONObject(0)
        val records = c.getJSONArray("shifts").objects().map {
            V2Rules.PayRecord(
                id = it.getString("id"), isJob = it.optString("kind") == "job",
                start = it.optStringOrNull("start"), end = it.optStringOrNull("end"),
                breakMinutes = it.optInt("breakMin", 0), hours = it.optDoubleOrNull("hours"),
                rate = it.optDoubleOrNull("rate"), bonus = it.optDouble("bonus", 0.0),
            )
        }
        val r = V2Rules.bulkRate(records, c.getDouble("rate"))
        val expected = c.getJSONArray("updated").objects().map { it.getString("id") to it.getDouble("total") }
        assertEquals(expected, r.updated)
        assertEquals(c.getInt("skipped"), r.skipped)
    }
}
