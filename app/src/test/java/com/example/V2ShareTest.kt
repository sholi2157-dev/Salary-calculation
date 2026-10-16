package com.example

import com.example.data.WorkEntry
import com.example.data.WorkPay
import com.example.data.WorkShareText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * Every share/copy text against the shared expected outputs (v2-share-fixtures.json, docs/v2-sharing.md).
 * The website runs the same file, so both platforms produce the same messages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class V2ShareTest {
    private val doc: JSONObject by lazy {
        JSONObject(javaClass.classLoader!!.getResourceAsStream("v2-share-fixtures.json").bufferedReader().readText())
    }

    private fun day(iso: String): Long {
        val (y, m, d) = iso.split("-").map { it.toInt() }
        return Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }.timeInMillis
    }

    private fun entry(raw: JSONObject): WorkEntry {
        val o = JSONObject(doc.getJSONObject("entryDefaults").toString()).also { d -> raw.keys().forEach { d.put(it, raw.get(it)) } }
        fun dbl(k: String) = if (o.has(k) && !o.isNull(k)) o.getDouble(k) else null
        return WorkEntry(category = o.getString("category"), date = day(o.getString("date")), isTimeRange = o.getBoolean("isTimeRange"),
            startTime = o.optString("start").ifEmpty { null }, endTime = o.optString("end").ifEmpty { null },
            hours = o.getDouble("hours"), hourlyRate = o.getDouble("rate"), totalEarnings = o.getDouble("total"),
            isPaid = o.getBoolean("isPaid"), notes = o.getString("notes"), currency = o.getString("currency"),
            kind = o.getString("kind"), payKnown = o.getBoolean("payKnown"), bonus = o.getDouble("bonus"),
            isGroupShift = o.getBoolean("isGroupShift"), employerRate = dbl("employerRate"), workerRate = dbl("workerRate"),
            groupWorkersJson = o.optJSONArray("workers")?.toString() ?: "")
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun produce(c: JSONObject): String {
        val rows = c.getJSONArray("entries").objects().map(::entry)
        return when (c.getString("type")) {
            "personal" -> WorkShareText.personal(rows.single())
            "employer" -> WorkShareText.employer(rows.single())
            "worker" -> rows.single().let { WorkShareText.worker(it, WorkPay.workers(it)[c.getInt("worker")]) }
            "summary" -> WorkShareText.summary(rows, c.optString("workerName").ifEmpty { null })
            "excel" -> WorkShareText.excel(rows)
            "planned" -> c.getJSONObject("occurrence").let { o -> WorkShareText.planned(com.example.domain.V2Plan.Occurrence(o.getString("key"), o.getString("seriesId"),
                o.getString("category"), o.getString("date"), o.getString("start"), o.getString("end"), if (o.isNull("rate")) null else o.getDouble("rate"),
                o.getString("currency"), edited = false, hold = false, status = com.example.domain.V2Plan.Status.PLANNED)) }
            else -> error("unknown type ${c.getString("type")}")
        }
    }

    @Test fun everySharedTextMatchesTheContract() {
        val cases = doc.getJSONArray("cases").objects()
        assertTrue(cases.size >= 13)
        cases.forEach { c ->
            val text = produce(c)
            assertEquals(c.getString("name"), c.getString("expected"), text)
            c.optJSONArray("forbidden")?.let { f -> (0 until f.length()).forEach { assertFalse("${c.getString("name")} leaks ${f.getString(it)}", text.contains(f.getString(it))) } }
        }
    }

    @Test fun textsHaveNoHiddenDirectionMarksAndNeverChangeData() {
        doc.getJSONArray("cases").objects().forEach { c ->
            val rows = c.getJSONArray("entries").objects().map(::entry)
            val before = rows.map { it.copy() }
            val text = produce(c)
            assertFalse(c.getString("name"), text.any { it in '⁦'..'⁩' || it == '‏' || it == '‎' })
            assertEquals(before, rows)
        }
    }

    @Test fun hoursAreReadable() {
        assertEquals("8", WorkShareText.hours(8.0))
        assertEquals("7.3", WorkShareText.hours(7.1 + 0.2))
        assertEquals("2.97", WorkShareText.hours(2.97))
    }
}
