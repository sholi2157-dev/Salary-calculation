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
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
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
fun KisSettingsPage(viewModel: WorkViewModel, categories: List<WorkCategory>, onClose: () -> Unit, onReplayTour: () -> Unit, onSignIn: () -> Unit = {}, bottomInset: Dp = 0.dp,
                    onRecaps: () -> Unit = {}) {
    val c = Kis.colors
    val settings = Kis.settings
    val context = LocalContext.current
    val prefs by viewModel.localPreferences.collectAsStateWithLifecycle()
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val defaultCategory = prefs["defaultCategory"]?.takeIf { n -> categories.any { it.name == n } } ?: categories.firstOrNull()?.name ?: ""
    var pickingDefault by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var addingDefault by rememberSaveable { mutableStateOf(false) }
    var aiSheet by rememberSaveable { mutableStateOf(false) }
    var aiHelp by rememberSaveable { mutableStateOf(false) }
    var feedbackOpen by rememberSaveable { mutableStateOf(false) }
    // Focus leaves the text field before the form folds: the device never settled when a focused field was removed.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // Kept here, not in the form, so folding the form keeps the draft.
    var feedbackText by rememberSaveable { mutableStateOf("") }
    var feedbackImage by rememberSaveable { mutableStateOf<String?>(null) }
    // Not saveable: a pasted backup can be far larger than what Android may keep in saved state.
    var importText by remember { mutableStateOf("") }
    val hasKey = remember(aiSheet) { runCatching { PersonalAiKey.read(context, viewModel.owner.uid).isNotBlank() }.getOrDefault(false) }
    // Weekly plans and their reminders (device-only settings, see PlanSettings).
    val plan = viewModel.planSettings
    val series by viewModel.series.collectAsStateWithLifecycle()
    val plans by viewModel.occurrences.collectAsStateWithLifecycle()
    var seriesSheet by rememberSaveable { mutableStateOf(false) }
    var autoConfirm by remember { mutableStateOf(plan.autoConfirm) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { viewModel.saveBackup(context, it) } }

    Box(Modifier.fillMaxSize().background(c.bg)) {
    KisGlow(Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize().testTag("settings_root")) {
        Text("הגדרות", style = KisType.page, color = c.ink, modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 2.dp).semantics { heading() })
        // With the keyboard open the floating navigation is covered anyway: pad for the keyboard only, not both.
        val imeOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        // The scroll area ends above the floating navigation, so no row can sit (and be tapped) under it.
        Column(Modifier.weight(1f).padding(bottom = if (imeOpen) 0.dp else bottomInset).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp).imePadding()) {
            // First, so a problem can be reported without scrolling past everything else. The form unfolds in place:
            // inside a bottom sheet its multi-line field never let the device settle after Enter.
            Group("משוב ותקלות") {
                Row_("דיווח על תקלה או הצעה", null, tag = "feedback_row", onClick = { focusManager.clearFocus(force = true); feedbackOpen = !feedbackOpen }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.muted,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (feedbackOpen) -90f else 0f })
                }
                if (feedbackOpen) Box(Modifier.padding(bottom = 10.dp)) {
                    FeedbackForm(feedbackText, { feedbackText = it }, feedbackImage, { feedbackImage = it }, state = {
                        mapOf("page" to "settings", "theme" to settings.theme.key, "haptics" to settings.haptics, "accounts" to BuildConfig.ACCOUNTS_ENABLED,
                            "signedIn" to (viewModel.owner.uid != null), "entries" to viewModel.entries.value.size, "categories" to categories.size,
                            "weeklySeries" to series.size, "personalAiKey" to hasKey)
                    }, onSent = { focusManager.clearFocus(force = true); feedbackOpen = false })
                }
            }
            Group("עבודה") {
                Row_("מקום ברירת מחדל", null, tag = "default_category_picker",
                    modifier = Modifier.coachTarget(CoachTarget.CATEGORIES), onClick = { pickingDefault = true },
                    leading = { if (defaultCategory.isNotEmpty()) Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(workplaceOf(defaultCategory, categories).brush)) }) {
                    Text(defaultCategory, style = KisType.label, color = c.ink, maxLines = 1)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
                Row_("מטבע ברירת מחדל", null, info = "למקומות ולמשמרות חדשים. כל משמרת שומרת את המטבע שלה.") {
                    CurrencyToggle(defaultCurrency, { viewModel.updateDefaultCurrency(it) }, "settings_currency")
                }
                KisDivider()
                Row_("משמרות קבועות", null, info = "משמרות שחוזרות כל שבוע. יוצרים אותן במסך ההוספה: \"חוזרת כל שבוע\". כשהזמן נגמר הן נרשמות לבד, ותמיד אפשר לבטל.",
                    tag = "series_row", onClick = { seriesSheet = true }) {
                    if (series.isNotEmpty()) Text("${series.size}", style = KisType.label, color = c.muted)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
            }
            Group("מראה ותחושה") {
                Row_("ערכת צבעים", null) {
                    KisSegmented(ThemeChoice.entries.map { it to it.label }, settings.theme, settings::updateTheme, tagPrefix = "theme")
                }
                Row_("רטט במגע", null) { KisSwitch(settings.haptics, settings::updateHaptics, Modifier.testTag("haptics_switch"), label = "רטט במגע") }
            }
            Group("הסיכומים שלי") {
                Row_("כל הסיכומים", null, tag = "recaps_row", onClick = onRecaps, info = "כל סיכום חודשי ושנתי שכבר נפתח נשמר כאן. סיכום חודשי נפתח ב-30 לחודש ומופיע בדף הבית עוד 4 ימים.") {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
            }
            Group("התראות") { NotificationSwitches(viewModel) }
            Group("עזרה וכלים") {
                Row_("רישום במילים (AI)", if (hasKey) "פעיל" else null, tag = "ai_key_row", onClick = { aiSheet = true },
                    modifier = Modifier.coachTarget(CoachTarget.API)) {
                    Text("?", style = KisType.label, color = c.muted, modifier = Modifier.size(32.dp).clip(CircleShape).background(c.sunk).kisClick { aiHelp = true }
                        .semantics { contentDescription = "מה זה מפתח אישי" }.padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                KisDivider()
                Row_("הצגת ההדרכה מחדש", null, tag = "replay_onboarding", onClick = onReplayTour) {}
                if (BuildConfig.LOCAL_DISTRIBUTION) {
                    KisDivider()
                    Column(Modifier.padding(vertical = 8.dp).testTag("settings_updates")) {
                        Text("עדכונים", style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
                        WorkUpdateSettings()
                    }
                }
            }
            Group("גיבוי ונתונים", info = (if (BuildConfig.ACCOUNTS_ENABLED) "הנתונים נשמרים בטלפון ובחשבון שלך. " else "הנתונים נשמרים רק במכשיר. ") + "כדאי לשמור גיבוי גם מחוץ לאפליקציה. גיבוי מהאפליקציה עובר לאתר, וגיבוי מהאתר עובר לאפליקציה.") {
                Row(Modifier.padding(top = 10.dp)) {}
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    KisButton("שמירה לקובץ", { saveBackup.launch("salary-backup.json") }, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "save_backup_btn")
                    KisButton("שיתוף", { viewModel.shareExportData(context) }, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "share_backup_btn")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    KisButton("העתקה", { viewModel.copyExportToClipboard(context) }, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "copy_backup_btn")
                    KisButton("אקסל (CSV)", { viewModel.exportToCsvAndShare(context) }, Modifier.weight(1f), KisButtonKind.GHOST, height = 48.dp, tag = "csv_export_btn")
                }
                KisDivider()
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ייבוא", style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
                    InfoDot("ייבוא", "מדביקים שורות מאקסל (עם כותרות) או טקסט גיבוי. לפני השמירה תוצג בדיקה, ומשמרות שכבר קיימות לא יתווספו שוב.")
                }
                KisTextField(importText, { importText = it }, "הדבקת נתונים", Modifier.fillMaxWidth().heightIn(min = 90.dp).testTag("settings_import_text_field"), singleLine = false, minLines = 3)
                KisButton("בדיקה וייבוא", {
                    if (importText.isBlank()) Toast.makeText(context, "נא להדביק נתונים לייבוא", Toast.LENGTH_SHORT).show()
                    else if (viewModel.importDataFromString(context, importText)) importText = ""
                }, Modifier.fillMaxWidth().padding(vertical = 10.dp), height = 48.dp, tag = "settings_import_action_btn")
            }
            // The name and the Google account in one compact place; set once and rarely touched again, so it sits last.
            Group("חשבון", info = if (BuildConfig.ACCOUNTS_ENABLED) "המשמרות, המקומות, המשמרות הקבועות וההגדרות נשמרים בחשבון Google שלך ומסתנכרנים בין האפליקציה לאתר. שינוי שנעשה בשני מקומות לפני שהסתנכרן לא נדרס: בוחרים איזו גרסה לשמור." else null) {
                ProfileFields()
                if (BuildConfig.ACCOUNTS_ENABLED) AccountRows(viewModel, onSignIn = onSignIn, onSignedOut = onClose, onBackup = { saveBackup.launch("salary-backup.json") })
            }
            Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KisLogo(Modifier.size(28.dp))
                Text("${androidx.compose.ui.res.stringResource(com.example.R.string.app_name)} · ${BuildConfig.VERSION_NAME}", style = KisType.caption, color = c.muted)
            }
        }
    }
    }

    // One panel for the places: a tap makes a place the default, the pencil opens its name, rate and colour right
    // there, and a long press on the two lines at its side moves it in the list.
    if (pickingDefault) KisSheet(onDismiss = { pickingDefault = false }, tag = "default_category_sheet") {
        Text("מקומות עבודה", style = KisType.title, color = c.ink)
        PlacesPanel(categories, defaultCategory, viewModel, onDefault = { viewModel.setDefaultCategory(it); pickingDefault = false })
        KisButton("+ מקום עבודה חדש", { pickingDefault = false; addingDefault = true; adding = true }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "default_category_add")
    }
    if (adding) NewWorkplaceSheet(viewModel, categories, defaultCurrency, makeDefault = addingDefault, onDismiss = { adding = false; addingDefault = false })
    if (seriesSheet) SeriesSheet(series, plans, categories, autoConfirm, onAuto = { plan.autoConfirm = it; autoConfirm = it },
        onRate = { s, rate -> viewModel.applySeriesRate(s.id, rate) }, onEnd = { s -> viewModel.endSeries(s.id) }, onDismiss = { seriesSheet = false })
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
private fun Group(title: String, info: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Kis.colors
    Row(Modifier.padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = KisType.title, color = c.ink, modifier = Modifier.testTag("settings_section_$title").semantics { heading() })
        if (info != null) InfoDot(title, info)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(horizontal = 16.dp, vertical = 4.dp), content = content)
}

/** A settings row: title + optional hint on the right, the control on the left (RTL). */
@Composable
private fun Row_(title: String, hint: String?, modifier: Modifier = Modifier, tag: String? = null, onClick: (() -> Unit)? = null,
                 leading: (@Composable () -> Unit)? = null, info: String? = null, trailing: @Composable () -> Unit) {
    val c = Kis.colors
    Row(modifier.fillMaxWidth().heightIn(min = 58.dp).then(if (onClick != null) Modifier.kisClick(onClick = onClick) else Modifier)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
                if (info != null) InfoDot(title, info)
            }
            // A short state only (e.g. "פעיל"); explanations live behind ⓘ.
            if (hint != null) Text(hint, style = KisType.caption, color = c.muted)
        }
        trailing()
    }
}

@Composable
private fun WorkplaceEditor(cat: WorkCategory, categories: List<WorkCategory>, viewModel: WorkViewModel, onDismiss: () -> Unit) {
    val c = Kis.colors
    val settings = Kis.settings
    var name by rememberSaveable { mutableStateOf(cat.name) }
    var rate by rememberSaveable { mutableStateOf(KisFormat.hours(cat.defaultRate)) }
    var currency by rememberSaveable { mutableStateOf(viewModel.categoryCurrency(cat.name)) }
    var color by rememberSaveable { mutableIntStateOf(settings.workplaceIndex(cat.name, categories)) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val taken = categories.any { it.id != cat.id && it.name.trim().equals(name.trim(), true) }
    val rateValue = rate.toDoubleOrNull()?.takeIf { it >= 0 }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.sunk).padding(12.dp).testTag("edit_category_sheet"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * The places, in the user's order. A tap on a row makes it the default; the pencil opens its name, rate and colour
 * under it (one at a time); a long press on the two lines (a small vibration) picks it up and dragging moves it. The
 * add page lists the places in the same order.
 */
@Composable
private fun PlacesPanel(categories: List<WorkCategory>, defaultCategory: String, viewModel: WorkViewModel, onDefault: (String) -> Unit) {
    val c = Kis.colors
    val settings = Kis.settings
    val step = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }
    var order by remember(categories) { mutableStateOf(categories.map { it.name }) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    fun drop() { if (dragging == null) return; dragging = null; dragY = 0f; settings.updatePlaceOrder(order) }
    Column(Modifier.testTag("places_panel"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        order.forEach { name ->
            val cat = categories.firstOrNull { it.name == name } ?: return@forEach
            val lifted = dragging == name
            val on = name == defaultCategory
            key(name) {
                Row(Modifier.fillMaxWidth().height(56.dp).zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationY = if (lifted) dragY else 0f; shadowElevation = if (lifted) 12f else 0f; shape = RoundedCornerShape(14.dp); clip = true }
                    .background(if (lifted) c.surface else if (on) c.sunk else androidx.compose.ui.graphics.Color.Transparent)
                    .kisClick { onDefault(name) }.semantics { selected = on }
                    .testTag("default_category_option_$name").padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(workplaceOf(name, categories).brush))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = KisType.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = c.ink, maxLines = 1)
                        Text("${KisFormat.money(cat.defaultRate, viewModel.categoryCurrency(name))} לשעה", style = KisType.caption, color = c.muted)
                    }
                    if (on) Icon(Icons.Rounded.Check, "ברירת מחדל", tint = c.ink)
                    Icon(Icons.Rounded.Edit, "עריכת $name", tint = c.ink, modifier = Modifier.size(44.dp).clip(CircleShape)
                        .kisClick { open = if (open == name) null else name }.testTag("category_row_$name").padding(11.dp))
                    Icon(Icons.Rounded.DragHandle, "הזזת $name", tint = c.muted, modifier = Modifier.size(44.dp)
                        .pointerInput(name) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { open = null; dragging = name; dragY = 0f; settings.haptic(KisSettings.Haptic.SELECT) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragY += amount.y
                                    val i = order.indexOf(name)
                                    if (dragY > step / 2 && i < order.lastIndex) { order = order.toMutableList().apply { add(i + 1, removeAt(i)) }; dragY -= step; settings.haptic(KisSettings.Haptic.TICK) }
                                    else if (dragY < -step / 2 && i > 0) { order = order.toMutableList().apply { add(i - 1, removeAt(i)) }; dragY += step; settings.haptic(KisSettings.Haptic.TICK) }
                                },
                                onDragEnd = { drop() }, onDragCancel = { drop() })
                        }.testTag("place_drag_$name").padding(10.dp))
                }
                if (open == name) WorkplaceEditor(cat, categories, viewModel, onDismiss = { open = null })
            }
        }
    }
}
