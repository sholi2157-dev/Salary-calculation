package com.example

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class OnboardingExperienceTest {
    @get:Rule val ui = createComposeRule()
    @Test fun completeRealTourAt360() = tour(360)
    @Test @Config(qualifiers = "w390dp-h844dp-xhdpi") fun completeRealTourAt390() = tour(390)
    @Test fun readableControlsAtIncreasedFontSize() = tour(360, 1.3f)
    private fun tour(width: Int, fontScale: Float = 1f) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Classify before Room opens, exactly as the Activity does.
        val store = WorkOnboardingStore(app); store.initialize()
        val db = WorkAccountScope(null).database(app)
        val vm = WorkViewModel(app, WorkRepository(db.workDao()))
        val controller = OnboardingController(store, 0)
        val before = runBlocking { db.workDao().exportSnapshot() }
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDensity provides Density(density.density, fontScale)) {
                MyApplicationTheme { WorkOnboarding(controller, vm, listOf(WorkCategory(name = "עצמאי", defaultRate = 40.0)), emptyList()) }
            }
        }
        for ((index, step) in workOnboardingSteps.withIndex()) {
            ui.onNodeWithTag("onboarding_progress").assertTextEquals("${index + 1} מתוך 9")
            if (step.target != null) {
                ui.mainClock.advanceTimeBy(1200)
                ui.waitForIdle()
                ui.onNodeWithTag("onboarding_root").captureRoboImage(filePath = "/tmp/onboarding-diagnostic-$width-$index-$fontScale.png")
                ui.waitUntil(5000) { ui.onAllNodes(SemanticsMatcher.expectValue(CoachTargetKey, step.target), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                ui.mainClock.advanceTimeBy(600)
                ui.waitForIdle()
                val target = ui.onNode(SemanticsMatcher.expectValue(CoachTargetKey, step.target), useUnmergedTree = true).getUnclippedBoundsInRoot()
                val card = ui.onNodeWithTag("onboarding_card").getUnclippedBoundsInRoot()
                assertTrue("${step.target} visible above or below coach: $target / $card", target.bottom <= card.top + 4.dp || target.top >= card.bottom - 4.dp)
                assertTrue("RTL coach next appears to right of back", ui.onNodeWithTag("onboarding_next").getUnclippedBoundsInRoot().left >= ui.onNodeWithTag("onboarding_back").getUnclippedBoundsInRoot().right)
                // Press on the spotlight itself: actual Save/Start/Share cannot run.
                ui.onNodeWithTag("onboarding_root").performTouchInput { click(androidx.compose.ui.geometry.Offset((target.left.value + target.right.value) / 2 * app.resources.displayMetrics.density, (target.top.value + target.bottom.value) / 2 * app.resources.displayMetrics.density)) }
                assertEquals(index, controller.index)
            }
            ui.onNodeWithTag("onboarding_next").assertIsDisplayed().assertIsEnabled()
            if (fontScale == 1f) ui.onNodeWithTag("onboarding_root").captureRoboImage(filePath = "/tmp/onboarding-$width-$index.png")
            if (index == 3) {
                ui.onNodeWithTag("onboarding_back").performClick()
                assertEquals(2, controller.index)
                ui.onNodeWithTag("onboarding_next").performClick()
            }
            ui.onNodeWithTag("onboarding_next").performClick()
        }
        assertFalse(controller.active)
        assertFalse(WorkOnboardingStore(app).initialize())
        assertEquals(before, runBlocking { db.workDao().exportSnapshot() })
        assertNull(vm.activeShiftStartTime.value)
        controller.replay()
        ui.onNodeWithTag("onboarding_skip").performClick()
        assertFalse(controller.active)
        assertFalse(store.initialize())
    }
    @Test fun settingsReplayActionIsDiscoverableAndWorks() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = WorkAccountScope(null).database(app)
        val vm = WorkViewModel(app, WorkRepository(db.workDao()))
        val store = WorkOnboardingStore(app); store.initialize(); store.complete()
        val controller = OnboardingController(store)
        ui.setContent { MyApplicationTheme { ManagementScreen(vm, emptyList(), {}, onReplayTutorial = controller::replay) } }
        ui.onNodeWithTag("replay_onboarding").performClick()
        assertTrue(controller.active)
        assertEquals(ONBOARDING_VERSION, store.completedVersion)
    }
}
