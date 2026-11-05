package com.example

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.example.data.WorkAccountScope
import com.example.data.WorkEntry
import com.example.ui.WorkViewModel
import com.example.ui.WorkViewModelFactory
import com.example.ui.kis.KisApp
import com.example.ui.kis.KisTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

/** Exercise the real Home -> shell -> ViewModel cancellation path with synthetic data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class WorkLiveCancelTest {
    val ui = createComposeRule()
    // ui-test-manifest is debug-only. Register the same host in Robolectric before
    // Compose launches it, so these cancellation checks also exercise Release.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(ui)
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val owner = WorkAccountScope("live-cancel-${UUID.randomUUID()}")
    private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private val timer get() = ShiftStateManager.forAccount(app, owner)
    private val dao get() = owner.database(app).workDao()
    private val originalStart = System.currentTimeMillis() - 120_000L
    private var confirmedStart: Long? = null

    @After fun close() {
        models.viewModelStore.clear()
        timer.clear(originalStart)
        confirmedStart?.let { timer.clear(it) }
    }

    private fun openConfirmation(): List<WorkEntry> {
        runBlocking { dao.insertEntry(WorkEntry(category = "synthetic", date = 1000L,
            isTimeRange = false, hours = 1.0, hourlyRate = 30.0, totalEarnings = 30.0)) }
        val before = runBlocking { dao.getEntriesList() }
        val vm = ViewModelProvider(models, WorkViewModelFactory(app, owner))
            .get(owner.storageKey, WorkViewModel::class.java)
        assertTrue(timer.start("synthetic", 30.0, originalStart))
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KisTheme(darkOverride = false) { KisApp(vm) }
            }
        }
        ui.onNodeWithTag("cancel_active_shift").performScrollTo().performClick()
        ui.onNodeWithTag("confirm_cancel_active_shift").assertIsDisplayed()
        return before
    }

    @Test fun confirmationCannotCancelTheReplacementTimer() {
        val before = openConfirmation()
        val replacement = originalStart + 60_000L
        confirmedStart = replacement
        ui.runOnIdle {
            assertTrue(timer.clear(originalStart))
            assertTrue(timer.start("replacement", 70.0, replacement, "$"))
        }
        confirmStale(before)
        assertEquals(replacement, timer.activeShiftStartTime.value)
        assertEquals("replacement", timer.activeShiftCategory.value)
        assertEquals("$", timer.activeShiftCurrency.value)
        assertEquals(70.0, timer.activeShiftRate.value, 0.0)
    }

    @Test fun confirmationAfterTimerEndedIsANoopWithFeedback() {
        val before = openConfirmation()
        ui.runOnIdle { assertTrue(timer.clear(originalStart)) }
        confirmStale(before)
        assertNull(timer.activeShiftStartTime.value)
    }

    private fun confirmStale(before: List<WorkEntry>) {
        ui.onNodeWithTag("confirm_cancel_active_shift").performScrollTo().performClick()
        ui.onNodeWithTag("confirm_cancel_active_shift").assertDoesNotExist()
        ui.onNodeWithText("המשמרת הפעילה השתנתה, לא בוטלה").assertIsDisplayed()
        runBlocking {
            assertEquals(before, dao.getEntriesList())
            assertNull(dao.receipt("timer:$originalStart"))
            confirmedStart?.let { assertNull(dao.receipt("timer:$it")) }
        }
    }

    @Test fun normalConfirmationStillDiscardsOnlyTheOriginalTimer() {
        val before = openConfirmation()
        ui.onNodeWithTag("confirm_cancel_active_shift").performClick()
        ui.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            timer.activeShiftStartTime.value == null
        }
        ui.onNodeWithTag("confirm_cancel_active_shift").assertDoesNotExist()
        ui.onNodeWithTag("live_shift_fab").performScrollTo().assertIsDisplayed()
        runBlocking {
            assertEquals(before, dao.getEntriesList())
            assertEquals(0L, dao.receipt("timer:$originalStart")?.result)
        }
    }
}
