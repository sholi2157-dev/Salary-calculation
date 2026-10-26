package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import com.example.DiagnosticLog
import com.example.FeedbackReport
import com.example.data.WorkEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    const val RECIPIENT = "sholi2157+salaryfeedback@gmail.com"
    data class Draft(val subject: String, val body: String)
    fun draft(type: String, message: String, name: String, code: Long, android: String, manufacturer: String, model: String) = Draft(
        "[$type] שכר עבודות $name",
        "סוג: $type\nגרסה: $name ($code)\nAndroid: $android\nמכשיר: $manufacturer $model\n\n${message.trim()}"
    )
    fun intent(draft: Draft) = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$RECIPIENT?subject=${Uri.encode(draft.subject)}&body=${Uri.encode(draft.body)}"))
    @Suppress("DEPRECATION") fun draft(context: Context, type: String, message: String): Draft {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return draft(type, message, info.versionName ?: "", if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(), Build.VERSION.RELEASE, Build.MANUFACTURER, Build.MODEL)
    }
}
/**
 * With the relay set (FeedbackReport), "שליחה" sends the report straight to the owner's mailbox: the text, version,
 * device, the [state] allowlist, the local event trail and an optional picked screenshot. Without it, or when sending
 * fails, the mail-app draft (version and device only) stays available.
 */
@Composable fun FeedbackForm(message: String, onMessage: (String) -> Unit, image: String?, onImage: (String?) -> Unit,
                             state: () -> Map<String, Any> = { emptyMap() }, onSent: () -> Unit = {}) {
    val context = LocalContext.current
    val c = Kis.colors
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf("תקלה") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var fallback by remember { mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImage(uri.toString())
    }
    fun mailDraft() {
        try { context.startActivity(SalaryFeedback.intent(SalaryFeedback.draft(context, type, message))) }
        catch (_: android.content.ActivityNotFoundException) { fallback = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("תקלה", "הצעה", "הערה").forEach { kind -> KisChip(kind, selected = type == kind, onClick = { type = kind }, tag = "feedback_type_$kind") }
        }
        KisTextField(message, { onMessage(it); failed = false }, "מה קרה, או מה תרצה להציע?", Modifier.fillMaxWidth().testTag("feedback_message"), singleLine = false, minLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default))
        if (FeedbackReport.enabled) {
            if (image == null) KisButton("צירוף צילום מסך", {
                pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 44.dp, tag = "feedback_attach")
            else Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("צילום מסך מצורף", style = KisType.label, color = c.ink, modifier = Modifier.weight(1f).testTag("feedback_attached"))
                KisButton("הסרה", { onImage(null) }, kind = KisButtonKind.GHOST, height = 40.dp, tag = "feedback_attach_remove")
            }
            Text("יישלחו התיאור, הגרסה, פרטי המכשיר ויומן פעולות קצר. סכומים, מקומות עבודה, שמות ומפתחות לא נשלחים.", style = KisType.caption, color = c.muted)
            KisButton(if (sending) "שולח…" else "שליחה", {
                sending = true; failed = false
                scope.launch {
                    val (name, code) = FeedbackReport.version(context)
                    val report = FeedbackReport.build(type, message, name, code, FeedbackReport.device(context), runCatching { state() }.getOrDefault(emptyMap()),
                        DiagnosticLog.snapshot(), System.currentTimeMillis())
                    image?.let { u -> withContext(Dispatchers.IO) { FeedbackReport.screenshot(context, Uri.parse(u)) } }?.let { report.put("screenshot", it) }
                    val ok = FeedbackReport.send(report)
                    sending = false
                    DiagnosticLog.event(if (ok) "feedback_sent" else "feedback_failed")
                    if (ok) {
                        onMessage(""); onImage(null)
                        android.widget.Toast.makeText(context, "המשוב נשלח. תודה!", android.widget.Toast.LENGTH_SHORT).show()
                        onSent()
                    } else failed = true
                }
            }, Modifier.fillMaxWidth(), enabled = message.isNotBlank() && !sending, height = 48.dp, tag = "feedback_send")
            if (failed) {
                Text("השליחה לא הצליחה. אפשר לנסות שוב, או לשלוח כטיוטת מייל.", style = KisType.caption, color = c.ink, modifier = Modifier.testTag("feedback_failed"))
                KisButton("פתיחת טיוטת מייל", { mailDraft() }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "feedback_mail")
            }
        } else {
            Text("יצורפו רק גרסת האפליקציה ופרטי המכשיר. נתוני העבודה לא מצורפים.", style = KisType.caption, color = c.muted)
            KisButton("פתיחת טיוטת מייל", { mailDraft() }, Modifier.fillMaxWidth(), enabled = message.isNotBlank(), height = 48.dp, tag = "feedback_send")
        }
        if (fallback) {
            Text("לא נמצאה אפליקציית מייל. אפשר להעתיק ולשלוח ל־${SalaryFeedback.RECIPIENT}", style = KisType.caption, color = c.ink)
            KisButton("העתקת המשוב והכתובת", {
                val draft = SalaryFeedback.draft(context, type, message)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("משוב", "${SalaryFeedback.RECIPIENT}\n${draft.subject}\n\n${draft.body}"))
                android.widget.Toast.makeText(context, "המשוב והכתובת הועתקו", android.widget.Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp)
        }
    }
}
