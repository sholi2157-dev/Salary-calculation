package com.example.ui.kis

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import java.util.Locale

// ---------------------------------------------------------------- formatting

/**
 * The minimum size of anything a person taps (audit M-34), in one place: every button, chip, row action and icon
 * button uses it. 44dp, the same minimum as the web (44px). 44 vs 48dp is an open owner decision: change it here only.
 */
val KisMinTouch: Dp = 44.dp

object KisFormat {
    /** Keep start–end left-to-right within Hebrew planning rows, as in timeLabel. */
    fun timeRange(start: String, end: String): String = "\u2066$start–$end\u2069"

    /** "1,234" for whole amounts, "1,234.50" otherwise. */
    fun number(v: Double): String {
        val cents = Math.round(v * 100)
        return if (cents % 100 == 0L) String.format(Locale.US, "%,d", cents / 100) else String.format(Locale.US, "%,.2f", v)
    }
    fun money(v: Double, currency: String) = "$currency${number(v)}"

    /** Hours as a short decimal: 8, 7.5, 7.25. */
    fun hours(h: Double): String {
        val r = Math.round(h * 100) / 100.0
        return if (r % 1.0 == 0.0) r.toLong().toString() else String.format(Locale.US, "%s", r.toBigDecimal().stripTrailingZeros().toPlainString())
    }
    /** Minutes as h:mm (7:30). */
    fun hm(minutes: Int): String = "${minutes / 60}:${String.format(Locale.US, "%02d", minutes % 60)}"
    /** A duration on screen, always h:mm ("2:30 שעות", M-36, same as web KisUi.duration); rates and shared texts keep decimals. */
    fun duration(h: Double): String = hm(Math.round((if (h.isFinite()) h else 0.0) * 60).toInt())

    val DAY_SHORT = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")
    val DAY_LONG = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
    val MONTHS = listOf("ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני", "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר")
}

/** "₪1,234" with the symbol in Rubik (Secular One draws ₪ as letters) and digits in the given style. */
fun moneyString(amount: Double, currency: String, symbolScale: Float = 0.62f): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = symbolScale.em, baselineShift = BaselineShift(0.14f))) {
        append(currency)
    }
    append(KisFormat.number(amount))
}

@Composable
fun MoneyText(amount: Double, currency: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, symbolScale: Float = 0.62f,
              onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}) {
    Text(moneyString(amount, currency, symbolScale), modifier = modifier, style = style.copy(textDirection = TextDirection.Ltr, fontFeatureSettings = "tnum"),
        color = if (color == Color.Unspecified) Kis.colors.ink else color, maxLines = 1, softWrap = false, onTextLayout = onTextLayout)
}

// ---------------------------------------------------------------- interaction

/** Pressed-state scale (0.97) plus a light tap haptic. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.kisClick(enabled: Boolean = true, role: Role? = Role.Button, haptic: KisSettings.Haptic? = KisSettings.Haptic.TAP,
                      onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(80), label = "press")
    val settings = Kis.settings
    val scaled = this.graphicsLayer { scaleX = scale; scaleY = scale }
    return if (onLongClick == null) scaled.clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, role = role) {
        haptic?.let(settings::haptic); onClick()
    } else scaled.combinedClickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, role = role,
        onLongClick = { settings.haptic(KisSettings.Haptic.SELECT); onLongClick() }) { haptic?.let(settings::haptic); onClick() }
}

/*
 * M-30 (D-3) component system: one "selected" recipe (a selected surface with a 2dp ink edge) for chips, days, currencies,
 * tabs and segments in both themes; 3 button heights (touch targets stay at least 44dp); 4 radii plus pills; disabled
 * colours instead of opacity; a filled delete button with its own text colour.
 */
object KisRadius { val tag = 8.dp; val small = 12.dp; val medium = 16.dp; val large = 24.dp; val pill = 99.dp }
object KisButtonHeight { val large = 56.dp; val medium = 48.dp; val small = 44.dp }
val KisColors.selectedBg get() = if (dark) Color(0xFF424853) else surface
val KisColors.selectedInk get() = ink
val KisColors.selectedEdge get() = ink
val KisColors.disabledBg get() = if (dark) Color(0xFF21252B) else Color(0xFFD4D8DE)
val KisColors.disabledInk get() = if (dark) Color(0xFF5F636C) else Color(0xFF8A91A0)
/** Text on a filled danger surface (readable in dark too, where danger is a light coral). */
val KisColors.onDangerFill get() = if (dark) Color(0xFF2B0E0B) else Color.White

/** The one "selected" look: selected surface and a 2dp ink edge in [shape]; nothing when not selected. */
fun Modifier.kisSelected(on: Boolean, c: KisColors, shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (on) this.background(c.selectedBg, shape).border(2.dp, c.selectedEdge, shape) else this

enum class KisButtonKind { PRIMARY, GHOST, DANGER, OUTLINE }

@Composable
fun KisButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: KisButtonKind = KisButtonKind.PRIMARY, enabled: Boolean = true,
              icon: ImageVector? = null, height: Dp = KisButtonHeight.large, haptic: KisSettings.Haptic? = KisSettings.Haptic.TAP, tag: String? = null) {
    val c = Kis.colors
    val (bg, fg) = when {
        !enabled -> (if (kind == KisButtonKind.OUTLINE) Color.Transparent else c.disabledBg) to c.disabledInk
        kind == KisButtonKind.PRIMARY -> c.inkFill to c.onInk
        kind == KisButtonKind.GHOST -> c.sunk to c.ink
        kind == KisButtonKind.DANGER -> c.danger to c.onDangerFill
        else -> Color.Transparent to c.ink
    }
    val shape = RoundedCornerShape(if (height < KisButtonHeight.large) KisRadius.small else KisRadius.medium)
    Row(modifier.heightIn(min = height).clip(shape).background(bg)
        .then(if (kind == KisButtonKind.OUTLINE) Modifier.border(1.5.dp, c.lineStrong, shape) else Modifier)
        .kisClick(enabled = enabled, haptic = haptic, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = KisType.body.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small rounded action used in rows of actions (detail sheet, selection bar). */
@Composable
fun KisAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, danger: Boolean = false, tag: String? = null, enabled: Boolean = true) {
    val c = Kis.colors
    Column(modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(KisRadius.small))
        .background(if (!enabled) c.disabledBg else if (primary) c.inkFill else if (danger) c.danger else c.sunk)
        .kisClick(enabled = enabled, onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        val fg = if (!enabled) c.disabledInk else if (primary) c.onInk else if (danger) c.onDangerFill else c.ink
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, style = KisType.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

@Composable
fun KisChip(text: String, selected: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier, dashed: Boolean = false, warn: Boolean = false,
            leading: ImageVector? = null, tag: String? = null, minHeight: Dp = KisMinTouch, onLongClick: (() -> Unit)? = null) {
    val c = Kis.colors
    val shape = RoundedCornerShape(12.dp)
    val bg = when { warn -> c.pendingSoft; selected -> c.selectedBg; else -> c.surface }
    val fg = if (warn) c.pending else if (selected) c.selectedInk else c.ink
    Row(modifier.heightIn(min = minHeight).clip(shape).background(bg)
        .then(when {
            warn -> Modifier
            dashed -> Modifier.dashedBorder(c.line, 12.dp)
            selected -> Modifier.border(2.dp, c.selectedEdge, shape)
            else -> Modifier.border(1.5.dp, c.line, shape)
        })
        .kisClick(onLongClick = onLongClick, onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .semantics { this.selected = selected }
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (leading != null) Icon(leading, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = KisType.label, color = fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = this.drawBehind {
    val w = width.toPx()
    drawRoundRect(color = color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
}

@Composable
fun KisSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val c = Kis.colors
    val settings = Kis.settings
    Switch(checked = checked, onCheckedChange = { settings.haptic(KisSettings.Haptic.TAP); onChange(it) },
        modifier = modifier.then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        // M-28: on is ink (not the money green); off has a strong edge (a pale thumb on a pale track disappears on a card).
        colors = SwitchDefaults.colors(checkedThumbColor = c.controlKnob, checkedTrackColor = c.controlOn, checkedBorderColor = c.controlOn,
            uncheckedThumbColor = c.lineStrong, uncheckedTrackColor = c.sunk, uncheckedBorderColor = c.lineStrong))
}

/** Segmented control (tseg): a sunk track with a raised surface for the chosen option. */
@Composable
fun <T> KisSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, tagPrefix: String? = null) {
    val c = Kis.colors
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(c.sunk).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(Modifier.heightIn(min = KisMinTouch).clip(RoundedCornerShape(KisRadius.tag)).kisSelected(on, c, RoundedCornerShape(KisRadius.tag))
                .kisClick(role = Role.RadioButton) { onSelect(value) }.semantics { this.selected = on }
                .then(if (tagPrefix != null) Modifier.testTag("${tagPrefix}_$value") else Modifier)
                .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, style = KisType.label.copy(fontSize = 13.sp), color = if (on) c.selectedInk else c.muted, maxLines = 1)
            }
        }
    }
}

/** ₪ / $ two-cell control. */
@Composable
fun CurrencyToggle(currency: String, onCurrency: (String) -> Unit, tag: String, modifier: Modifier = Modifier) {
    val c = Kis.colors
    Row(modifier.clip(RoundedCornerShape(12.dp)).border(1.5.dp, c.line, RoundedCornerShape(12.dp)).testTag(tag)) {
        listOf("₪", "$").forEach { value ->
            val on = currency == value
            // Fill the selected cell in both themes; the other cell stays quiet.
            Box(Modifier.size(width = 48.dp, height = KisMinTouch).background(if (on) c.accent else c.surface)
                .kisClick(role = Role.RadioButton) { onCurrency(value) }.semantics { this.selected = on; contentDescription = if (value == "₪") "שקל" else "דולר" }
                .testTag("${tag}_$value"), contentAlignment = Alignment.Center) {
                Text(value, style = KisType.label.copy(fontFamily = Rubik, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = if (on) c.accentInk else c.muted)
            }
        }
    }
}

@Composable
fun KisCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), radius: Dp = 20.dp, color: Color = Kis.colors.surface, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(radius)).background(color).padding(padding), content = content)
}

@Composable
fun Tag(text: String, warn: Boolean = false, modifier: Modifier = Modifier, color: Color? = null, ink: Color? = null) {
    val c = Kis.colors
    Text(text, modifier.clip(RoundedCornerShape(8.dp)).background(color ?: if (warn) c.pendingSoft else c.sunk).padding(horizontal = 7.dp, vertical = 1.dp),
        style = KisType.micro, color = ink ?: if (warn) c.pending else c.muted, maxLines = 1)
}

/** Workplace initial on its colour. Dashed outline for a planned (not yet worked) shift. */
@Composable
fun Avatar(name: String, workplace: Workplace, size: Dp = 44.dp, planned: Boolean = false) {
    val c = Kis.colors
    val accent = workplace.accent(c.dark)
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp))
        .then(if (planned) Modifier.dashedBorder(accent, 14.dp) else Modifier.background(accent)), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).ifEmpty { "?" }, style = KisType.title.copy(fontSize = 18.sp),
            color = if (planned) accent else workplace.accentInk(c.dark))
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null, actionTag: String? = null) {
    val c = Kis.colors
    Row(modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = KisType.title, color = c.ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(action, style = KisType.label.copy(fontWeight = FontWeight.Medium), color = c.muted,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).kisClick(onClick = onAction).then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier).padding(horizontal = 6.dp, vertical = 10.dp))
    }
}

@Composable
fun KisTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String? = null,
                 singleLine: Boolean = true, minLines: Int = 1, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
                 keyboardActions: KeyboardActions = KeyboardActions.Default, isError: Boolean = false, enabled: Boolean = true,
                 visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                 /** A filled-in value is selected as a whole when the field takes focus, so typing replaces it instead of joining the digits. */
                 selectAllOnFocus: Boolean = false) {
    val c = Kis.colors
    if (selectAllOnFocus) {
        var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value)) }
        if (field.text != value) field = androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))
        OutlinedTextField(value = field, onValueChange = { field = it; if (it.text != value) onValueChange(it.text) },
            modifier = modifier.keepInViewWhenFocused().onFocusChanged { if (it.isFocused) field = field.copy(selection = androidx.compose.ui.text.TextRange(0, field.text.length)) }, label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = c.muted) } }, singleLine = singleLine, minLines = minLines,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, isError = isError, enabled = enabled, visualTransformation = visualTransformation,
            textStyle = KisType.body.copy(color = c.ink, textDirection = TextDirection.ContentOrRtl), shape = RoundedCornerShape(12.dp), colors = kisFieldColors())
        return
    }
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = modifier.keepInViewWhenFocused(), label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.muted) } }, singleLine = singleLine, minLines = minLines,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, isError = isError, enabled = enabled, visualTransformation = visualTransformation,
        textStyle = KisType.body.copy(color = c.ink, textDirection = TextDirection.ContentOrRtl), shape = RoundedCornerShape(12.dp), colors = kisFieldColors())
}

/**
 * Design decision "גלילה ומקלדת": a focused field stays visible above the keyboard. Once the keyboard has opened (the
 * page shrinks by its height), the field is brought into view within its scroll area, and again if the keyboard changes.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.keepInViewWhenFocused(): Modifier = composed {
    val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    androidx.compose.runtime.LaunchedEffect(focused, keyboard) { if (focused) { kotlinx.coroutines.delay(120); requester.bringIntoView() } }
    this.bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}

@Composable
private fun kisFieldColors() = Kis.colors.let { c ->
    OutlinedTextFieldDefaults.colors(focusedContainerColor = c.bg, unfocusedContainerColor = c.bg, disabledContainerColor = c.bg,
        focusedBorderColor = c.ink, unfocusedBorderColor = c.lineStrong, focusedLabelColor = c.ink, unfocusedLabelColor = c.muted, cursorColor = c.ink,
        focusedTextColor = c.ink, unfocusedTextColor = c.ink, disabledTextColor = c.muted, errorBorderColor = c.danger, errorLabelColor = c.danger)
}

/** Bottom sheet in Kis surface colours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KisSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Kis.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface,
        contentColor = c.ink, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.line)) }) {
        // Scrolls when the content is taller than the screen (a big team, a large font, the keyboard open), so nothing is
        // squeezed: the last row (the actions) keeps its size instead of being pressed flat.
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(horizontal = 16.dp).padding(bottom = 18.dp)
            .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** M-30 / R-46: one confirmation layout. Callers retain their wording, action identity and haptics. */
@Composable
fun KisConfirmSheet(title: String, onDismiss: () -> Unit, confirmText: String, onConfirm: () -> Unit,
                    message: String? = null, cancelText: String = "ביטול", danger: Boolean = true,
                    tag: String? = null, confirmTag: String? = null,
                    confirmHaptic: KisSettings.Haptic? = KisSettings.Haptic.TAP) {
    val c = Kis.colors
    KisSheet(onDismiss = onDismiss, tag = tag) {
        Text(title, style = KisType.title, color = c.ink)
        if (message != null) Text(message, style = KisType.body, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KisButton(cancelText, onDismiss, Modifier.weight(1f), KisButtonKind.GHOST, tag = "confirm_cancel")
            KisButton(confirmText, onConfirm, Modifier.weight(1f),
                if (danger) KisButtonKind.DANGER else KisButtonKind.PRIMARY,
                haptic = confirmHaptic, tag = confirmTag)
        }
    }
}

/** The "כיס" mark: three rising bars on a base line, on an ink tile. */
@Composable
fun KisLogo(modifier: Modifier = Modifier.size(38.dp)) {
    Canvas(modifier.semantics { contentDescription = "סמל האפליקציה" }) {
        val k = size.minDimension / 64f
        drawRoundRect(Color(0xFF0D1015), cornerRadius = CornerRadius(19 * k))
        val w = 7 * k
        fun bar(x: Float, top: Float, color: Color) = drawLine(color, Offset(x * k, 44 * k), Offset(x * k, top * k), strokeWidth = w, cap = StrokeCap.Round)
        bar(19f, 30f, Color(0xFF7C8CFF)); bar(32f, 20f, Color(0xFFFFC54D)); bar(45f, 25f, Color(0xFF34C79A))
        drawLine(Color(0xFFEDF0F5), Offset(19 * k, 44 * k), Offset(45 * k, 44 * k), strokeWidth = w, cap = StrokeCap.Round)
    }
}

/**
 * The app's soft purple glow from the top corner, the same on every page (Home, History, Settings, the editor).
 * It fades out completely inside the screen, so there is no visible edge anywhere.
 */
@Composable
fun KisGlow(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, color: Color? = null) {
    val c = Kis.colors
    val glow = color ?: c.accent
    Canvas(modifier) {
        val radius = minOf(size.width * 1.1f, size.height * 0.75f)
        drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(glow.copy(alpha = if (c.dark) 0.24f else 0.12f), glow.copy(alpha = if (c.dark) 0.12f else 0.06f), Color.Transparent),
            center = Offset(size.width * 0.15f, 0f), radius = radius))
    }
}

/** A thin rule used between facts and rows. */
@Composable
fun KisDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Kis.colors.line))
}
