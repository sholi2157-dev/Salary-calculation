package com.example

import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.api.GeminiParser.ParsedShift
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class AiShiftExperienceTest {
    @get:Rule val ui = createComposeRule()
    private fun result(text: String) = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) }
    @Test fun voiceCancellationLateResultsAndInterruption() {
        var draft = "טיוטה קיימת"
        var error = ""
        val voice = VoiceTranscriptSession({ draft = it }, { error = it })
        voice.begin(draft); voice.onPartialResults(result("שמונה שעות"))
        assertEquals("טיוטה קיימת\nשמונה שעות", draft)
        voice.cancel(); voice.onResults(result("תוצאה מאוחרת"))
        assertEquals("טיוטה קיימת", draft); assertFalse(voice.active)
        voice.begin(draft); voice.onPartialResults(result("תמלול חלקי")); voice.interrupt(); voice.onResults(result("מאוחר"))
        assertEquals("טיוטה קיימת\nתמלול חלקי", draft)
        voice.begin(draft); voice.onError(SpeechRecognizer.ERROR_NETWORK)
        assertFalse(voice.active); assertTrue(error.isNotBlank()); assertTrue(draft.endsWith("תמלול חלקי"))
    }
    @Test fun editableVoiceParseReviewAt360() = checkFlow()
    @Test @Config(qualifiers = "w390dp-h800dp-xhdpi") fun editableVoiceParseReviewAt390() = checkFlow()
    private fun checkFlow() {
        var text by mutableStateOf("")
        var error by mutableStateOf<String?>(null)
        var processing by mutableStateOf(false)
        var review by mutableStateOf(false)
        var parseCount = 0
        var saves = 0
        val voice = VoiceTranscriptSession({ text = it }, { error = it })
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        AiShiftInput(text, { text = it; error = null }, voice.active, voice.listening, processing, error,
                            { voice.begin(text) }, { voice.stop() }, { voice.cancel() }, { parseCount++; processing = true })
                    }
                    if (review) AiShiftReview(listOf(
                        ParsedShift("מעסיק בעברית", 1, 8.0, 50.0, "הערה לבדיקה"),
                        ParsedShift("קבוצה", 1, 4.5, 20.0, "", isGroup = true, groupMembers = listOf("ישראל", "משה"), currency = "$")
                    ), { review = false }, { saves++; review = false })
                }
            }
        }
        ui.onNodeWithTag("ai_parse_button").assertIsNotEnabled()
        ui.onNodeWithTag("ai_mic_btn").performScrollTo().performClick()
        ui.onNodeWithTag("ai_recording").assertIsDisplayed()
        ui.onRoot().captureRoboImage(filePath = "/tmp/stage-ai-recording-${ui.onRoot().getUnclippedBoundsInRoot().right.value.toInt()}.png")
        ui.onNodeWithTag("ai_stop").performClick()
        ui.runOnIdle { voice.onResults(result("אתמול עבדתי שמונה שעות")) }
        assertEquals(0, parseCount); assertEquals(0, saves)
        ui.onNodeWithTag("ai_free_text_input").performScrollTo().assertIsEnabled().performTextReplacement("אתמול עבדתי 7 שעות בתעריף 50")
        ui.onNodeWithTag("ai_parse_button").performScrollTo().performClick()
        assertEquals(1, parseCount); assertEquals(0, saves)
        ui.onNodeWithTag("ai_processing").assertIsDisplayed()
        ui.onRoot().captureRoboImage(filePath = "/tmp/stage-ai-processing-${ui.onRoot().getUnclippedBoundsInRoot().right.value.toInt()}.png")
        ui.runOnIdle { processing = false; error = "לא הצלחנו לפענח. אפשר לתקן ולנסות שוב." }
        ui.onNodeWithTag("ai_inline_error").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("ai_free_text_input").assertTextContains("7 שעות", substring = true)
        ui.onNodeWithTag("ai_free_text_input").performScrollTo().performTextReplacement("אתמול עבדתי 8 שעות בתעריף 50")
        ui.onNodeWithTag("ai_inline_error").assertDoesNotExist()
        ui.onNodeWithTag("ai_parse_button").performScrollTo().performClick()
        ui.runOnIdle { processing = false; review = true }
        ui.onNodeWithTag("ai_review").assertIsDisplayed()
        ui.onNodeWithText("₪50.00", substring = true).assertIsDisplayed()
        ui.onNodeWithText("$20.00", substring = true).assertIsDisplayed()
        assertEquals(0, saves)
        ui.onNodeWithTag("ai_review").captureRoboImage(filePath = "/tmp/stage-ai-review-${ui.onNodeWithTag("ai_review").getUnclippedBoundsInRoot().right.value.toInt()}.png")
        ui.onNodeWithText("חזרה לעריכה").performClick()
        ui.onNodeWithTag("ai_free_text_input").assertTextContains("8 שעות", substring = true)
        ui.runOnIdle { review = true }
        ui.onNodeWithTag("ai_confirm_save").performClick()
        assertEquals(1, saves)
    }
}
