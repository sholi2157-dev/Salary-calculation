package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.kis.KisConfirmSheet
import com.example.ui.kis.KisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose actions: opening and cancelling never mutate; confirming invokes only the supplied action. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp")
class WorkConfirmSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun exercise(dark: Boolean, confirm: Boolean) {
        val visible = mutableStateOf(true)
        var actions = 0
        var dismissals = 0
        compose.setContent {
            KisTheme(darkOverride = dark) {
                if (visible.value) KisConfirmSheet(
                    title = "למחוק את המשמרת?",
                    message = "אפשר לבטל מיד אחרי המחיקה.",
                    onDismiss = { dismissals++; visible.value = false },
                    confirmText = "מחיקה",
                    onConfirm = { actions++; visible.value = false },
                    confirmHaptic = null, confirmTag = "delete_test")
            }
        }
        compose.onNodeWithText("למחוק את המשמרת?").assertIsDisplayed()
        compose.onNodeWithText("אפשר לבטל מיד אחרי המחיקה.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, actions) }
        compose.onNodeWithTag(if (confirm) "delete_test" else "confirm_cancel").performClick()
        compose.runOnIdle {
            assertEquals(if (confirm) 1 else 0, actions)
            assertEquals(if (confirm) 0 else 1, dismissals)
        }
    }
    @Test fun cancellingInLightDoesNotRunTheAction() = exercise(dark = false, confirm = false)
    @Test fun confirmingInDarkRunsTheActionOnce() = exercise(dark = true, confirm = true)
}
