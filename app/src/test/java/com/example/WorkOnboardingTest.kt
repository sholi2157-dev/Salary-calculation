package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkOnboardingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun fresh(): WorkOnboardingStore {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime = 1000; info.lastUpdateTime = 1000
        shadowOf(context.packageManager).installPackage(info)
        return WorkOnboardingStore(context)
    }
    @Test fun freshInstallAndCompletionSurviveReopen() {
        val store = fresh()
        assertTrue(store.initialize())
        assertTrue(WorkOnboardingStore(context).initialize())
        val controller = OnboardingController(store, 0)
        repeat(workOnboardingSteps.size) { controller.next() }
        assertFalse(controller.active)
        assertEquals(ONBOARDING_VERSION, store.completedVersion)
        assertFalse(WorkOnboardingStore(context).initialize())
    }
    @Test fun skipReplayBackNextAndReplaySkipKeepCompletion() {
        val store = fresh(); store.initialize()
        val controller = OnboardingController(store, 0)
        controller.finish()
        assertFalse(WorkOnboardingStore(context).initialize())
        controller.replay(); assertEquals(0, controller.index)
        controller.back(); assertEquals(0, controller.index)
        controller.next(); assertEquals(1, controller.index)
        controller.back(); assertEquals(0, controller.index)
        assertEquals(ONBOARDING_VERSION, store.completedVersion)
        controller.finish()
        assertFalse(WorkOnboardingStore(context).initialize())
    }
    @Test fun emptyUpgradeDoesNotForceTour() {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime = 1000; info.lastUpdateTime = 2000
        shadowOf(context.packageManager).installPackage(info)
        val store = WorkOnboardingStore(context)
        assertFalse(store.initialize())
        val controller = OnboardingController(store)
        controller.replay(); assertTrue(controller.active)
        repeat(workOnboardingSteps.size) { controller.next() }
        assertFalse(store.initialize())
    }
    @Test fun restoredLegacyStorageIsPreserved() {
        context.getSharedPreferences("active_shift_prefs", Context.MODE_PRIVATE).edit().putLong("start", 4567).commit()
        context.getSharedPreferences("personal_ai_setup", 0).edit().putBoolean("offered_local_device", true).commit()
        assertFalse(fresh().initialize())
        assertEquals(4567, context.getSharedPreferences("active_shift_prefs", 0).getLong("start", 0))
        assertTrue(context.getSharedPreferences("personal_ai_setup", 0).getBoolean("offered_local_device", false))
    }
}
