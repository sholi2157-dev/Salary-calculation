package com.example.ui.kis

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.api.PersonalAiKey
import com.example.data.WorkCategory
import com.example.ui.CoachTarget
import com.example.ui.FeedbackForm
import com.example.ui.WorkUpdateSettings
import com.example.ui.WorkViewModel
import com.example.ui.coachTarget

@Composable
fun KisSettingsPage(viewModel: WorkViewModel, categories: List<WorkCategory>, onClose: () -> Unit, onReplayTour: () -> Unit, onSignIn: () -> Unit = {}) {
    val c = Kis.colors
    val settings = Kis.settings
    val context = LocalContext.current
    val prefs by viewModel.localPreferences.collectAsStateWithLifecycle()
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val serviceNotification by viewModel.serviceNotificationEnabled.collectAsStateWithLifecycle()
    val defaultCategory = prefs["defaultCategory"]?.takeIf { n -> categories.any { it.name == n } } ?: categories.firstOrNull()?.name ?: ""
    var pickingDefault by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var addingDefault by rememberSaveable { mutableStateOf(false) }
    var aiSheet by rememberSaveable { mutableStateOf(false) }
    var aiHelp by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    val hasKey = remember(aiSheet) { runCatching { PersonalAiKey.read(context, viewModel.owner.uid).isNotBlank() }.getOrDefault(false) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { viewModel.saveBackup(context, it) } }

    Column(Modifier.fillMaxSize().background(c.bg).testTag("settings_root")) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "חזרה", tint = c.ink, modifier = Modifier.size(48.dp).clip(CircleShape).kisClick(onClick = onClose).testTag("settings_close").padding(12.dp))
            Text("הגדרות", style = KisType.page, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp).navigationBarsPadding().imePadding()) {
            Group("עבודה") {
                Row_("מקום ברירת מחדל", "נבחר אוטומטית במשמרת חדשה ובטיימר", tag = "default_category_picker", onClick = { pickingDefault = true }) {
                    Text(defaultCategory, style = KisType.label, color = c.ink)
                }
                Column(Modifier.coachTarget(CoachTarget.CATEGORIES)) {
                    categories.forEach { cat ->
                        val w = workplaceOf(cat.name, categories)
                        Row_(cat.name, "${KisFormat.money(cat.defaultRate, viewModel.categoryCurrency(cat.name))} לשעה", tag = "category_row_${cat.name}", onClick = { editing = cat.name },
                            leading = { Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(w.brush)) }) {}
                    }
                    Row_("+ מקום עבודה", "שם, תעריף ומטבע", tag = "add_category_btn", onClick = { adding = true }) {}
                }
            }
            Group("ברירות מחדל") {
                Row_("מטבע ברירת מחדל", "למקומות ולמשמרות חדשים. כל משמרת שומרת את המטבע שלה") {
                    CurrencyToggle(defaultCurrency, { viewModel.updateDefaultCurrency(it) }, "settings_currency")
                }
                Row_("רטט במגע", "רטט קצר בלחיצות, ורטט שונה לשמירה ולמחיקה") { KisSwitch(settings.haptics, settings::updateHaptics, Modifier.testTag("haptics_switch"), label = "רטט במגע") }
            }
            Group("מראה") {
                Row_("ערכת צבעים", when (settings.theme) { ThemeChoice.SYSTEM -> "לפי הגדרת הטלפון"; ThemeChoice.DARK -> "כהה תמיד"; ThemeChoice.LIGHT -> "בהיר תמיד" }) {
                    KisSegmented(ThemeChoice.entries.map { it to it.label }, settings.theme, settings::updateTheme, tagPrefix = "theme")
                }
            }
            Group("התראות") {
                Row_("התראת משמרת פעילה", "טיימר וסכום שנצבר בהתראה קבועה בזמן שהשעון רץ") {
                    KisSwitch(serviceNotification, { viewModel.updateServiceNotificationEnabled(it) }, Modifier.testTag("service_notification_switch"), label = "התראת משמרת פעילה")
                }
            }
            Group("רישום במילים (AI)") {
                Row_("מפתח Gemini אישי", if (hasKey) "פעיל עם המפתח שלך" else "לא חובה. בלעדיו כל השאר עובד", tag = "ai_key_row", onClick = { aiSheet = true },
                    modifier = Modifier.coachTarget(CoachTarget.API)) {
                    Text("?", style = KisType.label, color = c.muted, modifier = Modifier.size(32.dp).clip(CircleShape).background(c.sunk).kisClick { aiHelp = true }
                        .semantics { contentDescription = "מה זה מפתח אישי" }.padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            if (BuildConfig.LOCAL_DISTRIBUTION) Group("עדכונים") {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("גרסה ${BuildConfig.VERSION_NAME}", style = KisType.caption, color = c.muted)
                    WorkUpdateSettings()
                }
            }
            Group("גיבוי ונתונים") {
                Text("הנתונים נשמרים רק במכשיר. כדאי לשמור גיבוי גם מחוץ לאפליקציה.", style = KisType.caption, color = c.muted, modifier = Modifier.padding(vertical = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    KisButton("שמירה לקובץ", { saveBackup.launch("salary-backup.json") }, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "save_backup_btn")
                    KisButton("שיתוף", { viewModel.shareExportData(context) }, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "share_backup_btn")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    KisButton("העתקה", { viewModel.copyExportToClipboard(context) }, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "copy_backup_btn")
                    KisButton("אקסל (CSV)", { viewModel.exportToCsvAndShare(context) }, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "csv_export_btn")
                }
                KisDivider()
                Text("ייבוא: הדבק שורות מאקסל (עם כותרות) או טקסט גיבוי. לפני השמירה תוצג בדיקה, ומשמרות שכבר קיימות לא יתווספו שוב.",
                    style = KisType.caption, color = c.muted, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                KisTextField(importText, { importText = it }, "הדבקת נתונים", Modifier.fillMaxWidth().heightIn(min = 90.dp).testTag("settings_import_text_field"), singleLine = false, minLines = 3)
                KisButton("בדיקה וייבוא", {
                    if (importText.isBlank()) Toast.makeText(context, "נא להדביק נתונים לייבוא", Toast.LENGTH_SHORT).show()
                    else if (viewModel.importDataFromString(context, importText)) importText = ""
                }, Modifier.fillMaxWidth().padding(vertical = 10.dp), height = 48.dp, tag = "settings_import_action_btn")
            }
            Group("משוב ותקלות") { Box(Modifier.padding(vertical = 10.dp)) { FeedbackForm() } }
            Group("הדרכה") {
                Row_("הצגת ההדרכה מחדש", "סיור קצר במקומות החשובים", tag = "replay_onboarding", onClick = onReplayTour) {}
            }
            if (BuildConfig.ACCOUNTS_ENABLED || viewModel.owner.uid != null) Group("חשבון") {
                val session by viewModel.currentUserSession.collectAsStateWithLifecycle()
                Row_(if (session == null) "התחברות" else "התנתקות", session?.email ?: "שימוש מקומי — ללא סנכרון", tag = "settings_sign_out_btn", onClick = {
                    if (session == null) onSignIn()
                    else android.app.AlertDialog.Builder(context).setTitle("התנתקות מהחשבון")
                        .setMessage("הנתונים השמורים יישארו בחשבון במכשיר. לאחר ההתנתקות יוצגו נתוני השימוש המקומי.")
                        .setNegativeButton("ביטול", null).setPositiveButton("התנתקות") { _, _ -> if (viewModel.signOut(context)) onClose() }.show()
                }) {}
                if (viewModel.owner.uid != null) Row_("העתקה חד־פעמית של הנתונים המקומיים", null, onClick = { viewModel.reviewLegacyData(context) }) {}
            }
            Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KisLogo(Modifier.size(28.dp))
                Text("${androidx.compose.ui.res.stringResource(com.example.R.string.app_name)} · ${BuildConfig.VERSION_NAME}", style = KisType.caption, color = c.muted)
            }
        }
    }

    if (pickingDefault) KisSheet(onDismiss = { pickingDefault = false }, tag = "default_category_sheet") {
        Text("מקום ברירת מחדל", style = KisType.title, color = c.ink)
        categories.forEach { cat ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).kisClick { viewModel.setDefaultCategory(cat.name); pickingDefault = false }
                .testTag("default_category_option_${cat.name}").padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(workplaceOf(cat.name, categories).brush))
                Text(cat.name, style = KisType.body, color = c.ink, modifier = Modifier.weight(1f))
                if (cat.name == defaultCategory) Icon(Icons.Rounded.Check, "נבחר", tint = c.ink)
            }
        }
        KisButton("הוספת מקום ובחירה בו", { pickingDefault = false; addingDefault = true; adding = true }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "default_category_add")
    }
    editing?.let { name -> categories.firstOrNull { it.name == name }?.let { cat ->
        EditWorkplaceSheet(cat, categories, viewModel, onDismiss = { editing = null })
    } }
    if (adding) NewWorkplaceSheet(viewModel, categories, defaultCurrency, makeDefault = addingDefault, onDismiss = { adding = false; addingDefault = false })
    if (aiSheet) AiKeySheet(viewModel.owner.uid, onDismiss = { aiSheet = false })
    if (aiHelp) KisSheet(onDismiss = { aiHelp = false }, tag = "ai_help") {
        Text("מה זה מפתח אישי?", style = KisType.title, color = c.ink)
        Text("עם מפתח אפשר לתאר עבודה במילים (\"אתמול במסעדה מ-5 עד 11\") ולקבל משמרות מוכנות לבדיקה לפני שמירה.", style = KisType.body, color = c.ink)
        Text("איך משיגים: נכנסים ל-aistudio.google.com עם חשבון גוגל ← Get API key ← Create API key ← מעתיקים ומדביקים כאן. המפתח של הפרויקט שלך, והשימוש נספר במכסה שלו.", style = KisType.body, color = c.ink)
        Text("המפתח נשמר מוצפן במכשיר הזה בלבד, לא מוצג במסכים, לא נכלל בגיבוי ולא נשלח לנו. אפשר להסיר אותו בכל רגע.", style = KisType.caption, color = c.muted)
        KisButton("הבנתי", { aiHelp = false }, Modifier.fillMaxWidth(), height = 48.dp)
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = Kis.colors
    Text(title, style = KisType.title, color = c.ink, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp).testTag("settings_section_$title").semantics { heading() })
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(horizontal = 16.dp, vertical = 4.dp), content = content)
}

/** A settings row: title + optional hint on the right, the control on the left (RTL). */
@Composable
private fun Row_(title: String, hint: String?, modifier: Modifier = Modifier, tag: String? = null, onClick: (() -> Unit)? = null,
                 leading: (@Composable () -> Unit)? = null, trailing: @Composable () -> Unit) {
    val c = Kis.colors
    Row(modifier.fillMaxWidth().heightIn(min = 58.dp).then(if (onClick != null) Modifier.kisClick(onClick = onClick) else Modifier)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
            if (hint != null) Text(hint, style = KisType.caption, color = c.muted)
        }
        trailing()
    }
}

@Composable
private fun EditWorkplaceSheet(cat: WorkCategory, categories: List<WorkCategory>, viewModel: WorkViewModel, onDismiss: () -> Unit) {
    val c = Kis.colors
    val settings = Kis.settings
    var name by rememberSaveable { mutableStateOf(cat.name) }
    var rate by rememberSaveable { mutableStateOf(KisFormat.hours(cat.defaultRate)) }
    var currency by rememberSaveable { mutableStateOf(viewModel.categoryCurrency(cat.name)) }
    var color by rememberSaveable { mutableIntStateOf(settings.workplaceIndex(cat.name, categories)) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val taken = categories.any { it.id != cat.id && it.name.trim().equals(name.trim(), true) }
    val rateValue = rate.toDoubleOrNull()?.takeIf { it >= 0 }
    KisSheet(onDismiss = onDismiss, tag = "edit_category_sheet") {
        Text("עריכת מקום", style = KisType.title, color = c.ink)
        KisTextField(name, { name = it }, "שם", Modifier.fillMaxWidth().testTag("edit_category_name"), isError = taken)
        if (taken) Text("כבר יש מקום בשם הזה", style = KisType.caption, color = c.danger)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisTextField(rate, { rate = it.filter { ch -> ch.isDigit() || ch == '.' } }, "תעריף לשעה", Modifier.weight(1f).testTag("edit_category_rate"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            CurrencyToggle(currency, { currency = it }, "edit_category_currency")
        }
        Text("שינוי התעריף חל רק על משמרות חדשות. סכומים שכבר נשמרו לא משתנים.", style = KisType.caption, color = c.muted)
        Text("צבע", style = KisType.label, color = c.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Workplaces.forEachIndexed { i, w ->
                Box(Modifier.size(40.dp).clip(CircleShape).background(w.brush).border(3.dp, if (i == color) c.ink else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .kisClick { color = i }.semantics { contentDescription = w.name + if (i == color) ", נבחר" else "" }.testTag("color_$i"))
            }
        }
        KisButton("שמירה", {
            val clean = name.trim()
            if (clean.isNotEmpty() && !taken && rateValue != null) {
                if (clean != cat.name) settings.renameWorkplaceColor(cat.name, clean)
                settings.setWorkplaceColor(clean, color)
                viewModel.editCategory(cat, clean, rateValue, currency)
                settings.haptic(KisSettings.Haptic.SUCCESS)
                onDismiss()
            }
        }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && !taken && rateValue != null, haptic = null, tag = "edit_category_save")
        if (categories.size > 1) KisButton("מחיקת המקום", { confirmDelete = true }, Modifier.fillMaxWidth(), KisButtonKind.DANGER, height = 48.dp, tag = "delete_category_${cat.name}")
    }
    if (confirmDelete) KisSheet(onDismiss = { confirmDelete = false }) {
        Text("למחוק את \"${cat.name}\"?", style = KisType.title, color = c.ink)
        Text("המשמרות שלו לא יימחקו: הן יעברו למקום ברירת המחדל, והסכומים שנשמרו יישארו כמו שהם.", style = KisType.body, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton("ביטול", { confirmDelete = false }, Modifier.weight(1f), KisButtonKind.GHOST)
            KisButton("מחיקה", { confirmDelete = false; settings.haptic(KisSettings.Haptic.WARN); viewModel.deleteCategory(cat); onDismiss() },
                Modifier.weight(1f), KisButtonKind.DANGER, haptic = null, tag = "confirm_delete_category")
        }
    }
}

@Composable
private fun NewWorkplaceSheet(viewModel: WorkViewModel, categories: List<WorkCategory>, defaultCurrency: String, makeDefault: Boolean = false, onDismiss: () -> Unit) {
    val c = Kis.colors
    var name by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("40") }
    var currency by rememberSaveable { mutableStateOf(defaultCurrency) }
    val taken = categories.any { it.name.trim().equals(name.trim(), true) }
    val rateValue = rate.toDoubleOrNull()?.takeIf { it >= 0 }
    KisSheet(onDismiss = onDismiss, tag = "add_place_sheet") {
        Text(if (makeDefault) "מקום חדש — יהיה ברירת המחדל" else "מקום עבודה חדש", style = KisType.title, color = c.ink)
        KisTextField(name, { name = it }, "שם המקום", Modifier.fillMaxWidth().testTag("category_input"), isError = taken)
        if (taken) Text("כבר יש מקום בשם הזה", style = KisType.caption, color = c.danger)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisTextField(rate, { rate = it.filter { ch -> ch.isDigit() || ch == '.' } }, "תעריף לשעה", Modifier.weight(1f).testTag("category_rate_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            CurrencyToggle(currency, { currency = it }, "new_category_currency")
        }
        KisButton("הוספה", { if (name.isNotBlank() && !taken && rateValue != null) { viewModel.addWorkplace(name, rateValue, currency, makeDefault); onDismiss() } },
            Modifier.fillMaxWidth(), enabled = name.isNotBlank() && !taken && rateValue != null, tag = "category_add_confirm")
    }
}

/** The key is entered here only; it is never shown again, saved in UI state or included in backups. */
@Composable
private fun AiKeySheet(ownerUid: String?, onDismiss: () -> Unit) {
    val c = Kis.colors
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var hasKey by remember { mutableStateOf(runCatching { PersonalAiKey.read(context, ownerUid).isNotBlank() }.getOrDefault(false)) }
    KisSheet(onDismiss = onDismiss, tag = "ai_key_sheet") {
        Text("מפתח Gemini אישי", style = KisType.title, color = c.ink)
        Text(if (hasKey) "מפתח כבר שמור. אפשר להחליף או להסיר אותו." else "מדביקים כאן מפתח מ-aistudio.google.com. הוא נשמר מוצפן במכשיר הזה בלבד.", style = KisType.body, color = c.muted)
        KisTextField(draft, { draft = it; status = null }, if (hasKey) "מפתח חדש" else "מפתח אישי", Modifier.fillMaxWidth().testTag("ai_key_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
        status?.let { Text(it, style = KisType.caption, color = c.muted) }
        KisButton("שמירת המפתח", {
            try {
                PersonalAiKey.save(context, draft, ownerUid); draft = ""
                Toast.makeText(context, "המפתח נשמר במכשיר. החיבור ייבדק בפענוח הראשון.", Toast.LENGTH_LONG).show()
                onDismiss()
            } catch (_: Exception) { status = "שמירת המפתח לא הצליחה. יש לבדוק את המפתח ולנסות שוב." }
        }, Modifier.fillMaxWidth(), enabled = draft.isNotBlank(), tag = "ai_key_save")
        if (hasKey) KisButton("הסרת המפתח", {
            try { PersonalAiKey.remove(context, ownerUid); hasKey = false; status = "המפתח הוסר. המשמרות וכל שאר הנתונים נשמרו." }
            catch (_: Exception) { status = "הסרת המפתח לא הצליחה. אפשר לנסות שוב." }
        }, Modifier.fillMaxWidth(), KisButtonKind.DANGER, height = 48.dp)
    }
}
