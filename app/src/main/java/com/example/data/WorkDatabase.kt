package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope

@Database(entities = [WorkEntry::class, WorkCategory::class, WorkerDirectory::class, WorkSyncRecord::class], version = 6, exportSchema = false)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(DatabaseCallback())
                .build()
    }

    internal class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            WorkSyncSchema.installTriggers(db)
            // Seed synchronously in this database, not through the guest singleton.
            db.execSQL("INSERT INTO work_categories (name, defaultRate) VALUES ('עצמאי', 40.0)")
        }
    }
}
