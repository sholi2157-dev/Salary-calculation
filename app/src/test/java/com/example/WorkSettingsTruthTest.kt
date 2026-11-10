package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.domain.V2Plan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkSettingsTruthTest {
    @Test fun defaultDeletionIsRejectedAndOtherDeletionOnlyMovesLabels() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            val first = WorkCategory(id = dao.insertCategory(WorkCategory(name = "ראשון")).toInt(), name = "ראשון")
            val chosen = WorkCategory(id = dao.insertCategory(WorkCategory(name = "נבחר")).toInt(), name = "נבחר")
            dao.setLocalPreference(WorkLocalPreference("categoryCurrency:ראשון", "$"))
            dao.insertEntry(WorkEntry(category = first.name, date = 1000L, isTimeRange = false,
                hours = 2.0, hourlyRate = 40.0, totalEarnings = 79.13, currency = "$",
                groupWorkersJson = """[{"name":"synthetic","futureField":17}]"""))
            dao.saveSeries(ShiftSeries("synthetic", first.name, V2Plan.mask(setOf(1)), "09:00", "17:00", null,
                "$", "2026-10-01", "2026-12-31", 5L), listOf("2026-10-05"))
            val before = dao.exportSnapshot()
            // No preference (or an invalid one): the same first category shown by Settings is the effective default.
            assertFalse(dao.removeCategorySafely(first))
            assertEquals(before, dao.exportSnapshot())
            dao.setLocalPreference(WorkLocalPreference("defaultCategory", "missing"))
            val invalidBefore = dao.exportSnapshot()
            assertFalse(dao.removeCategorySafely(first))
            assertEquals(invalidBefore, dao.exportSnapshot())
            dao.setLocalPreference(WorkLocalPreference("defaultCategory", chosen.name))
            val chosenBefore = dao.exportSnapshot()
            assertFalse(dao.removeCategorySafely(chosen))
            // A stale object cannot bypass the guard by carrying the default's id with another name.
            assertFalse(dao.removeCategorySafely(chosen.copy(name = "old name")))
            assertEquals(chosenBefore, dao.exportSnapshot())
            val entries = dao.getEntriesList()
            val series = dao.getSeries()
            val exceptions = dao.getExceptions()
            assertTrue(dao.removeCategorySafely(first))
            assertEquals(entries.map { it.copy(category = chosen.name) }, dao.getEntriesList())
            assertEquals(series.map { it.copy(category = chosen.name) }, dao.getSeries())
            assertEquals(exceptions, dao.getExceptions())
            assertEquals(listOf(chosen), dao.getCategoriesList())
            assertEquals(listOf(WorkLocalPreference("defaultCategory", chosen.name)), dao.getLocalPreferences())
            assertFalse(dao.removeCategorySafely(chosen))
        } finally { db.close() }
    }
}
