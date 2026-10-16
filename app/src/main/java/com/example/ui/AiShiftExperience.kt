package com.example.ui

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.api.GeminiParser.ParsedShift
import com.example.data.WorkMoney
import java.text.SimpleDateFormat
import java.util.*

/** Owns only speech-to-editable-text. Deliberately has no parse or save callback. */
class VoiceTranscriptSession(private val onText: (String) -> Unit, private val onErrorText: (String) -> Unit) : RecognitionListener {
    var active by mutableStateOf(false); private set
    var listening by mutableStateOf(false); private set
    private var original = ""
    fun begin(text: String) { original = text; active = true; listening = true }
    fun stop() { listening = false }
    fun cancel() { if (active) onText(original); interrupt() }
    fun interrupt() { active = false; listening = false }
    private fun transcript(bundle: Bundle?): String? = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
    private fun deliver(text: String) { onText(listOf(original.trim(), text.trim()).filter { it.isNotEmpty() }.joinToString("\n")) }
    override fun onReadyForSpeech(params: Bundle?) { if (active) listening = true }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { listening = false }
    override fun onPartialResults(partialResults: Bundle?) { if (active) transcript(partialResults)?.let(::deliver) }
    override fun onResults(results: Bundle?) {
        if (!active) return
        transcript(results)?.let(::deliver)
        interrupt()
    }
    override fun onError(error: Int) {
        if (!active) return
        interrupt()
        onErrorText(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            "לא זוהה דיבור ברור. אפשר להקליט שוב או להקליד." else "התמלול לא הושלם. הטקסט נשמר — אפשר לערוך או לנסות שוב.")
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

@Composable
fun AiShiftInput(text: String, onText: (String) -> Unit, capturing: Boolean, listening: Boolean, processing: Boolean,
                 error: String?, onRecord: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit, onParse: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val c = com.example.ui.kis.Kis.colors
    val accent = com.example.ui.kis.Workplaces[0].accent(c.dark)
    Column(Modifier.fillMaxWidth().testTag("ai_experience").coachTarget(CoachTarget.AI), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.AutoAwesome, null, tint = accent, modifier = Modifier.size(26.dp))
            Column {
                Text("משמרת במילים שלך", color = c.ink, style = com.example.ui.kis.KisType.title)
                Text("כותבים או מכתיבים ← פענוח ← בדיקה ושמירה", color = c.muted, style = com.example.ui.kis.KisType.caption)
            }
        }
        com.example.ui.kis.KisTextField(text, onText, "תיאור המשמרת", Modifier.fillMaxWidth().testTag("ai_free_text_input"),
            placeholder = "אתמול עבדתי במסעדה מ-17 עד 23, חצי שעה הפסקה, 52 שקל לשעה", singleLine = false, minLines = 3,
            enabled = !processing && !capturing,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }))
        if (capturing) {
            val pulse = rememberInfiniteTransition(label = "voice")
            val alpha by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "listening glow")
            Column(Modifier.fillMaxWidth().background(c.sunk, RoundedCornerShape(16.dp)).padding(12.dp).testTag("ai_recording"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Mic, null, tint = accent.copy(alpha = if (listening) alpha else 1f))
                    Text(if (listening) "מקשיב... אפשר לדבר" else "מסיים את התמלול...", color = c.ink, style = com.example.ui.kis.KisType.body)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.ui.kis.KisButton("סיום הקלטה", onStop, Modifier.weight(1f), com.example.ui.kis.KisButtonKind.PRIMARY, enabled = listening, height = 48.dp, tag = "ai_stop")
                    com.example.ui.kis.KisButton("ביטול הקלטה", onCancel, Modifier.weight(1f), com.example.ui.kis.KisButtonKind.GHOST, height = 48.dp, tag = "ai_cancel_voice")
                }
            }
        } else {
            com.example.ui.kis.KisButton("הקלטת תיאור בקול", { keyboard?.hide(); focus.clearFocus(); onRecord() }, Modifier.fillMaxWidth(),
                com.example.ui.kis.KisButtonKind.OUTLINE, enabled = !processing, icon = Icons.Outlined.Mic, height = 50.dp, tag = "ai_mic_btn")
            Text("אפשר לערוך את הטקסט לפני הפענוח. שום משמרת לא נשמרת בלי אישור שלך.", color = c.muted, style = com.example.ui.kis.KisType.caption)
        }
        if (error != null) Text(error, color = c.danger, style = com.example.ui.kis.KisType.caption, modifier = Modifier.testTag("ai_inline_error"))
        if (processing) {
            Column(Modifier.fillMaxWidth().background(c.sunk, RoundedCornerShape(16.dp)).padding(14.dp).testTag("ai_processing"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = accent)
                    Text("מפענח את המשמרת...", color = c.ink, style = com.example.ui.kis.KisType.body)
                }
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent, trackColor = c.line)
                Text("מיד אפשר לבדוק את הפרטים לפני השמירה", color = c.muted, style = com.example.ui.kis.KisType.caption)
            }
        } else {
            com.example.ui.kis.KisButton("פענוח", { keyboard?.hide(); focus.clearFocus(); onParse() }, Modifier.fillMaxWidth(),
                enabled = text.isNotBlank() && !capturing, icon = Icons.Filled.AutoAwesome, tag = "ai_parse_button")
        }
    }
}

@Composable
fun AiShiftReview(shifts: List<ParsedShift>, onEdit: () -> Unit, onSave: () -> Unit, saving: Boolean = false) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    val c = com.example.ui.kis.Kis.colors
    AlertDialog(onDismissRequest = { if (!saving) onEdit() }, containerColor = c.surface,
        icon = { Icon(Icons.Filled.AutoAwesome, null, tint = com.example.ui.kis.Workplaces[0].accent(c.dark)) },
        title = { Text("בדיקת הפענוח", color = c.ink, style = com.example.ui.kis.KisType.title) },
        text = {
            Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).testTag("ai_review"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("בדוק שהפרטים נכונים. לשינוי, חזור לתיאור ופענח שוב.", color = c.muted)
                shifts.forEach { shift ->
                    Column(Modifier.fillMaxWidth().background(c.sunk, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(shift.category, color = c.ink, fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(shift.date)), color = c.muted, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                        Text("${shift.hours} שעות · תעריף \u2066${WorkMoney.format(shift.hourlyRate, shift.currency)}\u2069 לשעה", color = c.ink)
                        if (shift.isGroup) Text("קבוצה: ${shift.groupMembers.joinToString(", ")}", color = c.muted)
                        if (shift.notes.isNotBlank()) Text(shift.notes, color = c.muted)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onSave, enabled = !saving, modifier = Modifier.testTag("ai_confirm_save")) { Text(if (saving) "שומר…" else "שמור את המשמרות", color = c.ink, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onEdit, enabled = !saving) { Text("חזרה לעריכה", color = c.muted) } })
}
