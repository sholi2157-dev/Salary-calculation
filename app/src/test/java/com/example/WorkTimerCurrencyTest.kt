package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkTimerCurrencyTest {
    @Test fun activeCurrencyPersistsWithoutRestartingTimerAndRejectsStaleChanges() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val owner = WorkAccountScope("currency-${UUID.randomUUID()}")
        val state = ShiftStateManager.forAccount(app, owner)
        assertTrue(state.start("synthetic", 37.25, 1234L, "$"))
        try {
            assertFalse(state.updateCurrency("₪", 1233L))
            assertTrue(state.updateCurrency("₪", 1234L))
            val reopened = WorkShiftState(app, owner)
            assertEquals(1234L, reopened.activeShiftStartTime.value)
            assertEquals("₪", reopened.activeShiftCurrency.value)
            assertEquals(37.25, reopened.activeShiftRate.value, 0.0)
        } finally { state.clear(1234L) }
        assertFalse(state.updateCurrency("$", 1234L))
    }

    @Test fun currencyCorrectionKeepsImportedAmountAndGroupMetadata() {
        val original = WorkEntry(id=7, category="synthetic", date=1234L, isTimeRange=false,
            hours=2.0, hourlyRate=40.0, totalEarnings=79.13, currency="₪", createdAt=321L,
            isGroupShift=true, groupWorkersJson="[]", isPaid=true)
        val result = WorkEntryEdits.apply(original, original.copy(currency="$"))
        assertEquals(original.copy(currency="$"), result)
    }

    @Test fun discardDoesNotInsertShiftAndStaleSaveCannotResurrectIt() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = WorkAccountScope("discard-${UUID.randomUUID()}").database(app).workDao()
        val original = WorkEntry(category="synthetic", date=2000L, isTimeRange=false,
            hours=1.0, hourlyRate=30.0, totalEarnings=30.0)
        dao.insertEntry(original)
        val before = dao.getEntriesList()
        assertEquals(0L, dao.finishTimer(1000L, null))
        assertEquals(0L, dao.finishTimer(1000L, original))
        assertEquals(before, dao.getEntriesList())
    }
}
