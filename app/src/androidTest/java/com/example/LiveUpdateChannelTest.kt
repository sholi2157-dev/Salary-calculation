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

/** Real public network and previous delivered release UI on a disposable, synthetic-data emulator. */
@RunWith(AndroidJUnit4::class)
class LiveUpdateChannelTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val args = InstrumentationRegistry.getArguments()
    private val expectedHash = args.getString("updateSha256")!!
    private val nextCode = args.getString("updateVersionCode")!!.toInt()
    private val previousCode = args.getString("previousVersionCode")!!.toInt()
    private val nextName = args.getString("updateVersionName")!!
    @Test fun previousReleaseFindsAndDownloadsThroughActualButton() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        assertEquals(previousCode, context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
        val dao = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).workDao()
        val before = runBlocking { dao.exportSnapshot() }
        File(context.getExternalFilesDir(null), "live-before.json").writeText(before)
        android.util.Log.i("LiveUpdateEvidence", "before_public_check")
        val online = runBlocking { WorkUpdates.check(context) }!!
        assertEquals(nextCode.toLong(), online.code)
        assertEquals(expectedHash, online.sha256)
        android.util.Log.i("LiveUpdateEvidence", "public_check_passed_opening_settings")
        ui.onNodeWithContentDescription("ניהול וקטגוריות").performClick()
        ui.onNodeWithText("מערכת ומשוב").performScrollTo().performClick()
        ui.onNodeWithText("בדוק עדכונים", substring = true).performScrollTo().performClick()
        ui.waitUntil(60000) { ui.onAllNodesWithText("גרסה $nextName זמינה").fetchSemanticsNodes().isNotEmpty() }
        android.util.Log.i("LiveUpdateEvidence", "manual_button_found_live_release")
        ui.onNodeWithText("עדכון", useUnmergedTree = true).performClick()
        val downloaded = File(context.cacheDir, "salary-update-$nextCode.apk")
        ui.waitUntil(180000) { downloaded.exists() }
        assertEquals(expectedHash, MessageDigest.getInstance("SHA-256").digest(downloaded.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) })
        android.util.Log.i("LiveUpdateEvidence", "live_download_verified_installer_started")
        downloaded.copyTo(File(context.getExternalFilesDir(null), "live-downloaded.apk"), overwrite = true)
        assertEquals(before, runBlocking { dao.exportSnapshot() })
        // Allow the UI coroutine to start Android's package installer before test cleanup.
        Thread.sleep(2000)
    }
}

@RunWith(AndroidJUnit4::class)
class LiveUpdateRetainedDataTest {
    @Test fun updatedReleaseRetainsDataAndDoesNotOfferItselfAgain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        assertEquals(InstrumentationRegistry.getArguments().getString("updateVersionCode")!!.toInt(), context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
        val dao = WorkDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).workDao()
        val after = runBlocking { dao.exportSnapshot() }
        assertEquals(File(context.getExternalFilesDir(null), "live-before.json").readText(), after)
        assertEquals(3, runBlocking { dao.getEntriesList() }.size)
        assertNull(runBlocking { WorkUpdates.check(context) })
        File(context.getExternalFilesDir(null), "live-after.json").writeText(after)
    }
}
