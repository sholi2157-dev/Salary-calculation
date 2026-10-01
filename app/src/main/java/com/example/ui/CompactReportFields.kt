package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.WorkPalette
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.data.WorkCategory
import com.example.ui.theme.FormSurface
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = WorkPalette.SecondaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
}

@Composable
fun CompactNumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, error: Boolean = false, inputTag: String? = null) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FieldLabel(label)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, textDirection = TextDirection.Ltr, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
            cursorBrush = SolidColor(WorkPalette.AccentText),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label }
                .then(if (inputTag != null) Modifier.testTag(inputTag) else Modifier),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(WorkPalette.Control, RoundedCornerShape(10.dp))
                    .border(1.dp, if (error) Color(0xFFEF4444) else WorkPalette.Outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) { inner() }
            }
        )
        if (error) Text("ערך לא תקין", color = Color(0xFFEF4444), fontSize = 11.sp)
    }
}

@Composable
private fun PickerField(label: String, value: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FieldLabel(label)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
            .background(WorkPalette.Control, RoundedCornerShape(10.dp))
            .border(1.dp, WorkPalette.Outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(value, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrRtl, textAlign = TextAlign.Center), maxLines = 1)
            Icon(Icons.Outlined.ArrowDropDown, null, tint = WorkPalette.SecondaryText, modifier = Modifier.size(16.dp))
        }
    }
}

/** Layout-only values: calculations, validation and drafts remain owned by DashboardScreen. */
data class ReportFieldValues(
    val date: Long, val manual: Boolean, val group: Boolean, val start: String, val end: String,
    val hours: String, val breakMinutes: String, val rate: String, val currency: String,
    val category: String, val notes: String, val separateRates: Boolean,
    val employerRate: String, val workerRate: String, val hoursError: Boolean, val rateError: Boolean
)

@Composable
fun CompactReportFields(
    values: ReportFieldValues, categories: List<WorkCategory>,
    onDate: (Long) -> Unit, onStart: (String) -> Unit, onEnd: (String) -> Unit,
    onHours: (String) -> Unit, onBreak: (String) -> Unit, onRate: (String) -> Unit,
    onCurrency: (String) -> Unit, onCategory: (WorkCategory) -> Unit, onNotes: (String) -> Unit,
    onAddCategory: () -> Unit, onDeleteCategory: () -> Unit, onSeparateRates: () -> Unit,
    onEmployerRate: (String) -> Unit, onWorkerRate: (String) -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun time(value: String, fallback: Int, onPicked: (String) -> Unit) {
        val t = value.split(":")
        TimePickerDialog(context, { _, h, m -> onPicked(String.format(Locale.US, "%02d:%02d", h, m)) },
            t.getOrNull(0)?.toIntOrNull() ?: fallback, t.getOrNull(1)?.toIntOrNull() ?: 0, true).show()
    }
    Column(Modifier.fillMaxWidth().testTag("compact_report_fields"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerField("תאריך", SimpleDateFormat("dd.MM", Locale.US).format(Date(values.date)), "report_date", Modifier.weight(1f)) {
                val cal = Calendar.getInstance().apply { timeInMillis = values.date }
                DatePickerDialog(context, { _, y, m, d ->
                    onDate(Calendar.getInstance().apply { set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d) }.timeInMillis)
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            if (!values.manual) {
                PickerField("כניסה", values.start, "report_start", Modifier.weight(1f)) { time(values.start, 9, onStart) }
                PickerField("יציאה", values.end, "report_end", Modifier.weight(1f)) { time(values.end, 17, onEnd) }
            } else {
                val h = values.hours.toDoubleOrNull() ?: 0.0
                PickerField("שעות עבודה", com.example.formatCleanHours(h), "report_hours", Modifier.weight(1f)) {
                    TimePickerDialog(context, { _, hours, minutes -> onHours(String.format(Locale.US, "%.2f", hours + minutes / 60.0)) },
                        h.toInt(), Math.round((h - h.toInt()) * 60).toInt(), true).show()
                }
            }
        }
        if (values.hoursError) Text("נא להזין כמות שעות תקינה", color = Color(0xFFEF4444), fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val mins = values.breakMinutes.toIntOrNull() ?: 0
            PickerField("הפסקה", "$mins דק׳", "report_break", Modifier.weight(1f)) {
                TimePickerDialog(context, { _, h, m -> onBreak((h * 60 + m).toString()) }, mins / 60, mins % 60, true).show()
            }
            CompactNumberField("תעריף לשעה", values.rate, onRate, Modifier.weight(1f), values.rateError, inputTag = "add_rate_input")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FieldLabel("מטבע")
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("report_currency"), contentPadding = PaddingValues(4.dp), shape = RoundedCornerShape(10.dp)) {
                        Text(values.currency, color = Color.White)
                        Icon(Icons.Outlined.ArrowDropDown, null, tint = WorkPalette.SecondaryText, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(FormSurface)) {
                        listOf("₪", "$").forEach { c -> DropdownMenuItem(text = { Text(c, color = Color.White) }, onClick = { onCurrency(c); expanded = false }) }
                    }
                }
            }
        }
        if (values.group) {
            TextButton(onClick = onSeparateRates, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(if (values.separateRates) "סגור תעריפים נפרדים" else "תעריפים נפרדים לקבוצה", color = WorkPalette.AccentText)
            }
            if (values.separateRates) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactNumberField("תעריף מעסיק", values.employerRate, onEmployerRate, Modifier.weight(1f))
                CompactNumberField("תעריף לעובד", values.workerRate, onWorkerRate, Modifier.weight(1f))
            }
        }
        FieldLabel("מעסיק / קטגוריה")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("report_category")
                    .background(WorkPalette.Control, RoundedCornerShape(10.dp)).border(1.dp, WorkPalette.Outline, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(values.category, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.ArrowDropDown, null, tint = WorkPalette.SecondaryText)
                }
                DropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(FormSurface)) {
                    categories.forEach { cat -> DropdownMenuItem(text = { Text(cat.name, color = Color.White) }, onClick = { onCategory(cat); expanded = false }) }
                }
            }
            IconButton(onClick = onAddCategory) { Icon(Icons.Outlined.Add, "הוסף מעסיק", tint = WorkPalette.AccentText) }
            IconButton(onClick = onDeleteCategory) { Icon(Icons.Outlined.Delete, "מחק מעסיק", tint = Color(0xFFEF4444)) }
        }
        OutlinedTextField(value = values.notes, onValueChange = onNotes,
            label = { Text("הערות") }, placeholder = { Text("מה עשית במשמרת?") }, maxLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("notes_input"),
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = FormSurface, unfocusedContainerColor = FormSurface, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
    }
}
