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
    /** F12: with Google sign-in, the tour opens once for a brand-new account and never for one that already existed. */
    @Test fun newAccountGetsTheTourOnceUntilFinished() {
        val store = fresh(); store.initialize()
        assertTrue(isNewAccount(5_000, 5_000))
        assertTrue(isNewAccount(5_000, 5_000 + NEW_ACCOUNT_WINDOW_MS))
        assertFalse(isNewAccount(5_000, 5_001 + NEW_ACCOUNT_WINDOW_MS))
        assertFalse(isNewAccount(null, 5_000)); assertFalse(isNewAccount(5_000, null))
        // A new account: the tour, and again after reopening while it was never finished.
        assertTrue(store.claimAccount("new", 10_000, 10_400))
        assertTrue(WorkOnboardingStore(context).claimAccount("new", 10_000, 10_400))
        val controller = OnboardingController(store, 0)
        controller.finish()
        assertFalse(WorkOnboardingStore(context).claimAccount("new", 10_000, 10_400))
        // Skipping counts the same; a later sign-in of that account never brings it back.
        assertFalse(WorkOnboardingStore(context).claimAccount("new", 10_000, 999_999_999))
    }
    @Test fun existingAccountNeverGetsTheTour() {
        val store = fresh(); store.initialize()
        // An account made long ago (an update, another phone, the site), seen here for the first time.
        assertFalse(store.claimAccount("old", 10_000, 50_000_000))
        assertFalse(store.claimAccount("old", 10_000, 50_000_000))
        // A second, brand-new account on the same phone still gets its own tour once.
        assertTrue(store.claimAccount("second", 60_000_000, 60_000_000))
        store.complete()
        assertFalse(store.claimAccount("second", 60_000_000, 60_000_000))
        assertFalse(store.claimAccount("old", 10_000, 50_000_000))
    }
    @Test fun withoutGoogleSignInTheTourFollowsTheInstallation() {
        // Debug and test builds have no Google sign-in: a new installation keeps opening the tour by itself.
        assertFalse(tourFollowsAccount(context))
    }
}
