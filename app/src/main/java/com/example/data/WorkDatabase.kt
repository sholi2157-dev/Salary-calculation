package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope

@Database(entities = [WorkEntry::class, WorkCategory::class, WorkerDirectory::class, WorkSyncRecord::class, WorkLocalReceipt::class, WorkLocalPreference::class, ShiftSeries::class, SeriesException::class, WorkSyncKeyedRecord::class], version = 11, exportSchema = true)
abstract class WorkDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao
    abstract fun syncDao(): WorkSyncDao

    companion object {
        @Volatile
        private var INSTANCE: WorkDatabase? = null
        private val accountInstances = mutableMapOf<String, WorkDatabase>()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_entries ADD COLUMN isGroupShift INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE work_entries ADD COLUMN employerRate REAL")
                db.execSQL("ALTER TABLE work_entries ADD COLUMN workerRate REAL")
                db.execSQL("ALTER TABLE work_entries ADD COLUMN groupWorkersJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS `worker_directory` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_categories ADD COLUMN defaultRate REAL NOT NULL DEFAULT 40.0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op or rebuild if needed
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_entries ADD COLUMN currency TEXT NOT NULL DEFAULT '₪'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                WorkSyncSchema.create(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS work_local_receipts (operation TEXT NOT NULL PRIMARY KEY, result INTEGER NOT NULL)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS work_local_preferences (name TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            }
        }

        /**
         * 2.0: additive only. New columns get defaults that describe every existing row exactly
         * (a normal shift, pay known, no bonus, not from a series). No row is updated, so the
         * sync journal triggers do not fire and no stored amount is touched.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Skip a column that already exists, so a retried or partially-applied upgrade cannot fail.
                val existing = mutableSetOf<String>()
                db.query("PRAGMA table_info(work_entries)").use { c -> while (c.moveToNext()) existing += c.getString(c.getColumnIndexOrThrow("name")) }
                fun add(column: String, definition: String) {
                    if (column !in existing) db.execSQL("ALTER TABLE work_entries ADD COLUMN $column $definition")
                }
                add("kind", "TEXT NOT NULL DEFAULT 'shift'")
                add("payKnown", "INTEGER NOT NULL DEFAULT 1")
                add("bonus", "REAL NOT NULL DEFAULT 0")
                add("seriesKey", "TEXT")
                add("autoConfirmed", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_work_entries_seriesKey ON work_entries (seriesKey)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS shift_series (id TEXT NOT NULL, category TEXT NOT NULL,
                    daysMask INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, hourlyRate REAL,
                    currency TEXT NOT NULL, fromDate TEXT NOT NULL, untilDate TEXT NOT NULL, createdAt INTEGER NOT NULL,
                    PRIMARY KEY(id))""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS series_exceptions (seriesId TEXT NOT NULL, date TEXT NOT NULL,
                    cancelled INTEGER NOT NULL DEFAULT 0, hold INTEGER NOT NULL DEFAULT 0, startTime TEXT, endTime TEXT,
                    rateOverridden INTEGER NOT NULL DEFAULT 0, hourlyRate REAL, PRIMARY KEY(seriesId, date))""")
            }
        }

        /** Account sync: the outbox for plans, plan days and settings. Additive; no business row is touched. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                WorkSyncSchema.createKeyed(db)
            }
        }

        /** Money passed through the user (car, materials). Additive; every existing row gets 0. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existing = mutableSetOf<String>()
                db.query("PRAGMA table_info(work_entries)").use { c -> while (c.moveToNext()) existing += c.getString(c.getColumnIndexOrThrow("name")) }
                if ("passThrough" !in existing) db.execSQL("ALTER TABLE work_entries ADD COLUMN passThrough REAL NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): WorkDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, WorkAccountStorage.GUEST_DATABASE).also { INSTANCE = it }
            }

        /**
         * Explicit account store. Opening it never moves/copies guest data.
         * UI account switching remains gated until all session-bound state is isolated.
         */
        fun getAccountDatabase(context: Context, uid: String): WorkDatabase = synchronized(this) {
            val name = WorkAccountStorage.databaseName(uid)
            accountInstances.getOrPut(name) { build(context, name) }
        }

        private fun build(context: Context, name: String): WorkDatabase =
            Room.databaseBuilder(context.applicationContext, WorkDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .addCallback(DatabaseCallback())
                .build()
    }

    internal class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            WorkSyncSchema.installTriggers(db)
            WorkSyncSchema.installKeyedTriggers(db)
            // Seed synchronously in this database, not through the guest singleton.
            db.execSQL("INSERT INTO work_categories (name, defaultRate) VALUES ('עצמאי', 40.0)")
        }
    }
}
