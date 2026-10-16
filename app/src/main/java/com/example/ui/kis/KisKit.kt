package com.example.ui.kis

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

object KisFormat {
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
fun MoneyText(amount: Double, currency: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, symbolScale: Float = 0.62f) {
    Text(moneyString(amount, currency, symbolScale), modifier = modifier, style = style.copy(textDirection = TextDirection.Ltr, fontFeatureSettings = "tnum"),
        color = if (color == Color.Unspecified) Kis.colors.ink else color, maxLines = 1, softWrap = false)
}

// ---------------------------------------------------------------- interaction

/** Pressed-state scale (0.97) plus a light tap haptic. */
@Composable
fun Modifier.kisClick(enabled: Boolean = true, role: Role? = Role.Button, haptic: KisSettings.Haptic? = KisSettings.Haptic.TAP, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(80), label = "press")
    val settings = Kis.settings
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, role = role) {
            haptic?.let(settings::haptic); onClick()
        }
}

enum class KisButtonKind { PRIMARY, GHOST, DANGER, OUTLINE }

@Composable
fun KisButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: KisButtonKind = KisButtonKind.PRIMARY, enabled: Boolean = true,
              icon: ImageVector? = null, height: Dp = 54.dp, haptic: KisSettings.Haptic? = KisSettings.Haptic.TAP, tag: String? = null) {
    val c = Kis.colors
    val (bg, fg) = when (kind) {
        KisButtonKind.PRIMARY -> c.ink to c.onInk
        KisButtonKind.GHOST -> c.sunk to c.ink
        KisButtonKind.DANGER -> c.sunk to c.danger
        KisButtonKind.OUTLINE -> Color.Transparent to c.ink
    }
    Row(modifier.heightIn(min = height).clip(RoundedCornerShape(16.dp)).background(bg)
        .then(if (kind == KisButtonKind.OUTLINE) Modifier.border(1.5.dp, c.line, RoundedCornerShape(16.dp)) else Modifier)
        .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
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
    Column(modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).background(if (primary) c.ink else c.sunk)
        .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
        .kisClick(enabled = enabled, onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        val fg = if (primary) c.onInk else if (danger) c.danger else c.ink
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, style = KisType.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

@Composable
fun KisChip(text: String, selected: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier, dashed: Boolean = false, warn: Boolean = false,
            leading: ImageVector? = null, tag: String? = null, minHeight: Dp = 40.dp) {
    val c = Kis.colors
    val shape = RoundedCornerShape(12.dp)
    val bg = when { warn -> c.pendingSoft; else -> c.surface }
    val fg = if (warn) c.pending else c.ink
    Row(modifier.heightIn(min = minHeight).clip(shape).background(bg)
        .then(when {
            warn -> Modifier
            dashed -> Modifier.dashedBorder(c.line, 12.dp)
            selected -> Modifier.border(2.dp, c.ink, shape)
            else -> Modifier.border(1.5.dp, c.line, shape)
        })
        .kisClick(onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .semantics { this.selected = selected }
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (leading != null) Icon(leading, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = KisType.label, color = fg, maxLines = 1)
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
        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Workplaces[1].accent(c.dark), checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = c.line, uncheckedBorderColor = Color.Transparent))
}

/** Segmented control (tseg): a sunk track with a raised surface for the chosen option. */
@Composable
fun <T> KisSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, tagPrefix: String? = null) {
    val c = Kis.colors
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(c.sunk).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(9.dp)).background(if (on) c.surface else Color.Transparent)
                .kisClick(role = Role.RadioButton) { onSelect(value) }.semantics { this.selected = on }
                .then(if (tagPrefix != null) Modifier.testTag("${tagPrefix}_$value") else Modifier)
                .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, style = KisType.label.copy(fontSize = 13.sp), color = if (on) c.ink else c.muted, maxLines = 1)
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
            Box(Modifier.size(width = 48.dp, height = 40.dp).background(if (on) c.ink else c.surface)
                .kisClick(role = Role.RadioButton) { onCurrency(value) }.semantics { this.selected = on; contentDescription = if (value == "₪") "שקל" else "דולר" }
                .testTag("${tag}_$value"), contentAlignment = Alignment.Center) {
                Text(value, style = KisType.label.copy(fontFamily = Rubik), color = if (on) c.onInk else c.ink)
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
    Text(text, modifier.clip(RoundedCornerShape(6.dp)).background(color ?: if (warn) c.pendingSoft else c.sunk).padding(horizontal = 7.dp, vertical = 1.dp),
        style = KisType.micro, color = ink ?: if (warn) c.pending else c.muted, maxLines = 1)
}

/** Workplace initial on its colour. Dashed outline for a planned (not yet worked) shift. */
@Composable
fun Avatar(name: String, workplace: Workplace, size: Dp = 44.dp, planned: Boolean = false) {
    val c = Kis.colors
    val accent = workplace.accent(c.dark)
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp))
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
                 visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None) {
    val c = Kis.colors
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = modifier, label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.muted) } }, singleLine = singleLine, minLines = minLines,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, isError = isError, enabled = enabled, visualTransformation = visualTransformation,
        textStyle = KisType.body.copy(color = c.ink, textDirection = TextDirection.ContentOrRtl), shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = c.bg, unfocusedContainerColor = c.bg, disabledContainerColor = c.bg,
            focusedBorderColor = c.ink, unfocusedBorderColor = c.line, focusedLabelColor = c.ink, unfocusedLabelColor = c.muted, cursorColor = c.ink,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink, disabledTextColor = c.muted, errorBorderColor = c.danger, errorLabelColor = c.danger))
}

/** Bottom sheet in Kis surface colours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KisSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Kis.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface,
        contentColor = c.ink, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.line)) }) {
        Column(modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(horizontal = 16.dp).padding(bottom = 18.dp)
            .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
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

/** A thin rule used between facts and rows. */
@Composable
fun KisDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Kis.colors.line))
}
