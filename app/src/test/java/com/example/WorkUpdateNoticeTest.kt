package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.WorkUpdateNotice
import com.example.ui.WorkUpdates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkUpdateNoticeTest {
    @Test fun checksEveryRequestKeepsNoticeOfflineAndRemovesInstalledOrUnavailableRelease() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("release_updates", Context.MODE_PRIVATE)
        preferences.edit().clear().putLong("lastBellCheck", System.currentTimeMillis()).commit()
        val release = WorkUpdates.Release(999, "test", "https://github.com/sholi2157-dev/Salary-calculation/releases/download/test/test.apk", "0".repeat(64), "test")
        var calls = 0
        repeat(2) { assertEquals(release, WorkUpdateNotice.refreshWith(context) { calls++; release }) }
        assertEquals(2, calls) // A previous hourly timestamp cannot suppress a new foreground/bell check.
        assertEquals(release, WorkUpdateNotice.refreshWith(context) { throw java.io.IOException("offline") })
        assertNull(WorkUpdateNotice.refreshWith(context) { null })
        assertNull(WorkUpdateNotice.cached(context))
    }
}
