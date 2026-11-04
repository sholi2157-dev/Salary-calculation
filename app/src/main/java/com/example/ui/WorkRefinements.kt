package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.FeedbackReport
import com.example.data.WorkEntry
import kotlinx.coroutines.launch
import com.example.data.WorkMoney
import com.example.ui.kis.Kis
import com.example.ui.kis.KisButton
import com.example.ui.kis.KisButtonKind
import com.example.ui.kis.KisChip
import com.example.ui.kis.KisTextField
import com.example.ui.kis.KisType

object SummaryPages {
    fun currencies(entries: List<WorkEntry>) = listOf("₪", "$").filter { c -> entries.any { it.currency == c } }
    fun compact(entries: List<WorkEntry>, separator: String = "   |   ") = WorkMoney.totals(entries).entries.joinToString(separator) {
        "\u2066${WorkMoney.format(it.value, it.key)}\u2069"
    }.ifEmpty { "0.00" }
}

object SalaryFeedback {
    const val RECIPIENT = FeedbackReport.RECIPIENT
    data class Draft(val subject: String, val body: String)
    fun draft(report: org.json.JSONObject) = Draft("Android feedback", report.toString(2))
    fun intent(draft: Draft) = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$RECIPIENT?subject=${Uri.encode(draft.subject)}&body=${Uri.encode(draft.body)}"))
}

/** Relay and email fallback use exactly the same allowlisted JSON body. */
@Composable fun FeedbackForm(message: String, onMessage: (String) -> Unit, contactEmail: String,
                             onContactEmail: (String) -> Unit, onSent: () -> Unit = {}) {
    val context = LocalContext.current
    val c = Kis.colors
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf("תקלה") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var fallback by remember { mutableStateOf(false) }
    fun report(): org.json.JSONObject {
        val (name, code) = FeedbackReport.version(context)
        val language = context.resources.configuration.locales[0].toLanguageTag()
        return FeedbackReport.build(type, message, name, code, language, System.currentTimeMillis(), contactEmail)
    }
    fun mailDraft() {
        try { context.startActivity(SalaryFeedback.intent(SalaryFeedback.draft(report()))) }
        catch (_: android.content.ActivityNotFoundException) { fallback = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("תקלה", "הצעה", "הערה").forEach { kind -> KisChip(kind, selected = type == kind, onClick = { type = kind }, tag = "feedback_type_$kind") }
        }
        KisTextField(message, { onMessage(FeedbackReport.clean(it, FeedbackReport.MAX_MESSAGE)); failed = false }, "מה קרה, או מה תרצו להציע?", Modifier.fillMaxWidth().testTag("feedback_message"), singleLine = false, minLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default))
        Text("עד 4,000 תווים. נא לא לכלול מידע אישי או נתוני שכר.", style = KisType.caption, color = c.muted)
        KisTextField(contactEmail, { onContactEmail(FeedbackReport.clean(it, FeedbackReport.MAX_CONTACT).replace("\n", "")) },
            "אימייל לחזרה (לא חובה)", Modifier.fillMaxWidth().testTag("feedback_contact"))
        Text("יישלחו הטקסט שהוקלד, גרסה, מסך, שפה וזמן. נתוני העבודה לא מצורפים.", style = KisType.caption, color = c.muted)
        if (FeedbackReport.enabled) {
            KisButton(if (sending) "שליחה…" else "שליחה", {
                sending = true; failed = false
                scope.launch {
                    val ok = FeedbackReport.send(report())
                    sending = false
                    if (ok) {
                        onMessage(""); onContactEmail("")
                        android.widget.Toast.makeText(context, "המשוב נשלח. תודה!", android.widget.Toast.LENGTH_SHORT).show()
                        onSent()
                    } else failed = true
                }
            }, Modifier.fillMaxWidth(), enabled = message.isNotBlank() && !sending, height = 48.dp, tag = "feedback_send")
            if (failed) {
                Text("השליחה לא הצליחה. אפשר לנסות שוב מאוחר יותר, או לפתוח טיוטת מייל.", style = KisType.caption, color = c.ink, modifier = Modifier.testTag("feedback_failed"))
                KisButton("פתיחת טיוטת מייל", { mailDraft() }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "feedback_mail")
            }
        } else {
            KisButton("פתיחת טיוטת מייל", { mailDraft() }, Modifier.fillMaxWidth(), enabled = message.isNotBlank(), height = 48.dp, tag = "feedback_send")
        }
        if (fallback) Text("לא נמצאה אפליקציית מייל. אפשר לנסות את השליחה שוב מאוחר יותר.", style = KisType.caption, color = c.ink)
    }
}
