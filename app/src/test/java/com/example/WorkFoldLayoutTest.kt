package com.example

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.WorkViewModel
import com.example.ui.kis.*
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Folding phones (Galaxy Z Fold): a 280–344dp cover screen and a ~690–880dp unfolded screen.
 * Folded, nothing is cut or pushed past the edge ("היסטוריה" stays one word on one line); unfolded, the app is one
 * centred column of at most 560dp, as on the web, instead of stretching every card across the screen.
 * Synthetic data only. Screenshots: /tmp/kis-fold-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class WorkFoldLayoutTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db by lazy { WorkAccountScope(null).database(app) }
    private val dao get() = db.workDao()

    @Before fun seed() {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
            val dayMs = 86_400_000L
            val today = EntryDraft.startOfDay(System.currentTimeMillis())
            listOf("מסעדה בשכונה" to 45.0, "שמירה בלילות" to 52.5).forEachIndexed { i, (name, rate) -> dao.insertCategory(WorkCategory(id = i + 1, name = name, defaultRate = rate)) }
            repeat(8) { i ->
                val place = if (i % 2 == 0) "מסעדה בשכונה" else "שמירה בלילות"
                val rate = if (i % 2 == 0) 45.0 else 52.5
                val hours = 3.0 + i % 4
                dao.insertEntry(WorkEntry(category = place, date = today - (i * 2 + 1) * dayMs + 12 * 3_600_000L, isTimeRange = false,
                    hours = hours, hourlyRate = rate, totalEarnings = hours * rate, isPaid = i > 5))
            }
        }
    }

    private fun show(dark: Boolean) {
        val vm = WorkViewModel(app, WorkRepository(dao))
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KisTheme(darkOverride = dark) { KisFrame { KisApp(vm) } }
            }
        }
        ui.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            ui.onAllNodesWithTag("home_hero").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun shot(name: String) = ui.onRoot().captureRoboImage(filePath = "/tmp/kis-fold-$name.png")
    private val rootWidth get() = ui.onRoot().getBoundsInRoot().right

    /** Every node with [tag] (or any node, unmerged) lies inside the screen's width. */
    private fun assertInside(what: String, nodes: SemanticsNodeInteractionCollection) {
        val w = rootWidth
        nodes.fetchSemanticsNodes().forEach { n ->
            val b = n.boundsInRoot
            val left = with(ui.density) { b.left.toDp() }; val right = with(ui.density) { b.right.toDp() }
            assertTrue("$what: ${n.config} at $left..$right outside 0..$w", left >= (-1).dp && right <= w + 1.dp)
        }
    }

    private fun coverScreen(dark: Boolean, name: String) {
        show(dark)
        assertInside("navigation", ui.onAllNodesWithTag("bottom_navigation"))
        assertInside("navigation item", ui.onAllNodes(hasAnyAncestor(hasTestTag("bottom_navigation")), useUnmergedTree = true))
        shot("$name-home")
        ui.onNodeWithTag("tab_1").performClick()
        ui.waitForIdle()
        // One line: the title's height is one line of the page style (36sp), not two.
        val title = ui.onNodeWithTag("history_title").getBoundsInRoot()
        assertTrue("History's title is one line (${title.height})", title.height < 50.dp)
        assertInside("history title and tools", ui.onAllNodes(hasTestTag("history_title") or hasTestTag("history_filters") or hasTestTag("copy_menu_btn")))
        assertInside("history cards", ui.onAllNodes(hasTestTagStartingWith("work_entry_card_")))
        shot("$name-history")
        ui.onNodeWithTag("add_entry_button").performClick()
        ui.waitForIdle()
        assertInside("editor top", ui.onAllNodes(hasTestTag("editor_close") or hasTestTag("editor_team")))
        shot("$name-editor")
    }

    @Test @Config(qualifiers = "w280dp-h653dp-xhdpi") fun coverScreen280KeepsEveryWordWhole() = coverScreen(false, "280-light")
    @Test @Config(qualifiers = "w344dp-h882dp-xhdpi") fun coverScreen344KeepsEveryWordWholeDark() = coverScreen(true, "344-dark")

    private fun unfolded(dark: Boolean, name: String) {
        show(dark)
        val frame = ui.onNodeWithTag("kis_frame").getBoundsInRoot()
        assertEquals(560f, frame.width.value, 1f)
        // Centred: the same space on both sides.
        assertEquals((rootWidth - frame.right).value, frame.left.value, 1f)
        assertInside("navigation", ui.onAllNodesWithTag("bottom_navigation"))
        shot("$name-home")
        ui.onNodeWithTag("tab_1").performClick()
        ui.waitForIdle()
        ui.onAllNodes(hasTestTagStartingWith("work_entry_card_")).fetchSemanticsNodes().forEach { n ->
            assertTrue("a card stays inside the column", with(ui.density) { n.boundsInRoot.width.toDp() } <= 560.dp)
        }
        shot("$name-history")
        ui.onNodeWithTag("add_entry_button").performClick()
        ui.waitForIdle()
        shot("$name-editor")
    }

    @Test @Config(qualifiers = "w690dp-h829dp-xhdpi") fun unfolded690IsOneCentredColumn() = unfolded(false, "690-light")
    @Test @Config(qualifiers = "w884dp-h1104dp-xhdpi") fun unfolded884IsOneCentredColumnDark() = unfolded(true, "884-dark")

    /** A normal phone is unchanged: the column is the whole screen. */
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi") fun phone360UsesTheWholeWidth() {
        show(false)
        assertEquals(rootWidth.value, ui.onNodeWithTag("kis_frame").getBoundsInRoot().width.value, 1f)
        ui.onNodeWithTag("tab_1").performClick()
        ui.waitForIdle()
        assertTrue(ui.onNodeWithTag("history_title").getBoundsInRoot().height < 50.dp)
        shot("360-light-history")
    }

    private fun hasTestTagStartingWith(prefix: String) =
        SemanticsMatcher("testTag starts with $prefix") { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith(prefix) == true }
}
