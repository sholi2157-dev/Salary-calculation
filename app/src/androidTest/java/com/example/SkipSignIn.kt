package com.example

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Device checks of the app's screens run without a Google account: the 2.1 sign-in screen stays away. */
class SkipSignIn : ExternalResource() {
    override fun before() { com.example.data.WorkAccountBinding.skipSignIn(InstrumentationRegistry.getInstrumentation().targetContext, true) }
}
