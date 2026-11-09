package com.example.ui.kis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import java.util.Calendar

/** The Android share sheet and clipboard. All texts come from WorkShareText (docs/v2-sharing.md). */
object KisShare {
    /** Dates are Hebrew with the civil date beside them (owner feedback 2.3): "כ״ח חשוון (8/11)"; another year adds both years. */
    fun shortDate(millis: Long): String = com.example.data.HebrewDate.label(millis)

    /** The native Android share sheet (WhatsApp and every other installed target). False when no app can receive text (the caller then offers copying instead). */
    fun share(context: Context, text: String, title: String = "שיתוף"): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        return try { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
        catch (_: android.content.ActivityNotFoundException) { false }
    }

    /** Shares, or copies with a message when nothing on the phone can receive text. Never sends by itself. */
    fun shareOrCopy(context: Context, text: String, title: String, onMessage: (String) -> Unit) {
        if (!share(context, text, title)) { copy(context, text); onMessage("אין אפליקציה לשיתוף · הטקסט הועתק") }
    }

    fun copy(context: Context, text: String, label: String = "משמרות") {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

/**
 * Sharing a report of money owed to the user. When the profile has payment details (Zelle, Bit, bank), a short question
 * first: add them to this message or not. Without details it shares at once, as before.
 */
@androidx.compose.runtime.Composable
fun rememberOwedShare(onMessage: (String) -> Unit): (text: String, title: String) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = Kis.settings
    val c = Kis.colors
    var pending by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null) }
    pending?.let { (text, title) ->
        val lines = settings.paymentLines()
        KisSheet(onDismiss = { pending = null }, tag = "pay_prompt") {
            androidx.compose.material3.Text("לצרף את פרטי התשלום שלך?", style = KisType.title, color = c.ink)
            androidx.compose.material3.Text(lines.joinToString("\n"), style = KisType.body, color = c.muted)
            KisButton("כן, לצרף", { pending = null; KisShare.shareOrCopy(context, com.example.data.WorkShareText.withPayment(text, lines), title, onMessage) },
                androidx.compose.ui.Modifier.fillMaxWidth(), tag = "pay_with")
            KisButton("בלי", { pending = null; KisShare.shareOrCopy(context, text, title, onMessage) },
                androidx.compose.ui.Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "pay_without")
        }
    }
    return { text, title -> if (settings.paymentLines().isEmpty()) KisShare.shareOrCopy(context, text, title, onMessage) else pending = text to title }
}
