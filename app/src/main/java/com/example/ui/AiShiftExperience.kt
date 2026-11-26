package com.example.ui

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
    val onAccent = if (c.dark) c.bg else Color.White
    // Owner 2026-11-26 (option 2): the page is built around dictation. A big microphone in the middle, a short line on
    // what to say, and a smaller box for typing below it. Nothing is saved without the review.
    Column(Modifier.fillMaxWidth().testTag("ai_experience"), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ספר לי על המשמרת", color = c.ink, style = com.example.ui.kis.KisType.title, textAlign = TextAlign.Center)
            Text("למשל: \"אתמול במסעדה מ-5 עד 11, חצי שעה הפסקה\"", color = c.muted, style = com.example.ui.kis.KisType.caption, textAlign = TextAlign.Center)
        }
        val pulse = rememberInfiniteTransition(label = "voice")
        val glow by pulse.animateFloat(0.10f, 0.26f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "listening glow")
        val ring = if (capturing && listening) glow else 0.14f
        Box(Modifier.padding(vertical = 6.dp).size(184.dp).coachTarget(CoachTarget.AI).clip(CircleShape).background(accent.copy(alpha = ring * 0.5f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(156.dp).clip(CircleShape).background(accent.copy(alpha = ring)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(128.dp).clip(CircleShape).background(if (processing) c.sunk else accent)
                    .clickable(enabled = !processing && !capturing, role = Role.Button) { keyboard?.hide(); focus.clearFocus(); onRecord() }
                    .semantics { contentDescription = if (capturing) "מקשיב" else "הקלטת תיאור בקול" }.testTag("ai_mic_btn"),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, null, tint = if (processing) c.muted else onAccent, modifier = Modifier.size(52.dp))
                }
            }
        }
        if (capturing) {
            Column(Modifier.fillMaxWidth().testTag("ai_recording"), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (listening) "מקשיב... אפשר לדבר" else "מסיים את התמלול...", color = c.ink, style = com.example.ui.kis.KisType.label)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.ui.kis.KisButton("סיום הקלטה", onStop, Modifier.weight(1f), com.example.ui.kis.KisButtonKind.PRIMARY, enabled = listening, height = 48.dp, tag = "ai_stop")
                    com.example.ui.kis.KisButton("ביטול הקלטה", onCancel, Modifier.weight(1f), com.example.ui.kis.KisButtonKind.GHOST, height = 48.dp, tag = "ai_cancel_voice")
                }
            }
        } else Text("לחיצה ומדברים", color = c.ink, style = com.example.ui.kis.KisType.label)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
            Text("או להקליד", color = c.muted, style = com.example.ui.kis.KisType.caption)
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
        }
        com.example.ui.kis.KisTextField(text, onText, "תיאור המשמרת", Modifier.fillMaxWidth().testTag("ai_free_text_input"),
            placeholder = "כותבים כאן...", singleLine = false, minLines = 2,
            enabled = !processing && !capturing,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }))
        if (!capturing) Text("אחרי הפענוח רואים את כל הפרטים ומאשרים לפני שמירה.", color = c.muted, style = com.example.ui.kis.KisType.caption,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                        Text("${com.example.ui.kis.KisFormat.hours(shift.hours)} שעות · תעריף \u2066${WorkMoney.format(shift.hourlyRate, shift.currency)}\u2069 לשעה", color = c.ink)
                        if (shift.isGroup) Text("קבוצה: ${shift.groupMembers.joinToString(", ")}", color = c.muted)
                        if (shift.notes.isNotBlank()) Text(shift.notes, color = c.muted)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onSave, enabled = !saving, modifier = Modifier.testTag("ai_confirm_save")) { Text(if (saving) "שומר…" else "שמור את המשמרות", color = c.ink, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onEdit, enabled = !saving) { Text("חזרה לעריכה", color = c.muted) } })
}
