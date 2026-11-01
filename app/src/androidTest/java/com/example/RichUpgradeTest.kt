package com.example

import android.content.Context
import android.database.Cursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.WorkDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 2.x-era data created inside the PUBLIC 2.2.1 build survives a direct update to the candidate (audit QA-02).
 * Uses only what is identical in every 2.x build: WorkDatabase.getDatabase, raw SQL on schema 11 and preference files,
 * because this test APK is compiled from the new code but runs inside whichever app version is installed.
 * Synthetic data only. Runs on a disposable emulator; never on a phone with real data.
 *
 * Stages (-e stage): seed (on 2.2.1: write data) · snapshot (on 2.2.1, after the app itself opened the data:
 * rich-before.json) · verify (on the candidate: rich-after.json, and the user data must be identical). The series lie wholly
 * in the past (beyond the 62-day catch-up window), so opening the app records nothing by itself.
 */
@RunWith(AndroidJUnit4::class)
class RichUpgradeTest {
    private val userTables = listOf("work_entries", "work_categories", "worker_directory", "shift_series", "series_exceptions", "work_local_preferences")
    /** Seeded preference keys per file; the candidate may add keys of its own, but never change or drop these. */
    private val seededPrefs = mapOf(
        "kis_ui" to listOf("theme", "haptics", "name", "lastName", "placeOrder", "color:שיעורים פרטיים במתמטיקה ופיזיקה לתיכון", "color:Tech Support"),
        "active_shift_prefs" to listOf("start_time", "category", "rate_double", "currency"),
    )

    @Test fun richDataSurvivesDirectUpdate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.aistudio.worktracker.qztvdw.distribution", context.packageName)
        val stage = InstrumentationRegistry.getArguments().getString("stage")!!
        val out = context.getExternalFilesDir(null)!!
        val sql = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).openHelper.writableDatabase
        when (stage) {
            "seed" -> {
                assertEquals("seed needs an empty store (fresh emulator)", 0, count(sql.query("SELECT COUNT(*) FROM work_entries")))
                seed(context) { sql.execSQL(it) }
            }
            "snapshot" -> File(out, "rich-before.json").writeText(snapshot(context) { sql.query(it) }.toString(1))
            "verify" -> {
                val before = JSONObject(File(out, "rich-before.json").readText())
                val after = snapshot(context) { sql.query(it) }
                File(out, "rich-after.json").writeText(after.toString(1))
                for (table in userTables - "work_local_preferences") {
                    val b0 = before.getJSONArray(table); val a0 = after.getJSONArray(table)
                    // Every row that existed is still there, unchanged; any row the update added is reported by name.
                    val added = (0 until a0.length()).map { a0.getJSONObject(it).toString() }.toSet() - (0 until b0.length()).map { b0.getJSONObject(it).toString() }.toSet()
                    for (i in 0 until b0.length()) assertTrue("row changed or removed in $table: ${b0.getJSONObject(i)}", (0 until a0.length()).any { a0.getJSONObject(it).toString() == b0.getJSONObject(i).toString() })
                    assertTrue("rows added to $table by the update: $added", added.isEmpty())
                }
                // The candidate may store new local preferences of its own; every one that existed must stay unchanged.
                val afterPrefs = (0 until after.getJSONArray("work_local_preferences").length()).map { after.getJSONArray("work_local_preferences").getJSONObject(it).toString() }.toSet()
                val lp = before.getJSONArray("work_local_preferences")
                for (i in 0 until lp.length()) assertTrue("local preference changed: ${lp.getJSONObject(i)}", lp.getJSONObject(i).toString() in afterPrefs)
                val bp = before.getJSONObject("prefs"); val ap = after.getJSONObject("prefs")
                for ((file, keys) in seededPrefs) for (key in keys)
                    assertEquals("pref $file/$key changed", bp.getJSONObject(file).opt(key)?.toString(), ap.getJSONObject(file).opt(key)?.toString())
                assertTrue("the active timer must still be running after the update", ap.getJSONObject("active_shift_prefs").has("start_time"))
            }
            else -> error("unknown stage $stage")
        }
    }

    private fun seed(context: Context, exec: (String) -> Unit) {
        val day = 86_400_000L
        val base = (System.currentTimeMillis() / day) * day - 3 * 3_600_000L // local-ish midnight; exact value is irrelevant
        fun q(s: String?) = if (s == null) "NULL" else "'" + s.replace("'", "''") + "'"
        val cats = listOf("עצמאי" to 40.0, "מסעדה" to 50.0, "שיעורים פרטיים במתמטיקה ופיזיקה לתיכון" to 120.0, "Tech Support" to 30.0, "הובלות" to 60.0, "אירועים" to 70.0)
        for ((name, rate) in cats.drop(1)) exec("INSERT INTO work_categories (name, defaultRate) VALUES (${q(name)}, $rate)")
        exec("INSERT INTO worker_directory (name) VALUES ('יוסי'), ('מנחם מענדל')")
        // category, dayOffset, isTimeRange, start, end, hours, rate, total, paid, notes, group, employer, worker, workersJson, currency, kind, payKnown, bonus, seriesKey, auto, passThrough
        data class R(val c: String, val d: Int, val tr: Boolean, val s: String?, val e: String?, val h: Double, val r: Double, val t: Double,
                     val paid: Boolean = false, val notes: String = "", val g: Boolean = false, val er: Double? = null, val wr: Double? = null,
                     val wj: String = "", val cur: String = "₪", val kind: String = "shift", val known: Boolean = true, val bonus: Double = 0.0,
                     val sk: String? = null, val auto: Boolean = false, val pass: Double = 0.0)
        val rows = listOf(
            R("מסעדה", 0, true, "11:00", "17:00", 6.0, 50.0, 300.0),
            R("מסעדה", 1, true, "22:00", "02:00", 3.5, 50.0, 175.0, notes = "לילה עם הפסקה של חצי שעה"),
            R("שיעורים פרטיים במתמטיקה ופיזיקה לתיכון", 2, false, null, null, 1.6666666666666667, 120.0, 200.0, notes = "1:40 שעות"),
            R("Tech Support", 3, false, null, null, 4.0, 30.0, 120.0, paid = true, cur = "$"),
            R("מסעדה", 4, false, null, null, 5.0, 0.0, 0.0, known = false),
            R("הובלות", 5, false, null, null, 0.0, 0.0, 850.0, kind = "job", notes = "הובלת דירה"),
            R("הובלות", 6, false, null, null, 2.0, 0.0, 0.0, kind = "job", known = false),
            R("אירועים", 7, true, "18:00", "23:00", 5.0, 70.0, 370.0, g = true, er = 70.0, wr = 50.0, bonus = 20.0, pass = 45.0,
                wj = """[{"name":"יוסי","hours":5.0,"rate":50.0,"paid":false},{"name":"מנחם מענדל","hours":4.0,"rate":50.0,"paid":true}]"""),
            R("מסעדה", 8, false, null, null, 1.234567, 34.123456, 42.12345, notes = "סכום מיובא חריג שאסור לחשב מחדש"),
            R("עצמאי", 9, false, null, null, 7.75, 40.0, 310.0, paid = true, notes = "x".repeat(300)),
            R("מסעדה", 10, true, "09:00", "17:00", 8.0, 50.0, 400.0, sk = "series-rich@2025-06-02", auto = true),
        )
        for (r in rows) exec(
            "INSERT INTO work_entries (category, date, isTimeRange, startTime, endTime, hours, hourlyRate, totalEarnings, isPaid, notes, createdAt, " +
                "isGroupShift, employerRate, workerRate, groupWorkersJson, currency, kind, payKnown, bonus, seriesKey, autoConfirmed, passThrough) VALUES (" +
                "${q(r.c)}, ${base - r.d * day}, ${if (r.tr) 1 else 0}, ${q(r.s)}, ${q(r.e)}, ${r.h}, ${r.r}, ${r.t}, ${if (r.paid) 1 else 0}, ${q(r.notes)}, ${base - r.d * day + 1000}, " +
                "${if (r.g) 1 else 0}, ${r.er ?: "NULL"}, ${r.wr ?: "NULL"}, ${q(r.wj)}, ${q(r.cur)}, ${q(r.kind)}, ${if (r.known) 1 else 0}, ${r.bonus}, ${q(r.sk)}, ${if (r.auto) 1 else 0}, ${r.pass})")
        exec("INSERT INTO shift_series (id, category, daysMask, startTime, endTime, hourlyRate, currency, fromDate, untilDate, createdAt) VALUES " +
            "('series-rich', 'מסעדה', 42, '09:00', '17:00', 50.0, '₪', '2025-06-01', '2025-08-31', ${base - 300 * day}), " +
            "('series-night', 'אירועים', 64, '22:00', '03:00', NULL, '₪', '2025-06-01', '2025-07-31', ${base - 290 * day})")
        exec("INSERT INTO series_exceptions (seriesId, date, cancelled, hold, startTime, endTime, rateOverridden, hourlyRate) VALUES " +
            "('series-rich', '2025-06-09', 1, 0, NULL, NULL, 0, NULL), ('series-rich', '2025-06-11', 0, 1, NULL, NULL, 0, NULL), " +
            "('series-night', '2025-06-07', 0, 0, '21:00', '02:30', 1, 65.0)")
        context.getSharedPreferences("kis_ui", Context.MODE_PRIVATE).edit()
            .putString("theme", "dark").putBoolean("haptics", false).putString("name", "בודק").putString("lastName", "סינתטי")
            .putString("placeOrder", JSONArray(listOf("Tech Support", "מסעדה")).toString())
            .putInt("color:שיעורים פרטיים במתמטיקה ופיזיקה לתיכון", 4).putInt("color:Tech Support", 6).commit()
        // An active timer started 90 minutes ago at a $ rate: it must survive the update and keep its start time.
        context.getSharedPreferences("active_shift_prefs", Context.MODE_PRIVATE).edit()
            .putLong("start_time", System.currentTimeMillis() - 90 * 60_000L).putString("category", "Tech Support")
            .putString("rate_double", "30.5").putString("currency", "$").commit()
    }

    private fun snapshot(context: Context, query: (String) -> Cursor): JSONObject {
        val out = JSONObject()
        for (table in userTables) {
            val rows = JSONArray()
            // A stable order that does not depend on rowid reuse; every column is kept with its stored type.
            query("SELECT * FROM $table ORDER BY 1, 2").use { c ->
                while (c.moveToNext()) rows.put(JSONObject().apply {
                    for (i in 0 until c.columnCount) put(c.getColumnName(i), when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).toString() // exact text, no JSON float rounding
                        else -> c.getString(i)
                    })
                })
            }
            out.put(table, rows)
        }
        out.put("prefs", JSONObject().apply {
            for (file in seededPrefs.keys) put(file, JSONObject().apply {
                for ((k, v) in context.getSharedPreferences(file, Context.MODE_PRIVATE).all) put(k, v?.toString() ?: JSONObject.NULL)
            })
        })
        return out
    }

    private fun count(c: Cursor) = c.use { it.moveToFirst(); it.getInt(0) }
}
