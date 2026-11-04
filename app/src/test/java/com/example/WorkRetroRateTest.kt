package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FB-08: editing a workplace's rate with "שינוי רטרואקטיבי" on gives its unpaid shifts at the previous rate the new rate
 * and an amount from WorkPay.money; off (the default) changes nothing stored. Paid shifts never change. Same cases as
 * web-tests/fb08-retro-rate.test.cjs. Synthetic data only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkRetroRateTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, WorkDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.workDao()
    @After fun close() = db.close()

    private fun shift(id: Int, hours: Double = 2.0, rate: Double = 50.0, total: Double = 100.0, category: String = "מסעדה", paid: Boolean = false,
                      currency: String = "₪", kind: String = WorkEntry.KIND_SHIFT, payKnown: Boolean = true, group: Boolean = false, bonus: Double = 0.0) =
        WorkEntry(id = id, category = category, date = 1_000L * id, isTimeRange = false, hours = hours, hourlyRate = rate, totalEarnings = total,
            isPaid = paid, currency = currency, kind = kind, payKnown = payKnown, isGroupShift = group, bonus = bonus, createdAt = 1L)

    @Test fun onlyUnpaidPlainShiftsAtThePreviousRateAndCurrencyChange() {
        val rows = listOf(shift(1), shift(2, paid = true), shift(3, category = "עצמאי"), shift(4, rate = 60.0, total = 120.0),
            shift(5, rate = 0.0, total = 0.0, payKnown = false), shift(6, rate = 0.0, total = 300.0, kind = WorkEntry.KIND_JOB),
            shift(7, group = true), shift(8, currency = "$"), shift(9, bonus = 15.0, total = 115.0))
        assertEquals(listOf(1 to 110.0, 9 to 125.0), WorkPay.retroRate(rows, "מסעדה", 50.0, 55.0, "₪"))
    }

    @Test fun noChangeForTheSameOrAnInvalidRate() {
        val rows = listOf(shift(1))
        assertTrue(WorkPay.retroRate(rows, "מסעדה", 50.0, 50.0, "₪").isEmpty())
        for (bad in listOf(Double.NaN, -1.0, Double.POSITIVE_INFINITY)) assertTrue(WorkPay.retroRate(rows, "מסעדה", 50.0, bad, "₪").isEmpty())
    }

    @Test fun amountsUseTheOneMoneyFunction() {
        // M-08: 50 minutes × 33.33 = 27.775 → 27.78; 1.5 h × 99.99 = 149.985 → 149.99.
        val rows = listOf(shift(1, hours = 50 / 60.0, rate = 30.0, total = 25.0), shift(2, hours = 1.5, rate = 30.0, total = 45.0),
            shift(3, hours = 1.234567, rate = 30.0, bonus = 0.005, total = 37.04))
        assertEquals(listOf(27.78, 50.0, 41.15), WorkPay.retroRate(rows, "מסעדה", 30.0, 33.33, "₪").map { it.second })
        assertEquals(149.99, WorkPay.retroRate(rows, "מסעדה", 30.0, 99.99, "₪")[1].second, 0.0)
        rows.zip(WorkPay.retroRate(rows, "מסעדה", 30.0, 33.33, "₪")).forEach { (e, r) -> assertEquals(WorkPay.money(e.hours, 33.33, e.bonus), r.second, 0.0) }
    }

    @Test fun offByDefaultChangesNoStoredAmount() = runBlocking {
        dao.insertCategory(WorkCategory(name = "מסעדה", defaultRate = 50.0))
        listOf(shift(1), shift(2, paid = true)).forEach { dao.insertEntry(it) }
        val before = dao.getEntriesList()
        val cat = dao.getCategoryByName("מסעדה")!!
        assertTrue(dao.editCategorySafely(cat, "מסעדה", 55.0, "₪").isEmpty())
        assertEquals(55.0, dao.getCategoryByName("מסעדה")!!.defaultRate, 0.0)
        assertEquals(before, dao.getEntriesList())
    }

    @Test fun onUpdatesUnpaidShiftsInTheSameSaveEvenWhenRenamedAndUndoRestoresOnlyUntouchedOnes() = runBlocking {
        dao.insertCategory(WorkCategory(name = "מסעדה", defaultRate = 50.0))
        listOf(shift(1), shift(2, hours = 3.5, total = 175.0), shift(3, hours = 5.0, total = 250.0), shift(4, paid = true),
            shift(5, rate = 99.99, hours = 1.5, total = 149.98)).forEach { dao.insertEntry(it) }
        val cat = dao.getCategoryByName("מסעדה")!!
        val changed = dao.editCategorySafely(cat, "מסעדה חדשה", 55.0, "₪", retro = true)
        assertEquals(listOf(1, 2, 3), changed.map { it.id }.sorted())
        val e = { id: Int -> runBlocking { dao.getEntryById(id)!! } }
        assertEquals(listOf(110.0, 192.5, 275.0), listOf(1, 2, 3).map { e(it).totalEarnings })
        assertTrue(listOf(1, 2, 3).all { e(it).hourlyRate == 55.0 && e(it).category == "מסעדה חדשה" })
        assertEquals(shift(4, paid = true).copy(category = "מסעדה חדשה"), e(4).copy(id = 4))
        assertEquals(149.98, e(5).totalEarnings, 0.0); assertEquals(99.99, e(5).hourlyRate, 0.0)
        // Meanwhile 2 got paid and 3 was edited: undo leaves both alone.
        dao.updateEntry(e(2).copy(isPaid = true)); dao.updateEntry(e(3).copy(totalEarnings = 280.0))
        assertEquals(1, dao.restoreRetroRate(changed))
        assertEquals(50.0 to 100.0, e(1).hourlyRate to e(1).totalEarnings)
        assertEquals(55.0 to 192.5, e(2).hourlyRate to e(2).totalEarnings); assertTrue(e(2).isPaid)
        assertEquals(55.0 to 280.0, e(3).hourlyRate to e(3).totalEarnings)
        assertEquals(55.0, dao.getCategoryByName("מסעדה חדשה")!!.defaultRate, 0.0)
    }
}
