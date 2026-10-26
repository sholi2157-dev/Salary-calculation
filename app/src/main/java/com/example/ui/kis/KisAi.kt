package com.example.ui.kis

import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.api.GeminiParser
import com.example.api.PersonalAiKey
import com.example.data.WorkCategory
import com.example.ui.AiShiftInput
import com.example.ui.AiShiftReview
import com.example.ui.VoiceTranscriptSession
import com.example.ui.WorkViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * "AI" entry (a shift of the user's own, or with [team] a group shift): type or dictate, parse with the user's own Gemini key, review, then save.
 * Nothing is saved without the review. Without a personal key it stops with a clear message.
 */
@Composable
fun KisAiEntry(viewModel: WorkViewModel, categories: List<WorkCategory>, modifier: Modifier = Modifier, team: Boolean = false, onSaved: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var proposal by remember { mutableStateOf<List<GeminiParser.ParsedShift>?>(null) }
    val voice = remember { VoiceTranscriptSession({ text = it; error = null }, { error = it }) }
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }
    fun startVoice() {
        if (parsing) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { error = "זיהוי קולי אינו זמין במכשיר הזה. אפשר להקליד את התיאור."; return }
        error = null
        voice.begin(text)
        try { recognizer.startListening(recognizerIntent) } catch (_: Exception) { voice.interrupt(); error = "לא ניתן להתחיל הקלטה כרגע. אפשר לנסות שוב או להקליד." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else error = "הקלטה דורשת הרשאת מיקרופון. אפשר לאשר בהגדרות המכשיר או להקליד."
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(recognizer, lifecycle) {
        recognizer.setRecognitionListener(voice)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && voice.active) { voice.interrupt(); recognizer.cancel() } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); voice.interrupt(); recognizer.destroy() }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AiShiftInput(text = text, onText = { text = it; error = null }, capturing = voice.active, listening = voice.listening, processing = parsing, error = error,
            onRecord = {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
                else permission.launch(android.Manifest.permission.RECORD_AUDIO)
            },
            onStop = { voice.stop(); recognizer.stopListening() },
            onCancel = { voice.cancel(); recognizer.cancel() },
            onParse = {
                if (text.isNotBlank() && !parsing && !voice.active) scope.launch {
                    parsing = true; error = null
                    try {
                        val now = viewModel.categories.value
                        // From the group page the description is about a shift with other people; from the user's own page it never is.
                        val said = if (team) "משמרת קבוצתית, עם האנשים שעבדו איתי: $text" else text
                        val parsed = GeminiParser.parseNaturalLanguageToShifts(said, now.map { it.name }, now.associate { it.name to it.defaultRate }, PersonalAiKey.read(context, viewModel.owner.uid),
                            currencyOf = viewModel::categoryCurrency)
                        val result = if (team) parsed else parsed.map { it.copy(isGroup = false, groupMembers = emptyList()) }
                        if (result.isNotEmpty()) proposal = result else error = "לא הצלחנו להבין את פרטי המשמרת. נסה להוסיף תאריך, שעות ותעריף ולפענח שוב."
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "הפענוח לא הושלם. בדוק את החיבור ואת המפתח האישי בהגדרות, או תקן את התיאור ונסה שוב." }
                    finally { parsing = false }
                }
            })
    }
    proposal?.let { list ->
        AiShiftReview(list, onEdit = { if (!saving) proposal = null }, saving = saving, onSave = {
            if (!saving) {
                saving = true
                viewModel.addShifts(list) { ok -> saving = false; if (ok) { proposal = null; text = ""; onSaved(list.size) } }
            }
        })
    }
}
