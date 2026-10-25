package com.example

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/**
 * Device checks of the app's screens run without a Google account: the 2.1 sign-in screen stays away. Against an
 * installed release from before 2.1 (the live update check starts from one) there is no sign-in and nothing to skip.
 */
class SkipSignIn : ExternalResource() {
    override fun before() {
        try { com.example.data.WorkAccountBinding.skipSignIn(InstrumentationRegistry.getInstrumentation().targetContext, true) }
        catch (_: NoClassDefFoundError) {}
    }
}
