package com.example

import android.content.Context
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The exact published APK, offline synthetic future release, no download or actual user storage. */
class QuietUpdateBellTest {
    @get:Rule(order = 0) val noSignIn = SkipSignIn()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    @Test fun futureReleaseIsOnlyARedBellAndCardUntilUserActs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("release_updates", Context.MODE_PRIVATE)
        val previous = preferences.getString("available", null)
        val release = org.json.JSONObject().put("versionCode", 999999).put("versionName", "בדיקת פעמון")
            .put("apkUrl", "https://github.com/sholi2157-dev/Salary-calculation/releases/download/test/test.apk")
            .put("sha256", "0".repeat(64)).put("releaseNotes", "בדיקה בלבד; לא מורידים דבר").toString()
        preferences.edit().putString("available", release).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                ui.waitUntil(10000) { ui.onAllNodesWithTag("launch_next").fetchSemanticsNodes().isNotEmpty() || ui.onAllNodesWithTag("home_bell").fetchSemanticsNodes().isNotEmpty() }
                if (ui.onAllNodesWithTag("launch_next").fetchSemanticsNodes().isNotEmpty()) {
                    ui.mainClock.advanceTimeBy(3000)
                    ui.onNodeWithTag("launch_next").performClick()
                    ui.onNodeWithTag("launch_first").performTextReplacement("בודק")
                    ui.onNodeWithTag("launch_last").performTextReplacement("פעמון")
                    Espresso.closeSoftKeyboard()
                    ui.onNodeWithTag("launch_done").performClick()
                    ui.onNodeWithTag("onboarding_skip").performClick()
                }
                ui.waitUntil(10000) { ui.onAllNodesWithTag("home_bell_dot", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("מאוחר יותר").assertDoesNotExist()
                ui.onNodeWithTag("update_note").assertDoesNotExist()
                val pixels = ui.onNodeWithTag("home_bell_dot", useUnmergedTree = true).captureToImage().toPixelMap()
                val dot = pixels[pixels.width / 2, pixels.height / 2]
                assertTrue("Bell dot must be red", dot.red > dot.green * 1.3f && dot.red > dot.blue * 1.3f)
                snap(context, "v2-update-bell-red")
                ui.onNodeWithTag("home_bell").performClick()
                ui.waitUntil(5000) {
                    runCatching { ui.onNodeWithTag("update_note").assertIsDisplayed(); true }.getOrDefault(false)
                }
                ui.onNodeWithTag("update_now").assertIsDisplayed()
                ui.waitForIdle()
                snap(context, "v2-update-bell-card")
                Espresso.pressBack()
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                ui.onNodeWithTag("home_bell_dot", useUnmergedTree = true).assertExists()
                ui.onNodeWithTag("update_note").assertDoesNotExist()
                ui.onNodeWithText("מאוחר יותר").assertDoesNotExist()
            }
        } finally {
            preferences.edit().apply { if (previous == null) remove("available") else putString("available", previous) }.commit()
        }
    }
    private fun snap(context: Context, name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
