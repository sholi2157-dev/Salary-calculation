package com.example

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.WorkDatabase
import com.example.data.WorkEntry
import com.example.data.WorkSyncSchema
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrades a real RC16 (schema 8) database to 2.0 (schema 9).
 * The schema 8 tables are created from the exported Room schema of the delivered RC16 build,
 * filled with synthetic rows covering every RC16 record type, then opened by the 2.0 database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class V2MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun createVersion8(name: String) {
        val schema = JSONObject(File("schemas/com.example.data.WorkDatabase/8.json").readText()).getJSONObject("database")
        assertEquals(8, schema.getInt("version"))
        context.getDatabasePath(name).parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO work_categories (id, name, defaultRate) VALUES (1, 'מסעדה', 52.0), (2, 'שיעורים', 120.0)")
            db.execSQL("INSERT INTO worker_directory (id, name) VALUES (1, 'נועה')")
            fun entry(id: Int, values: ContentValues.() -> Unit) = db.insertOrThrow("work_entries", null, ContentValues().apply {
                put("id", id); put("category", "מסעדה"); put("date", 1_760_000_000_000L + id); put("isTimeRange", 1)
                put("startTime", "17:00"); put("endTime", "22:30"); put("hours", 5.5); put("hourlyRate", 52.0); put("totalEarnings", 286.0)
                put("isPaid", 0); put("notes", ""); put("createdAt", 1000L + id); put("isGroupShift", 0); putNull("employerRate"); putNull("workerRate")
                put("groupWorkersJson", ""); put("currency", "₪"); values()
            })
            entry(1) { }
            entry(2) { put("startTime", "22:00"); put("endTime", "06:00"); put("hours", 8.0); put("totalEarnings", 416.0); put("isPaid", 1) }
            entry(3) { put("isTimeRange", 0); putNull("startTime"); putNull("endTime"); put("hours", 3.5); put("hourlyRate", 35.0); put("totalEarnings", 122.5); put("currency", "$") }
            entry(4) { put("totalEarnings", 299.97); put("notes", "סכום שהוזן ידנית\nשורה שנייה") } // stored amount differs from hours × rate on purpose
            entry(5) { put("isGroupShift", 1); put("employerRate", 60.5); put("workerRate", 40.25); put("groupWorkersJson", """[{"name":"נועה","hours":6,"isPaid":true}]""") }
            db.execSQL("INSERT INTO work_local_preferences (name, value) VALUES ('defaultCategory', 'מסעדה')")
            db.version = 8
        }
        // RC16's sync journal and triggers, installed by the same schema code RC16 runs.
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        WorkSyncSchema.create(helper.writableDatabase)
        helper.close()
    }

    private fun rawJournal(name: String): List<String> = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { raw ->
        raw.rawQuery("SELECT entityType || ':' || localId || ':' || syncId || ':' || revision FROM work_sync_journal ORDER BY entityType, localId", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    }

    private fun journal(db: WorkDatabase): List<String> = db.openHelper.readableDatabase
        .query("SELECT entityType || ':' || localId || ':' || syncId || ':' || revision FROM work_sync_journal ORDER BY entityType, localId")
        .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    @Test fun rc16DatabaseUpgradesWithEveryValueUnchanged() = runBlocking {
        val name = "v2-migration-${java.util.UUID.randomUUID()}"
        createVersion8(name)
        // Read every row through RC16's own columns before the upgrade.
        val before: List<ContentValues> = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { raw ->
            raw.rawQuery("SELECT * FROM work_entries ORDER BY id", null).use { c ->
                buildList { while (c.moveToNext()) add(ContentValues().also { android.database.DatabaseUtils.cursorRowToContentValues(c, it) }) }
            }
        }
        val db = Room.databaseBuilder(context, WorkDatabase::class.java, name)
            .addMigrations(WorkDatabase.MIGRATION_8_9, WorkDatabase.MIGRATION_9_10, WorkDatabase.MIGRATION_10_11)
            .build()
        try {
            val rows = db.workDao().getEntriesList().sortedBy { it.id }
            assertEquals(5, rows.size)
            for ((i, row) in rows.withIndex()) {
                val old = before[i]
                assertEquals(old.getAsInteger("id"), row.id)
                assertEquals(old.getAsString("category"), row.category)
                assertEquals(old.getAsLong("date"), row.date)
                assertEquals(old.getAsInteger("isTimeRange") == 1, row.isTimeRange)
                assertEquals(old.getAsString("startTime"), row.startTime)
                assertEquals(old.getAsString("endTime"), row.endTime)
                assertEquals(old.getAsDouble("hours"), row.hours, 0.0)
                assertEquals(old.getAsDouble("hourlyRate"), row.hourlyRate, 0.0)
                assertEquals(old.getAsDouble("totalEarnings"), row.totalEarnings, 0.0)
                assertEquals(old.getAsInteger("isPaid") == 1, row.isPaid)
                assertEquals(old.getAsString("notes"), row.notes)
                assertEquals(old.getAsLong("createdAt"), row.createdAt)
                assertEquals(old.getAsInteger("isGroupShift") == 1, row.isGroupShift)
                assertEquals(old.getAsDouble("employerRate"), row.employerRate)
                assertEquals(old.getAsDouble("workerRate"), row.workerRate)
                assertEquals(old.getAsString("groupWorkersJson"), row.groupWorkersJson)
                assertEquals(old.getAsString("currency"), row.currency)
                // Every existing row is a normal shift with known pay, no bonus, not from a series.
                assertEquals(WorkEntry.KIND_SHIFT, row.kind)
                assertTrue(row.payKnown)
                assertEquals(0.0, row.bonus, 0.0)
                assertEquals(null, row.seriesKey)
                assertFalse(row.autoConfirmed)
            }
            assertEquals(299.97, rows[3].totalEarnings, 0.0) // never recalculated from hours × rate
            assertEquals(setOf("מסעדה", "שיעורים"), db.workDao().getCategoriesList().map { it.name }.toSet())
            assertEquals("מסעדה", db.workDao().getLocalPreferences().single { it.name == "defaultCategory" }.value)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun upgradeDoesNotRewriteRowsOrSyncIdentities() = runBlocking {
        val name = "v2-journal-${java.util.UUID.randomUUID()}"
        createVersion8(name)
        val rc16 = rawJournal(name)
        assertEquals(8, rc16.size) // 5 entries + 2 categories + 1 worker
        val db = Room.databaseBuilder(context, WorkDatabase::class.java, name).addMigrations(WorkDatabase.MIGRATION_8_9, WorkDatabase.MIGRATION_9_10, WorkDatabase.MIGRATION_10_11).build()
        try {
            // Same stable identities and the same revisions: the upgrade wrote no business row.
            assertEquals(rc16, journal(db))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun seriesKeyCanNeverBeRecordedTwice() = runBlocking {
        val name = "v2-series-${java.util.UUID.randomUUID()}"
        createVersion8(name)
        val db = Room.databaseBuilder(context, WorkDatabase::class.java, name).addMigrations(WorkDatabase.MIGRATION_8_9, WorkDatabase.MIGRATION_9_10, WorkDatabase.MIGRATION_10_11).build()
        try {
            val sql = db.openHelper.writableDatabase
            fun planned() = sql.execSQL("""INSERT INTO work_entries (category, date, isTimeRange, startTime, endTime, hours, hourlyRate,
                totalEarnings, isPaid, notes, createdAt, isGroupShift, groupWorkersJson, currency, kind, payKnown, bonus, seriesKey, autoConfirmed)
                VALUES ('מסעדה', 1, 1, '17:00', '22:30', 5.5, 52, 286, 0, '', 1, 0, '', '₪', 'shift', 1, 0, 's1@2026-10-20', 1)""")
            planned()
            try { planned(); fail("a series occurrence must not be recorded twice") } catch (_: SQLiteConstraintException) { }
            sql.execSQL("INSERT INTO shift_series (id, category, daysMask, startTime, endTime, hourlyRate, currency, fromDate, untilDate, createdAt) VALUES ('s1', 'מסעדה', 21, '17:00', '22:30', NULL, '₪', '2026-10-15', '2026-12-10', 1)")
            sql.execSQL("INSERT INTO series_exceptions (seriesId, date, cancelled) VALUES ('s1', '2026-10-22', 1)")
            assertEquals(6, db.workDao().getEntriesList().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
