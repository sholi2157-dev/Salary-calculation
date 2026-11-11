package com.example

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.example.data.WorkAccountScope
import com.example.ui.WorkViewModel
import com.example.ui.WorkViewModelFactory
import com.example.ui.kis.KisApp
import com.example.ui.kis.KisTheme
import com.example.ui.kis.ShellRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** 2.4 task 05: the launcher's "הוספת משמרת" opens a new shift, the same as the website's home-screen shortcut. Synthetic data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class WorkAddShiftShortcutTest {
    val ui = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(ui)
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val owner = WorkAccountScope("add-shortcut-${UUID.randomUUID()}")
    private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }

    @After fun close() { models.viewModelStore.clear() }

    @Test fun addShiftRequestOpensTheNewShiftEditorOnce() {
        val vm = ViewModelProvider(models, WorkViewModelFactory(app, owner)).get(owner.storageKey, WorkViewModel::class.java)
        var handled = 0
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KisTheme(darkOverride = false) { KisApp(vm, request = ShellRequest.NEW_SHIFT, onRequestHandled = { handled++ }) }
            }
        }
        ui.waitForIdle()
        assertEquals(1, handled)
        // A new shift: "ביטול" (not "סגירה" of an edit) and the group-shift button, no delete.
        ui.onNodeWithTag("editor_close").assertIsDisplayed().assertTextContains("ביטול")
        ui.onNodeWithTag("editor_team").assertIsDisplayed()
        assertEquals(0, ui.onAllNodesWithTag("editor_delete").fetchSemanticsNodes().size)
    }

    @Test fun launcherDeclaresAddShiftAndTheActivityRoutesIt() {
        val res = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val shortcuts = File(res, "res/xml/shortcuts.xml").readText()
        assertTrue(shortcuts.contains("android:shortcutId=\"add_shift\""))
        assertTrue(shortcuts.contains("com.example.ACTION_ADD_SHIFT"))
        assertEquals("הוספת משמרת", app.getString(R.string.shortcut_add_shift))
        val activity = File(res, "java/com/example/MainActivity.kt").readText()
        assertTrue(activity.contains("\"com.example.ACTION_ADD_SHIFT\" -> com.example.ui.kis.ShellRequest.NEW_SHIFT"))
    }
}
