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
import androidx.compose.material.icons.rounded.Edit
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
fun KisSettingsPage(viewModel: WorkViewModel, categories: List<WorkCategory>, onClose: () -> Unit, onReplayTour: () -> Unit, onSignIn: () -> Unit = {}, bottomInset: Dp = 0.dp) {
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
    // Not saveable: a pasted backup can be far larger than what Android may keep in saved state.
    var importText by remember { mutableStateOf("") }
    val hasKey = remember(aiSheet) { runCatching { PersonalAiKey.read(context, viewModel.owner.uid).isNotBlank() }.getOrDefault(false) }
    // Weekly plans and their reminders (device-only settings, see PlanSettings).
    val plan = viewModel.planSettings
    val series by viewModel.series.collectAsStateWithLifecycle()
    val plans by viewModel.occurrences.collectAsStateWithLifecycle()
    var seriesSheet by rememberSaveable { mutableStateOf(false) }
    var autoConfirm by remember { mutableStateOf(plan.autoConfirm) }
    var notifyMonthly by remember { mutableStateOf(plan.notifyMonthly) }
    var notifyRate by remember { mutableStateOf(plan.notifyRate) }
    var quiet by remember { mutableStateOf(plan.quiet) }
    var quietFrom by remember { mutableStateOf(plan.quietFrom) }
    var quietTo by remember { mutableStateOf(plan.quietTo) }
    var denied by remember { mutableStateOf(false) }
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    fun turnOn(kind: String) {
        denied = false
        if (kind == "monthly") { plan.notifyMonthly = true; notifyMonthly = true } else { plan.notifyRate = true; notifyRate = true }
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Without permission the switch stays off, with a short explanation.
        asking?.let { if (granted) turnOn(it) else denied = true }
        asking = null
    }
    fun enable(kind: String, on: Boolean) {
        if (!on) { if (kind == "monthly") { plan.notifyMonthly = false; notifyMonthly = false } else { plan.notifyRate = false; notifyRate = false }; return }
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) { asking = kind; askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        else turnOn(kind)
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { viewModel.saveBackup(context, it) } }

    Column(Modifier.fillMaxSize().background(c.bg).testTag("settings_root")) {
        Text("הגדרות", style = KisType.page, color = c.ink, modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 2.dp).semantics { heading() })
        // With the keyboard open the floating navigation is covered anyway: pad for the keyboard only, not both.
        val imeOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        // The scroll area ends above the floating navigation, so no row can sit (and be tapped) under it.
        Column(Modifier.weight(1f).padding(bottom = if (imeOpen) 0.dp else bottomInset).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp).imePadding()) {
            Group("עבודה") {
                Row_("מקום ברירת מחדל", "נבחר אוטומטית במשמרת חדשה ובטיימר · כאן גם מוסיפים ועורכים מקומות", tag = "default_category_picker",
                    modifier = Modifier.coachTarget(CoachTarget.CATEGORIES), onClick = { pickingDefault = true },
                    leading = { if (defaultCategory.isNotEmpty()) Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(workplaceOf(defaultCategory, categories).brush)) }) {
                    Text(defaultCategory, style = KisType.label, color = c.ink, maxLines = 1)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
                Row_("מטבע ברירת מחדל", "למקומות ולמשמרות חדשים. כל משמרת שומרת את המטבע שלה") {
                    CurrencyToggle(defaultCurrency, { viewModel.updateDefaultCurrency(it) }, "settings_currency")
                }
                KisDivider()
                Row_("משמרות קבועות", if (series.isEmpty()) "יוצרים במסך ההוספה: \"חוזרת כל שבוע\"" else
                    "${series.size} פעילות · רישום אוטומטי ${if (autoConfirm) "פועל" else "כבוי"}", tag = "series_row", onClick = { seriesSheet = true }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
            }
            Group("מראה ותחושה") {
                Row_("ערכת צבעים", when (settings.theme) { ThemeChoice.SYSTEM -> "לפי הגדרת הטלפון"; ThemeChoice.DARK -> "כהה תמיד"; ThemeChoice.LIGHT -> "בהיר תמיד" }) {
                    KisSegmented(ThemeChoice.entries.map { it to it.label }, settings.theme, settings::updateTheme, tagPrefix = "theme")
                }
                Row_("רטט במגע", "רטט קצר בלחיצות, ורטט שונה לשמירה ולמחיקה") { KisSwitch(settings.haptics, settings::updateHaptics, Modifier.testTag("haptics_switch"), label = "רטט במגע") }
            }
            Group("התראות") {
                Row_("התראת משמרת פעילה", "טיימר וסכום שנצבר בהתראה קבועה בזמן שהשעון רץ") {
                    KisSwitch(serviceNotification, { viewModel.updateServiceNotificationEnabled(it) }, Modifier.testTag("service_notification_switch"), label = "התראת משמרת פעילה")
                }
                KisDivider()
                Row_("הסיכום החודשי מוכן", "פעם בחודש, בתחילת החודש: כמה עבדת וכמה הרווחת") {
                    KisSwitch(notifyMonthly, { enable("monthly", it) }, Modifier.testTag("notify_monthly_switch"), label = "הסיכום החודשי מוכן")
                }
                Row_("תזכורת לתעריף חסר", "לכל היותר פעם בשבוע, רק כשיש משמרות בלי תעריף") {
                    KisSwitch(notifyRate, { enable("rate", it) }, Modifier.testTag("notify_rate_switch"), label = "תזכורת לתעריף חסר")
                }
                // Turned on, but the phone blocks this app's notifications (permission removed in the system settings).
                if (!denied && (notifyMonthly || notifyRate) && !com.example.PlanJobs.canNotify(context)) Text("ההתראות של האפליקציה חסומות בהגדרות הטלפון, ולכן התזכורות לא יגיעו.",
                    style = KisType.caption, color = c.pending, modifier = Modifier.padding(bottom = 8.dp).testTag("notify_blocked"))
                if (denied) Text("בלי הרשאת התראות אי אפשר לשלוח תזכורות. אפשר לאשר בהגדרות הטלפון ולנסות שוב.", style = KisType.caption, color = c.pending,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("notify_denied"))
                KisDivider()
                Row_("שעות שקט", "תזכורת שנופלת בשעות האלה תגיע כשהן נגמרות") {
                    KisSwitch(quiet, { plan.quiet = it; quiet = it }, Modifier.testTag("quiet_switch"), label = "שעות שקט")
                }
                if (quiet) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    TimeChip("משעה", quietFrom, Modifier.weight(1f), "quiet_from") { plan.quietFrom = it; quietFrom = it }
                    TimeChip("עד שעה", quietTo, Modifier.weight(1f), "quiet_to") { plan.quietTo = it; quietTo = it }
                }
            }
            Group("עזרה וכלים") {
                Row_("רישום במילים (AI)", if (hasKey) "פעיל עם מפתח Gemini האישי שלך" else "מפתח Gemini אישי · לא חובה, בלעדיו כל השאר עובד", tag = "ai_key_row", onClick = { aiSheet = true },
                    modifier = Modifier.coachTarget(CoachTarget.API)) {
                    Text("?", style = KisType.label, color = c.muted, modifier = Modifier.size(32.dp).clip(CircleShape).background(c.sunk).kisClick { aiHelp = true }
                        .semantics { contentDescription = "מה זה מפתח אישי" }.padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                KisDivider()
                Row_("הצגת ההדרכה מחדש", "סיור קצר במקומות החשובים", tag = "replay_onboarding", onClick = onReplayTour) {}
                if (BuildConfig.LOCAL_DISTRIBUTION) {
                    KisDivider()
                    Column(Modifier.padding(vertical = 8.dp).testTag("settings_updates")) {
                        Text("עדכונים", style = KisType.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
                        WorkUpdateSettings()
                    }
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
        Text("מקומות עבודה", style = KisType.title, color = c.ink)
        Text("נגיעה במקום הופכת אותו לברירת המחדל. העיפרון פותח עריכה.", style = KisType.caption, color = c.muted)
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) { categories.forEach { cat ->
            val on = cat.name == defaultCategory
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.sunk else androidx.compose.ui.graphics.Color.Transparent)
                .kisClick { viewModel.setDefaultCategory(cat.name); pickingDefault = false }.semantics { selected = on }
                .testTag("default_category_option_${cat.name}").padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(workplaceOf(cat.name, categories).brush))
                Column(Modifier.weight(1f)) {
                    Text(cat.name, style = KisType.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = c.ink)
                    Text("${KisFormat.money(cat.defaultRate, viewModel.categoryCurrency(cat.name))} לשעה", style = KisType.caption, color = c.muted)
                }
                if (on) Icon(Icons.Rounded.Check, "ברירת מחדל", tint = c.ink)
                Icon(Icons.Rounded.Edit, "עריכת ${cat.name}", tint = c.ink, modifier = Modifier.size(48.dp).clip(CircleShape)
                    .kisClick { pickingDefault = false; editing = cat.name }.testTag("category_row_${cat.name}").padding(13.dp))
            }
        } }
        KisButton("+ מקום עבודה חדש", { pickingDefault = false; addingDefault = true; adding = true }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "default_category_add")
    }
    editing?.let { name -> categories.firstOrNull { it.name == name }?.let { cat ->
        EditWorkplaceSheet(cat, categories, viewModel, onDismiss = { editing = null })
    } }
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
