package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.WorkDatabase
import com.example.ui.WorkUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Real public network and original RC13 UI on a disposable, synthetic-data emulator. */
@RunWith(AndroidJUnit4::class)
class LiveUpdateChannelTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val expectedHash = "588e058c21f3a1a42de88d7a33028ebce2c2ec302ce3deb5d8b6d6874854f627"
    @Test fun originalRc13FindsAndDownloadsThroughActualButton() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        assertEquals(21, context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
        val dao = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).workDao()
        val before = runBlocking { dao.exportSnapshot() }
        File(context.getExternalFilesDir(null), "live-before.json").writeText(before)
        val online = runBlocking { WorkUpdates.check(context) }!!
        assertEquals(22L, online.code)
        assertEquals(expectedHash, online.sha256)
        ui.onNodeWithContentDescription("ניהול וקטגוריות").performClick()
        ui.onNodeWithText("מערכת ומשוב").performScrollTo().performClick()
        ui.onNodeWithText("בדוק עדכונים", substring = true).performScrollTo().performClick()
        ui.waitUntil(60000) { ui.onAllNodesWithText("גרסה 1.5-rc14 זמינה").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("עדכון", useUnmergedTree = true).performClick()
        val downloaded = File(context.cacheDir, "salary-update-22.apk")
        ui.waitUntil(180000) { downloaded.exists() }
        assertEquals(expectedHash, MessageDigest.getInstance("SHA-256").digest(downloaded.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) })
        downloaded.copyTo(File(context.getExternalFilesDir(null), "live-downloaded.apk"), overwrite = true)
        assertEquals(before, runBlocking { dao.exportSnapshot() })
        // Allow the UI coroutine to start Android's package installer before test cleanup.
        Thread.sleep(2000)
    }
    @Test fun installedRc14RetainsDataAndDoesNotOfferItselfAgain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        assertEquals(22, context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
        val dao = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).workDao()
        val after = runBlocking { dao.exportSnapshot() }
        assertEquals(File(context.getExternalFilesDir(null), "live-before.json").readText(), after)
        assertEquals(3, runBlocking { dao.getEntriesList() }.size)
        assertNull(runBlocking { WorkUpdates.check(context) })
        File(context.getExternalFilesDir(null), "live-after.json").writeText(after)
    }
}
