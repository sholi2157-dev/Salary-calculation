package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WorkEntry
import com.example.data.WorkMoney
import com.example.ui.theme.FormSurface
import java.util.Calendar
import java.util.Locale

object SummaryPages {
    fun currencies(entries: List<WorkEntry>) = listOf("₪", "$").filter { c -> entries.any { it.currency == c } }
    fun compact(entries: List<WorkEntry>, separator: String = "   |   ") = WorkMoney.totals(entries).entries.joinToString(separator) {
        "\u2066${WorkMoney.format(it.value, it.key)}\u2069"
    }.ifEmpty { "0.00" }
}

@Composable fun SettingsSectionHeader(title: String, expanded: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).testTag("settings_title_$title"), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "צמצם" else "הרחב", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("settings_chevron_$title"))
    }
}
@Composable fun SettingsSection(title: String, expanded: Boolean, toggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(
        onClick = toggle,
        modifier = Modifier.testTag("settings_section_$title"),
        colors = CardDefaults.cardColors(containerColor = FormSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSectionHeader(title, expanded)
            if (expanded) content()
        }
    }
}
@Composable fun CompactHistoryTotal(entries: List<WorkEntry>, modifier: Modifier = Modifier) {
    Text(
        text = "סה\"כ מוצג:\n${SummaryPages.compact(entries, separator = "\n")}",
        color = Color.White,
        modifier = modifier.testTag("history_total"),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    )
}
@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
            color = Color.White,
            maxLines = 1
        )
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable fun WorkSummaryCarousel(entries: List<WorkEntry>, defaultCurrency: String) {
    val currencies = SummaryPages.currencies(entries)
    val pager = rememberPagerState(pageCount = { 1 + currencies.size })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(
            state = pager,
            pageSize = androidx.compose.foundation.pager.PageSize.Fill,
            modifier = Modifier.fillMaxWidth().testTag("summary_pager")
        ) { page ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(232.dp)
                    .testTag("summary_page_$page"),
                colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (page == 0) {
                        Text("סיכום כללי", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        val now = Calendar.getInstance()
                        val today = entries.filter {
                            Calendar.getInstance().apply { timeInMillis = it.date }.let {
                                it.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                                    it.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            SummaryMetric("משמרות", entries.size.toString(), Modifier.weight(1f))
                            SummaryMetric("שעות עבודה", String.format(Locale.US, "%.2f", entries.sumOf { it.hours }), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            SummaryMetric("משמרות היום", today.size.toString(), Modifier.weight(1f))
                            SummaryMetric("שעות היום", String.format(Locale.US, "%.2f", today.sumOf { it.hours }), Modifier.weight(1f))
                        }
                    } else {
                        val currency = currencies[page - 1]
                        val rows = entries.filter { it.currency == currency }
                        fun money(selected: List<WorkEntry>) = WorkMoney.format(selected.sumOf { it.totalEarnings }, currency)
                        val now = Calendar.getInstance()
                        val month = rows.filter {
                            Calendar.getInstance().apply { timeInMillis = it.date }.let {
                                it.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                                    it.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                            }
                        }
                        val week = rows.filter {
                            Calendar.getInstance().apply { timeInMillis = it.date }.let {
                                it.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                                    it.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR)
                            }
                        }
                        Text(if (currency == "₪") "סיכום בשקלים" else "סיכום בדולרים", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            SummaryMetric("סה״כ הכנסות", money(rows), Modifier.weight(1f))
                            SummaryMetric("שולם", money(rows.filter { it.isPaid }), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            SummaryMetric("ממתין לתשלום", money(rows.filterNot { it.isPaid }), Modifier.weight(1f))
                            SummaryMetric("שעות עבודה", String.format(Locale.US, "%.2f", rows.sumOf { it.hours }), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            SummaryMetric("השבוע", money(week), Modifier.weight(1f))
                            SummaryMetric("החודש", money(month), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(1 + currencies.size) { index ->
                TextButton(
                    onClick = {},
                    enabled = false,
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(if (index == pager.currentPage) "●" else "○", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
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
    var type by rememberSaveable { mutableStateOf("תקלה") }
    var message by rememberSaveable { mutableStateOf("") }
    var fallback by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("תקלה", "הצעה", "הערה").forEach { kind -> FilterChip(selected = type == kind, onClick = { type = kind }, label = { Text(kind) }) }
        }
        OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("מה קרה, או מה תרצה להציע?") }, modifier = Modifier.fillMaxWidth().testTag("feedback_message"), minLines = 3,
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = FormSurface, unfocusedContainerColor = FormSurface))
        Text("יצורפו רק גרסת האפליקציה ופרטי המכשיר. נתוני העבודה לא מצורפים.", style = MaterialTheme.typography.bodySmall)
        Button(enabled = message.isNotBlank(), onClick = {
            try { context.startActivity(SalaryFeedback.intent(SalaryFeedback.draft(context, type, message))) }
            catch (_: android.content.ActivityNotFoundException) { fallback = true }
        }) { Text("פתח טיוטת דוא״ל") }
        if (fallback) {
            Text("לא נמצאה אפליקציית דוא״ל. אפשר להעתיק ולשלוח ל־${SalaryFeedback.RECIPIENT}")
            TextButton(onClick = {
                val draft = SalaryFeedback.draft(context, type, message)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("משוב", "${SalaryFeedback.RECIPIENT}\n${draft.subject}\n\n${draft.body}"))
                android.widget.Toast.makeText(context, "המשוב והכתובת הועתקו", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("העתק משוב וכתובת") }
        }
    }
}
