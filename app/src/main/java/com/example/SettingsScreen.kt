package com.example

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.ui.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.layout.onSizeChanged
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.example.ui.coachTarget
import com.example.ui.CoachTarget
import com.example.ui.LocalCoachStep
import com.example.ui.CoachScreen
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.WorkViewModel
import com.example.ui.WorkViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Settings, backup and AI key screens (moved verbatim from MainActivity.kt).

/** The secret is only entered here; never restored into a visible field or saved UI state. */
@Composable
private fun PersonalAiKeyDialog(onDismiss: () -> Unit, isFirstSetup: Boolean = true, ownerUid: String?) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var showExplanation by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var hasKey by remember {
        mutableStateOf(runCatching { com.example.api.PersonalAiKey.read(context, ownerUid).isNotBlank() }.getOrDefault(false))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("שמירת משמרות בעזרת ג׳מיני", modifier = Modifier.weight(1f))
                IconButton(onClick = { showExplanation = !showExplanation }) {
                    Icon(Icons.Outlined.Info, contentDescription = "הסבר על המפתח האישי")
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (hasKey) "מפתח אישי כבר שמור. אפשר להחליף או להסיר אותו כאן." else "אפשר להוסיף מפתח אישי עכשיו, או לדלג ולהוסיף בהגדרות בהמשך.")
                AnimatedVisibility(visible = showExplanation) {
                    Text("מפתח אישי מאפשר לתאר עבודה במילים ולקבל משמרות לבדיקה לפני שמירה. בלי מפתח אפשר להשתמש בכל הפעולות הרגילות. המפתח נשמר מוצפן במכשיר הזה, אינו מוצג במסכים ואינו נכלל בגיבוי המשמרות. הוא לא מסתנכרן למכשירים אחרים. הפענוח נשלח לגוגל ומשתמש במכסת הפרויקט שאליו שייך המפתח.")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; status = null },
                    label = { Text(if (hasKey) "מפתח אישי חדש" else "מפתח אישי (לא חובה)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                status?.let { Text(it) }
                if (hasKey) {
                    TextButton(onClick = {
                        try {
                            com.example.api.PersonalAiKey.remove(context, ownerUid)
                            draft = ""
                            hasKey = false
                            status = "המפתח הוסר. המשמרות וכל שאר הנתונים נשמרו."
                        } catch (_: Exception) {
                            status = "הסרת המפתח לא הצליחה. אפשר לנסות שוב."
                        }
                    }) { Text("הסרת המפתח האישי") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    try {
                        com.example.api.PersonalAiKey.save(context, draft, ownerUid)
                        draft = ""
                        Toast.makeText(context, "המפתח נשמר במכשיר. החיבור ייבדק בזמן הפענוח.", Toast.LENGTH_LONG).show()
                        onDismiss()
                    } catch (_: Exception) {
                        status = "שמירת המפתח לא הצליחה. יש לבדוק את המפתח ולנסות שוב."
                    }
                }
            ) { Text("שמירת המפתח") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isFirstSetup) "לא עכשיו" else "סגירה") }
        }
    )
}

// ================= MANAGEMENT & BACKUP SCREEN =================
@Composable
fun ManagementScreen(
    viewModel: WorkViewModel,
    categories: List<WorkCategory>,
    onNavigateBack: () -> Unit,
    onSignIn: () -> Unit = {},
    onReplayTutorial: () -> Unit = {}
) {
    val context = LocalContext.current
    val accountSession by viewModel.currentUserSession.collectAsStateWithLifecycle()
    var showPersonalKeySettings by remember { mutableStateOf(false) }
    if (showPersonalKeySettings) {
        key(accountSession?.uid) {
            PersonalAiKeyDialog(onDismiss = { showPersonalKeySettings = false }, isFirstSetup = false, ownerUid = viewModel.owner.uid)
        }
    }
    var newCategoryText by remember { mutableStateOf("") }
    var defaultCategoryMenu by remember { mutableStateOf(false) }
    var addingDefaultCategory by remember { mutableStateOf(false) }
    var defaultCategoryName by remember { mutableStateOf("") }
    var defaultCategoryRate by remember { mutableStateOf("40") }
    var categoryToDelete by remember { mutableStateOf<WorkCategory?>(null) }
    var categoryToEditByRate by remember { mutableStateOf<WorkCategory?>(null) }
    var editRateText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }

    val localPreferences by viewModel.localPreferences.collectAsStateWithLifecycle()
    if (addingDefaultCategory) {
        var error by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addingDefaultCategory = false }, containerColor = com.example.ui.theme.FormSurface,
            title = { Text("הוספת קטגוריה") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(defaultCategoryName, { defaultCategoryName = it }, label = { Text("שם קטגוריה") }, singleLine = true, modifier = Modifier.testTag("default_category_new_name"))
                    OutlinedTextField(defaultCategoryRate, { defaultCategoryRate = it }, label = { Text("תעריף לשעה") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("default_category_new_rate"))
                    Text("המטבע יהיה מטבע ברירת המחדל שלך.", style = MaterialTheme.typography.bodySmall)
                    if (error.isNotEmpty()) Text(error, color = Color(0xFFF87171))
                }
            }, confirmButton = { TextButton(onClick = {
                val name = defaultCategoryName.trim()
                val rate = defaultCategoryRate.toDoubleOrNull()
                if (name.isBlank() || categories.any { it.name == name } || rate == null || !rate.isFinite() || rate < 0) error = "יש להזין שם חדש ותעריף תקין"
                else { viewModel.addDefaultCategory(name, rate); addingDefaultCategory = false }
            }) { Text("הוסף ובחר") } }, dismissButton = { TextButton(onClick = { addingDefaultCategory = false }) { Text("ביטול") } })
    }

    val saveBackupLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.saveBackup(context, it) }
    }
    val savedNotificationEnabled by viewModel.serviceNotificationEnabled.collectAsStateWithLifecycle()
    val savedDefaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    var draftNotificationEnabled by remember(savedNotificationEnabled) { mutableStateOf(savedNotificationEnabled) }
    var draftDefaultCurrency by remember(savedDefaultCurrency) { mutableStateOf(savedDefaultCurrency) }

    // Accordion state - default to all closed (-1)
    val coachStep = LocalCoachStep.current
    var expandedSection by remember { mutableStateOf(if (coachStep?.screen == CoachScreen.API) 3 else -1) }

    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // During guidance, scroll targets must remain above the real sticky actions.
                .padding(bottom = if (coachStep != null) 88.dp else 0.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "הגדרות מערכת",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            OutlinedButton(onClick = onReplayTutorial, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("replay_onboarding"), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("הצג שוב את ההדרכה")
            }

            // Category 1: ניהול עבודה וקטגוריות
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 0
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .coachTarget(CoachTarget.CATEGORIES)

                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth().clickable {
                            expandedSection = if (isExpanded) -1 else 0
                        }.testTag("settings_section_עבודה וקטגוריות")) {
                            SettingsSectionHeader("עבודה וקטגוריות", isExpanded)
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "קטגוריות מסווגות את המשמרות. שינוי ברירת מחדל אינו משנה סכומים שנשמרו.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Input parameter to add category
                                var newCategoryRateText by remember { mutableStateOf("40") }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newCategoryText,
                                            onValueChange = { newCategoryText = it },
                                            label = { Text("קטגוריה חדשה", color = Color(0xFF8E8E93)) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("category_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF5C6BC0),
                                                unfocusedBorderColor = Color(0xFF3F3F46),
                                                focusedContainerColor = com.example.ui.theme.FormSurface,
                                                unfocusedContainerColor = com.example.ui.theme.FormSurface
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedTextField(
                                            value = newCategoryRateText,
                                            onValueChange = { newCategoryRateText = it },
                                            label = { Text("תעריף שעתי", color = Color(0xFF8E8E93)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(0.7f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF5C6BC0),
                                                unfocusedBorderColor = Color(0xFF3F3F46),
                                                focusedContainerColor = com.example.ui.theme.FormSurface,
                                                unfocusedContainerColor = com.example.ui.theme.FormSurface
                                            )
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (newCategoryText.isNotBlank()) {
                                                val rVal = newCategoryRateText.toDoubleOrNull() ?: 40.0
                                                viewModel.addCategory(newCategoryText, rVal)
                                                newCategoryText = ""
                                                triggerHapticFeedback(context, isDestructive = false)
                                                Toast.makeText(context, "הקטגוריה נוספה!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("add_category_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("הוסף קטגוריה", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "קטגוריות קיימות (לחץ על ה-X למחיקה):",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Flex style category flow
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    categories.forEach { cat ->
                                        Box(
                                            modifier = Modifier
                                                .background(com.example.ui.theme.FormSurface, shape = RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFF3F3F46), shape = RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.clickable {
                                                        triggerHapticFeedback(context, isDestructive = false)
                                                        categoryToEditByRate = cat
                                                        editRateText = cat.defaultRate.toString()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Edit,
                                                        contentDescription = "ערוך תעריף",
                                                        tint = Color(0xFF6366F1),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "${cat.name} (${viewModel.categoryCurrency(cat.name)}${cat.defaultRate})",
                                                        fontSize = 12.sp,
                                                        color = Color.White
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Outlined.Cancel,
                                                    contentDescription = "מחק קטגוריה",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable {
                                                            triggerHapticFeedback(context, isDestructive = true)
                                                            categoryToDelete = cat
                                                        }
                                                        .testTag("delete_category_${cat.name}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category 2: הגדרת מטבע ראשי
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 1
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()

                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth().clickable {
                            expandedSection = if (isExpanded) -1 else 1
                        }.testTag("settings_section_מטבע וברירות מחדל")) {
                            SettingsSectionHeader("מטבע וברירות מחדל", isExpanded)
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text("קטגוריית ברירת מחדל")
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { defaultCategoryMenu = true }, modifier = Modifier.fillMaxWidth().testTag("default_category_picker")) {
                        Text((localPreferences["defaultCategory"]?.takeIf { name -> categories.any { it.name == name } } ?: categories.firstOrNull()?.name ?: "עצמאי"))
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = defaultCategoryMenu, onDismissRequest = { defaultCategoryMenu = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.name) }, onClick = { viewModel.setDefaultCategory(cat.name); defaultCategoryMenu = false }, modifier = Modifier.testTag("default_category_option_${cat.name}"))
                        }
                        DropdownMenuItem(text = { Text("הוספת קטגוריה") }, onClick = { defaultCategoryMenu = false; defaultCategoryName = ""; defaultCategoryRate = "40"; addingDefaultCategory = true })
                    }
                }

                                Text(
                                    text = "בחר את מטבע ברירת המחדל לחישוב וניהול משמרות ברחבי האפליקציה.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = "מטבע ברירת מחדל",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "יחול אוטומטית בהוספת משמרות",
                                            color = Color(0xFF8E8E93),
                                            fontSize = 11.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("₪", "$").forEach { curr ->
                                            val isSelected = draftDefaultCurrency == curr
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 54.dp, height = 36.dp)
                                                    .background(
                                                        if (isSelected) Color(0xFF6366F1) else Color(0xFF1E293B),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) Color.Transparent else Color(0x33FFFFFF),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { draftDefaultCurrency = curr }
                                                    .testTag("settings_currency_$curr"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = curr,
                                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category 4: התראות מערכת
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 3
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()

                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth().clickable {
                            expandedSection = if (isExpanded) -1 else 3
                        }.testTag("settings_section_מערכת ומשוב")) {
                            SettingsSectionHeader("מערכת ומשוב", isExpanded)
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("עדכונים", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                WorkUpdateSettings()
                                HorizontalDivider(color = Color(0xFF334155))

                                Text("התראות משמרת", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "ניהול הגדרות התראה, טיימר פעיל במכשיר, ושומר מסך כהה.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Active Shift Notification Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 16.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = "התראת משמרת פעילה",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Start
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "הצגת טיימר פעיל ועדכון שכר שנצבר בהתראת רקע קבועה במכשיר בזמן שהשעון רץ.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF8E8E93),
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                    Switch(
                                        checked = draftNotificationEnabled,
                                        onCheckedChange = { newValue ->
                                            triggerHapticFeedback(context, isDestructive = false)
                                            draftNotificationEnabled = newValue
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF6366F1),
                                            uncheckedThumbColor = Color(0xFF8E8E93),
                                            uncheckedTrackColor = com.example.ui.theme.FormSurface
                                        ),
                                        modifier = Modifier.testTag("service_notification_switch")
                                    )
                                }

                                HorizontalDivider(color = Color(0xFF334155))
                                Text("בינה מלאכותית", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("שימוש אופציונלי בג׳מיני באמצעות המפתח האישי שלך")
                                TextButton(onClick = { showPersonalKeySettings = true }, modifier = Modifier.coachTarget(CoachTarget.API)) { Text("מפתח אישי") }

                                HorizontalDivider(color = Color(0xFF334155))
                                Text("משוב ודיווח על תקלה", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                FeedbackForm()
                            }
                        }
                    }
                }
            }

            // Category 5: תחזוקה וגיבוי נתונים
            Box(modifier = Modifier.fillMaxWidth()) {
                val isExpanded = expandedSection == 4
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.FormSurface),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF6366F1) else Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()

                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth().clickable {
                            expandedSection = if (isExpanded) -1 else 4
                        }.testTag("settings_section_גיבוי ונתונים")) {
                            SettingsSectionHeader("גיבוי ונתונים", isExpanded)
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text("הנתונים נשמרים במכשיר. שמור גיבוי גם מחוץ לאפליקציה.")
                                Button(onClick = { saveBackupLauncher.launch("salary-backup.json") }) { Text("שמור קובץ גיבוי") }

                                Text(
                                    text = "ייבוא וייצוא נתונים לצורך גיבוי ושחזור.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Import/Restore section
                                Text(
                                    text = "ייבוא נתונים (אקסל או JSON)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "הדבק שורות מאקסל (מופרד באמצעות Tabs/פסיקים) או טקסט גיבוי JSON:",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = importText,
                                    onValueChange = { importText = it },
                                    placeholder = { Text("הדבק נתונים כאן...", color = Color(0xFF64748B), fontSize = 12.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .testTag("settings_import_text_field"),
                                    maxLines = 3,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF5C6BC0),
                                        unfocusedBorderColor = Color(0xFF3F3F46),
                                        focusedContainerColor = com.example.ui.theme.FormSurface,
                                        unfocusedContainerColor = com.example.ui.theme.FormSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (importText.isBlank()) {
                                            Toast.makeText(context, "נא להזין טקסט ייבוא תקין", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val success = viewModel.importDataFromString(context, importText)
                                            if (success) {
                                                importText = ""
                                                triggerHapticFeedback(context, isDestructive = false)
                                                // The confirmation dialog owns the actual import.
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("settings_import_action_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ייבא נתונים כעת", color = Color.White, fontSize = 13.sp)
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = Color(0xFF2D2D2D))
                                Spacer(modifier = Modifier.height(16.dp))

                                // Export section
                                Text(
                                    text = "גיבוי וייצוא נתונים",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.copyExportToClipboard(context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("copy_backup_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F3F46))
                                    ) {
                                        Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("העתק ללוח", fontSize = 12.sp, maxLines = 1, color = Color.White)
                                    }

                                    Button(
                                        onClick = { viewModel.shareExportData(context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("share_backup_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Share, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("שתף גיבוי", fontSize = 12.sp, maxLines = 1, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (viewModel.owner.uid != null) {
                val syncStatus by viewModel.cloudStatus.collectAsStateWithLifecycle()
                Text(syncStatus)
                if (BuildConfig.VERSIONED_SYNC_ENABLED) {
                    TextButton(onClick = { viewModel.syncNow() }) { Text("סנכרון עכשיו") }
                    TextButton(onClick = { viewModel.reviewSyncConflicts(context) }) { Text("סקירת שינויים מתנגשים") }
                }
                TextButton(onClick = { viewModel.reviewLegacyData(context) }) {
                    Text("העתקה חד־פעמית של הנתונים המקומיים לחשבון")
                }
            }
            // Sign Out row option
            if (BuildConfig.ACCOUNTS_ENABLED) Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                    border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                triggerHapticFeedback(context, isDestructive = true)
                                if (accountSession == null) {
                                    onSignIn()
                                } else {
                                    android.app.AlertDialog.Builder(context)
                                        .setTitle("התנתקות מהחשבון")
                                        .setMessage("הנתונים השמורים יישארו בחשבון במכשיר. טפסים שלא נשמרו ייסגרו. לאחר ההתנתקות יוצגו נתוני השימוש המקומי.")
                                        .setNegativeButton("ביטול", null)
                                        .setPositiveButton("התנתקות") { _, _ ->
                                            if (viewModel.signOut(context)) {
                                                onNavigateBack()
                                                Toast.makeText(context, "התנתקת מהמערכת בהצלחה", Toast.LENGTH_SHORT).show()
                                            }
                                        }.show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("settings_sign_out_btn")
                        ) {
                            Text(
                                text = if (accountSession == null) "התחברות" else "התנתק",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "חשבון משתמש",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val sessionUser by viewModel.currentUserSession.collectAsStateWithLifecycle()
                            Text(
                                text = sessionUser?.email ?: sessionUser?.displayName ?: "שימוש מקומי — ללא סנכרון",
                                fontSize = 12.sp,
                                color = Color(0xFF8E8E93),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }

        // Pinned/Sticky Bottom Action Bar containing Cancel & Save Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .testTag("settings_action_bar")
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, com.example.ui.theme.FormSurface.copy(alpha = 0.95f), com.example.ui.theme.FormSurface)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel Button on the left
            OutlinedButton(
                onClick = {
                    onNavigateBack()
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .testTag("settings_cancel_btn"),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "ביטול",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Save Button on the right
            Button(
                onClick = {
                    viewModel.updateServiceNotificationEnabled(draftNotificationEnabled)
                    viewModel.updateDefaultCurrency(draftDefaultCurrency)
                    Toast.makeText(context, "ההגדרות נשמרו בהצלחה!", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .testTag("settings_save_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "שמור",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("אישור מחיקה", color = Color.White) },
            text = { Text("האם אתה בטוח שברצונך למחוק? פעולה זו אינה ניתנת לביטול.", color = Color(0xFFE2E8F0)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryToDelete?.let { viewModel.deleteCategory(it) }
                        categoryToDelete = null
                    }
                ) {
                    Text("מחק", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("ביטול", color = Color(0xFF8E8E93))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E8F0)
        )
    }

    if (categoryToEditByRate != null) {
        var errorEditRate by remember { mutableStateOf(false) }
        var editName by remember(categoryToEditByRate) { mutableStateOf(categoryToEditByRate?.name ?: "") }
        var editCurrency by remember(categoryToEditByRate) { mutableStateOf(viewModel.categoryCurrency(categoryToEditByRate?.name ?: "")) }
        AlertDialog(
            onDismissRequest = { categoryToEditByRate = null },
            title = { Text("עדכון תעריף שעתי ברירת מחדל", color = Color.White, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "עדכון תעריף ברירת המחדל עבור '${categoryToEditByRate?.name}'. שינוי זה ישפיע רק על משמרות עתידיות ולא ישנה נתונים קודמים.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("שם קטגוריה") })
                    Row { listOf("₪", "$").forEach { currency ->
                        TextButton(onClick = { editCurrency = currency }) { Text(if (currency == editCurrency) "✓ $currency" else currency) }
                    } }
                    OutlinedTextField(
                        value = editRateText,
                        onValueChange = {
                            editRateText = it
                            errorEditRate = it.toDoubleOrNull() == null || it.toDouble() <= 0.0
                        },
                        label = { Text("תעריף שעתי חדש", color = Color(0xFF8E8E93)) },
                        singleLine = true,
                        isError = errorEditRate,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF5C6BC0),
                            unfocusedBorderColor = Color(0xFF3F3F46),
                            focusedContainerColor = com.example.ui.theme.FormSurface,
                            unfocusedContainerColor = com.example.ui.theme.FormSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorEditRate) {
                        Text("אנא הזן מספר תקין הגדול מ-0", color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedRate = editRateText.toDoubleOrNull()
                        if (parsedRate != null && parsedRate > 0.0) {
                            categoryToEditByRate?.let { cat ->
                                viewModel.editCategory(cat, editName, parsedRate, editCurrency)
                            }
                            categoryToEditByRate = null
                            Toast.makeText(context, "תעריף שעתי עודכן בהצלחה", Toast.LENGTH_SHORT).show()
                        } else {
                            errorEditRate = true
                        }
                    }
                ) {
                    Text("שמור", color = Color(0xFF6366F1))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEditByRate = null }) {
                    Text("ביטול", color = Color(0xFF8E8E93))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E8F0)
        )
    }
}
// Simple FlowRow helper representation for Jetpack compose (since standard flow is experimental / in layout package)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Since we are showing a small list of badges, simple wrapping can be easily handled or we can use standard horizontal flow.
        // For standard simplicity, we render them in a wrapped Row using Compose flow or standard Row flow.
        // Compose 1.4+ has FlowRow in foundation. Here's a beautiful, lightweight Row wrap mock using a simple Box wrap or using Android's standard Row to prevent compilation errors.
        // Actually, in multi-line flow we can just draw them in a lazy row or simple wrap arrangement.
        // Let's draw them elegantly using a horizontal Row with scroll or wrapping.
        LazyRow(
            horizontalArrangement = horizontalArrangement,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Row(
                    horizontalArrangement = horizontalArrangement,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    content()
                }
            }
        }
    }
}
