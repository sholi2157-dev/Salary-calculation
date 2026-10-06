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
import com.example.ui.theme.FormSurface
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
    Column(Modifier.fillMaxWidth().testTag("ai_experience").coachTarget(CoachTarget.AI), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(24.dp))
            Column {
                Text("משמרת במילים שלך", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("כתיבה או קול ← פענוח ← בדיקה ושמירה", color = Color(0xFFA5B4FC), style = MaterialTheme.typography.labelMedium)
            }
        }
        OutlinedTextField(value = text, onValueChange = onText, enabled = !processing && !capturing,
            modifier = Modifier.fillMaxWidth().testTag("ai_free_text_input"), minLines = 3, maxLines = 6,
            label = { Text("תיאור המשמרת / התמלול") },
            placeholder = { Text("אתמול עבדתי אצל פלוני מ־9 עד 5, חצי שעה הפסקה, 50 שקל לשעה.") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrRtl),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = FormSurface, unfocusedContainerColor = FormSurface,
                disabledContainerColor = FormSurface, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                disabledTextColor = Color.White, focusedBorderColor = Color(0xFF818CF8), unfocusedBorderColor = Color(0xFF555E87)))
        if (capturing) {
            val pulse = rememberInfiniteTransition(label = "voice")
            val alpha by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "listening glow")
            Surface(color = Color(0xFF252B4D), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("ai_recording")) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Mic, null, tint = Color(0xFFA5B4FC).copy(alpha = if (listening) alpha else 1f))
                        Text(if (listening) "מקשיב... אפשר לדבר" else "מסיים את התמלול...", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onStop, enabled = listening, modifier = Modifier.heightIn(min = 48.dp).testTag("ai_stop")) {
                            Icon(Icons.Filled.Stop, null, tint = Color(0xFFC7D2FE)); Text("סיום הקלטה", color = Color(0xFFC7D2FE))
                        }
                        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).testTag("ai_cancel_voice")) { Text("ביטול הקלטה", color = Color(0xFF94A3B8)) }
                    }
                }
            }
        } else {
            OutlinedButton(onClick = { keyboard?.hide(); focus.clearFocus(); onRecord() }, enabled = !processing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_mic_btn"), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.Mic, null, tint = Color(0xFFC7D2FE)); Spacer(Modifier.width(8.dp)); Text("הקלטת תיאור בקול", color = Color(0xFFC7D2FE))
            }
            Text("אפשר לערוך את הטקסט לפני הפענוח. שום משמרת לא נשמרת בלי אישור שלך.", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
        }
        if (error != null) Text(error, color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai_inline_error"))
        if (processing) {
            Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF292C58), Color(0xFF352358))), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF5553A5), RoundedCornerShape(14.dp)).padding(14.dp).testTag("ai_processing"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFFC7D2FE))
                    Text("מפענח את המשמרת...", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFA5B4FC), trackColor = Color(0xFF42416B))
                Text("מיד אפשר לבדוק את הפרטים לפני השמירה", color = Color(0xFFC7D2FE), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Button(onClick = { keyboard?.hide(); focus.clearFocus(); onParse() }, enabled = text.isNotBlank() && !capturing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_parse_button"), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366C4))) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("פענח עם AI", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AiShiftReview(shifts: List<ParsedShift>, onEdit: () -> Unit, onSave: () -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    AlertDialog(onDismissRequest = onEdit, containerColor = Color(0xFF171D34),
        icon = { Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFFA5B4FC)) },
        title = { Text("בדיקת הפענוח", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).testTag("ai_review"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("בדוק שהפרטים נכונים. לשינוי, חזור לתיאור ופענח שוב.", color = Color(0xFF94A3B8))
                shifts.forEach { shift ->
                    Column(Modifier.fillMaxWidth().background(FormSurface, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(shift.category, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(shift.date)), color = Color(0xFFC7D2FE), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                        Text("${shift.hours} שעות · תעריף \u2066${WorkMoney.format(shift.hourlyRate, shift.currency)}\u2069 לשעה", color = Color.White)
                        if (shift.isGroup) Text("קבוצה: ${shift.groupMembers.joinToString(", ")}", color = Color(0xFFC7D2FE))
                        if (shift.notes.isNotBlank()) Text(shift.notes, color = Color(0xFF94A3B8))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onSave, modifier = Modifier.testTag("ai_confirm_save")) { Text("שמור את המשמרות", color = Color(0xFFC7D2FE), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onEdit) { Text("חזרה לעריכה", color = Color(0xFF94A3B8)) } })
}
