package com.example

import com.example.domain.V2Summary
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Summaries: the shared expected results (v2-summary.json), asserted the same way by the website. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class V2SummaryTest {
    private val doc by lazy { JSONObject(javaClass.classLoader!!.getResourceAsStream("v2-summary.json").bufferedReader().readText()) }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.int(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun row(o: JSONObject) = V2Summary.Row(o.getString("date"), o.str("start"), o.str("end"), o.getDouble("hours"), o.getString("kind") == "job",
        o.getBoolean("payKnown"), o.getDouble("total"), o.getString("currency"), o.getBoolean("paid"), o.getString("category"))

    @Test fun summariesMatchTheContract() {
        for (c in doc.getJSONArray("cases").objects()) {
            val name = c.getString("name")
            val got = V2Summary.summary(c.getJSONArray("rows").objects().map(::row), c.getString("scope"), c.getString("today"))
            val w = c.getJSONObject("expected")
            assertEquals(name, w.getInt("count"), got.count)
            assertEquals(name, w.getDouble("hours"), got.hours, 0.0)
            assertEquals(name, w.getJSONArray("currencies").objects().map { V2Summary.Currency(it.getString("currency"), it.getDouble("total"), it.getDouble("paid"),
                it.getDouble("unpaid"), it.dbl("perHour")) }, got.currencies)
            assertEquals(name, w.getInt("pending"), got.pending)
            assertEquals(name, (0 until 7).map { w.getJSONArray("days").getDouble(it) }, got.days)
            assertEquals(name, (0 until 24).map { w.getJSONArray("hourMinutes").getInt(it) }, got.hourMinutes)
            assertEquals(name, w.getJSONArray("places").objects().map { V2Summary.Place(it.getString("name"), it.getDouble("hours")) }, got.places)
            assertEquals(name, if (w.isNull("longest")) null else w.getJSONObject("longest").let { V2Summary.Longest(it.getString("date"), it.str("start"), it.str("end"), it.getDouble("hours")) }, got.longest)
            assertEquals(name, w.getInt("nights"), got.nights)
            assertEquals(name, w.int("bestDay"), got.bestDay)
            assertEquals(name, w.int("peakHour"), got.peakHour)
            assertEquals(name, w.dbl("previousHours"), got.previousHours)
            assertEquals(name, w.int("trend"), got.trend)
            assertEquals(name, w.str("persona"), got.persona)
            assertEquals(name, if (w.isNull("fun")) null else w.getJSONObject("fun").let { V2Summary.Fun(it.getString("key"), it.getLong("count")) }, got.comparison)
            assertEquals(name, w.getJSONArray("insights").objects().map { i ->
                when (i.getString("kind")) {
                    "unpaid" -> V2Summary.Insight.Unpaid(i.getJSONArray("amounts").objects().map { it.getString("currency") to it.getDouble("amount") })
                    "pending" -> V2Summary.Insight.Pending(i.getInt("count"))
                    "trend" -> V2Summary.Insight.Trend(i.getInt("percent"), i.getString("previousMonth"))
                    else -> V2Summary.Insight.Day(i.getInt("day"))
                }
            }, got.insights)
            got.persona?.let { assertNotNull(name, V2Summary.PERSONAS[it]) }
            got.comparison?.let { assertNotNull(name, V2Summary.FUN[it.key]) }
        }
    }

    @Test fun hourProfileFollowsAShiftAcrossMidnight() {
        val p = V2Summary.hourProfile(listOf(V2Summary.Row("2026-10-01", "22:30", "01:15", 2.75, false, true, 0.0, "₪", false, "x")))
        assertEquals(listOf(60, 15), p.subList(0, 2)); assertEquals(30, p[22]); assertEquals(60, p[23]); assertEquals(165, p.sum())
    }
}
