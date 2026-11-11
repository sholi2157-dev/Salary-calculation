package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 2.4 task 03: the F12 rule, row by row. The tour opens once per NEW account on its first successful connect; never on
 * reconnect after logout, an update or another device; replay only from "הצגת ההדרכה מחדש"; without an account, once
 * per installation. Same rows as browser-tests/f12-account-tour.cjs on the site. Synthetic ids and times only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkF12MatrixTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val t0 = 1_800_000_000_000L
    private fun install(first: Long, last: Long) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime = first; info.lastUpdateTime = last
        shadowOf(context.packageManager).installPackage(info)
    }
    /** A process start: a new store over the same preferences (what a killed app sees when it opens again). */
    private fun reopen() = WorkOnboardingStore(context)
    @Before fun freshInstall() { install(1000, 1000) }

    @Test fun brandNewAccountFirstConnect() {
        val store = reopen(); store.initialize()
        assertTrue(store.claimAccount("acct-new", t0, t0 + 900, isNewUser = true))
        // Firebase's answer may be missing (a session restored by Firebase itself): the creation window decides.
        assertTrue(reopen().claimAccount("acct-new-2", t0, t0 + 900, isNewUser = null))
    }

    @Test fun sameAccountLogoutAndReconnectNeverAgain() {
        val store = reopen(); store.initialize()
        assertTrue(store.claimAccount("acct-a", t0, t0))
        OnboardingController(store, 0).finish()
        // Logout, then sign in again a minute later: still inside the creation window, still no tour.
        assertFalse(reopen().claimAccount("acct-a", t0, t0 + 60_000, isNewUser = false))
        assertFalse(reopen().claimAccount("acct-a", t0, t0 + 60_000, isNewUser = null))
    }

    @Test fun existingAccountFirstTimeOnANewDevice() {
        reopen().initialize()
        assertFalse(reopen().claimAccount("acct-old", t0 - 90L * 86_400_000, t0, isNewUser = false))
        assertFalse(reopen().claimAccount("acct-old-2", t0 - 90L * 86_400_000, t0, isNewUser = null))
    }

    @Test fun newAccountKilledMidTourResumesThenNever() {
        val store = reopen(); store.initialize()
        assertTrue(store.claimAccount("acct-k", t0, t0, isNewUser = true))
        val first = OnboardingController(store, 0); first.next(); first.next()
        assertTrue(first.active) // the process dies here, mid-tour
        // Next open: the tour comes back from its start (Firebase gives no isNewUser for a restored session).
        val again = reopen()
        assertTrue(again.claimAccount("acct-k", t0, t0, isNewUser = null))
        OnboardingController(again, 0).finish()
        assertFalse(reopen().claimAccount("acct-k", t0, t0, isNewUser = null))
        assertFalse(reopen().claimAccount("acct-k", t0, t0 + 5 * 60_000, isNewUser = null))
    }

    @Test fun newAccountOnASecondDeviceInsideTheWindow() {
        reopen().initialize()
        // Made on the site 40 s ago, signed in here now: the window alone says "new", Firebase says it already existed.
        assertTrue(isNewAccount(t0, t0 + 40_000))
        assertFalse(reopen().claimAccount("acct-two", t0, t0 + 40_000, isNewUser = false))
        // Decided once: a later open of the same session never brings it.
        assertFalse(reopen().claimAccount("acct-two", t0, t0 + 40_000, isNewUser = null))
    }

    @Test fun noAccountInstallOncePerInstallation() {
        val store = reopen()
        assertTrue(store.initialize())
        assertTrue(reopen().initialize()) // killed before finishing: once more
        OnboardingController(store, 0).finish()
        assertFalse(reopen().initialize())
        com.example.data.WorkAccountBinding.skipSignIn(context, true)
        try { assertFalse(tourFollowsAccount(context)) } finally { com.example.data.WorkAccountBinding.skipSignIn(context, false) }
    }

    @Test fun replayFromSettingsAnyTimeWithoutReopeningAnAccount() {
        val store = reopen(); store.initialize()
        assertTrue(store.claimAccount("acct-r", t0, t0))
        OnboardingController(store, 0).finish()
        val controller = OnboardingController(store)
        assertFalse(controller.active)
        controller.replay(); assertTrue(controller.active); assertEquals(0, controller.index)
        repeat(workOnboardingSteps.size) { controller.next() }
        assertFalse(controller.active)
        assertFalse(reopen().claimAccount("acct-r", t0, t0))
        assertFalse(reopen().initialize())
    }

    @Test fun appUpdateNoTourButWhatsNew() {
        install(1000, 2000)
        assertFalse(reopen().initialize())
        assertFalse(reopen().claimAccount("acct-u", t0 - 30L * 86_400_000, t0, isNewUser = null))
        assertEquals(StartOverlay.WHATS_NEW, startOverlay(launching = false, touring = false, accountDecided = true,
            notificationsDue = false, notificationsReady = false, whatsNew = true))
    }

    @Test fun oneOverlayAtATimeInOrder() {
        fun at(l: Boolean, t: Boolean, d: Boolean, nd: Boolean, nr: Boolean, w: Boolean) = startOverlay(l, t, d, nd, nr, w)
        // The launch covers everything; the tour waits behind it.
        assertEquals(StartOverlay.LAUNCH, at(true, true, true, true, true, true))
        // The tour before the notification question and what's new.
        assertEquals(StartOverlay.TOUR, at(false, true, true, true, true, true))
        // A sign-in still deciding: nothing yet (no sheet the tour could land on).
        assertEquals(StartOverlay.NONE, at(false, false, false, true, true, true))
        // The question waits its short delay; what's new does not jump in during that gap.
        assertEquals(StartOverlay.NONE, at(false, false, true, true, false, true))
        assertEquals(StartOverlay.NOTIFICATIONS, at(false, false, true, true, true, true))
        // Answered: what's new; then nothing.
        assertEquals(StartOverlay.WHATS_NEW, at(false, false, true, false, false, true))
        assertEquals(StartOverlay.NONE, at(false, false, true, false, false, false))
        // Exactly one value for every combination.
        for (bits in 0 until 64) {
            val b = { i: Int -> bits shr i and 1 == 1 }
            val o = at(b(0), b(1), b(2), b(3), b(4), b(5))
            if (o == StartOverlay.TOUR) assertTrue(!b(0))
            if (o == StartOverlay.NOTIFICATIONS || o == StartOverlay.WHATS_NEW) assertTrue(!b(0) && !b(1) && b(2))
            if (o == StartOverlay.WHATS_NEW) assertFalse(b(3))
        }
    }
}
