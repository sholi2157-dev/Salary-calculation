package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/** M-43/D-13: Excel money uses D-5; tables never replace historical rows. Synthetic data only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WorkTableMoneyTest {
    private val amounts = listOf(1.005, 2.675, 149.985, 1.00499999999, 42.12345, 0.0)
    private fun rows() = (0 until 22).map { i ->
        val day = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, i + 1, 12, 0) }.timeInMillis
        val amount = amounts[i % amounts.size]
        WorkEntry(category = "מקום ${i % 8}", date = day, createdAt = day + 1000,
            isTimeRange = false, hours = 100 / 60.0, hourlyRate = amount, totalEarnings = amount, bonus = amount,
            currency = if (i % 2 == 0) "₪" else "$", notes = "הערה מקורית",
            isGroupShift = i == 0, groupWorkersJson = if (i == 0) """[{"name":"בדיקה","hours":1,"future":"keep"}]""" else "")
    }

    @Test fun decimalCellsMatchMoneyAndBackupRetainsEveryStoredDecimal() {
        val original = rows()
        for (text in listOf(WorkShareText.csv(original), WorkShareText.excel(original))) {
            val table = WorkTableImport.decode(text)
            assertTrue(table.fromTable)
            table.entries.forEachIndexed { i, e ->
                val expected = WorkPay.money(1.0, amounts[i % amounts.size])
                assertEquals(expected, e.totalEarnings, 0.0)
                assertEquals(expected, e.hourlyRate, 0.0)
                assertEquals(expected, e.bonus, 0.0)
            }
            assertTrue(WorkTableImport.missingEntries(original, table.entries).isEmpty())
            assertEquals(1, WorkTableImport.missingEntries(original, table.entries + table.entries.first()).size)
            assertEquals(1, WorkTableImport.missingEntries(original, listOf(table.entries.first().copy(totalEarnings = 1.02))).size)
            assertTrue(WorkTableImport.missingEntries(original, table.entries.map { it.copy(notes = "נערך באקסל", isPaid = true) }).isEmpty())
        }
        val backup = WorkBackup.decode(WorkBackup.encode(emptyList(), original, emptyList()))
        assertFalse(backup.fromTable)
        assertEquals(original.map { it.totalEarnings }, backup.entries.map { it.totalEarnings })
        assertTrue(WorkBackup.missingEntries(original, backup.entries).isEmpty())
        assertEquals(1, WorkBackup.missingEntries(original, listOf(original.first().copy(notes = "different"))).size)
    }

    @Test fun actualDaoTableImportIsIdempotentAndPreservesHistoricalRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WorkDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.workDao()
            val original = rows()
            dao.importBackup(WorkBackup.Contents(emptyList(), original, emptyList()))
            val before = dao.getEntriesList()
            val table = WorkTableImport.decode(WorkShareText.csv(before))
            assertEquals(0, dao.importBackup(table).added)
            assertEquals(before, dao.getEntriesList())
            assertEquals(8, dao.getCategoriesList().size)
            val extra = table.copy(entries = table.entries + table.entries.first())
            assertEquals(1, dao.importBackup(extra).added)
            assertEquals(0, dao.importBackup(extra).added)
            assertEquals(23, dao.getEntriesList().size)
        } finally { db.close() }
    }
}
