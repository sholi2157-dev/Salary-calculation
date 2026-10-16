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
import com.example.data.WorkEntry
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
@Composable fun FeedbackForm() {
    val context = LocalContext.current
    val c = Kis.colors
    var type by rememberSaveable { mutableStateOf("תקלה") }
    var message by rememberSaveable { mutableStateOf("") }
    var fallback by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("תקלה", "הצעה", "הערה").forEach { kind -> KisChip(kind, selected = type == kind, onClick = { type = kind }, tag = "feedback_type_$kind") }
        }
        KisTextField(message, { message = it }, "מה קרה, או מה תרצה להציע?", Modifier.fillMaxWidth().testTag("feedback_message"), singleLine = false, minLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default))
        Text("יצורפו רק גרסת האפליקציה ופרטי המכשיר. נתוני העבודה לא מצורפים.", style = KisType.caption, color = c.muted)
        KisButton("פתיחת טיוטת מייל", {
            try { context.startActivity(SalaryFeedback.intent(SalaryFeedback.draft(context, type, message))) }
            catch (_: android.content.ActivityNotFoundException) { fallback = true }
        }, Modifier.fillMaxWidth(), enabled = message.isNotBlank(), height = 48.dp, tag = "feedback_send")
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
