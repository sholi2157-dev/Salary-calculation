package com.example.ui.kis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.V2Rules
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TimeHandle { START, END }

/**
 * A 24-hour ruler, right to left (00 on the right). Only the handles move, only after a deliberate
 * 8dp drag, and always in 15-minute steps with a tick haptic — a stray touch never changes a time.
 * Start sits above the track and end below it, so the two never cover each other.
 */
@Composable
fun TimeRuler(start: String?, end: String, selected: TimeHandle, ink: Color, surface: Color,
              onChange: (TimeHandle, String) -> Unit, onSelect: (TimeHandle) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val settings = Kis.settings
    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)
    val emitChange by rememberUpdatedState(onChange)
    val emitSelect by rememberUpdatedState(onSelect)
    // Children are positioned with absolute x (00 at the right edge), so align them from the absolute left.
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 26.dp).height(86.dp).testTag("time_ruler"), contentAlignment = AbsoluteAlignment.TopLeft) {
        val widthPx = with(density) { maxWidth.toPx() }
        fun xOf(minutes: Int): Float = widthPx - minutes / 1440f * widthPx // RTL: 00 at the right edge
        fun minutesAt(x: Float): Int = ((((widthPx - x) / widthPx) * 96f).roundToInt() * 15).coerceIn(0, 1425)
        val handleW = 52.dp
        val trackTop = 32.dp
        // Track and the worked span (two pieces when it crosses midnight).
        Box(Modifier.absoluteOffset(y = trackTop).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(ink.copy(alpha = 0.22f)))
        if (start != null) {
            val a = V2Rules.toMinutes(start); val b = V2Rules.toMinutes(end)
            val pieces = if (b > a) listOf(a to b) else listOf(a to 1440, 0 to b)
            pieces.filter { it.second > it.first }.forEach { (from, to) ->
                val left = with(density) { xOf(to).toDp() }
                val w = with(density) { (xOf(from) - xOf(to)).toDp() }
                Box(Modifier.absoluteOffset(x = left, y = trackTop).width(w).height(12.dp).clip(RoundedCornerShape(6.dp)).background(ink))
            }
        }
        @Composable fun handle(h: TimeHandle, value: String, top: Dp, label: String) {
            val on = selected == h
            val x = with(density) { xOf(V2Rules.toMinutes(value)).toDp() } - handleW / 2
            Box(Modifier.absoluteOffset(x = x, y = top).size(width = handleW, height = 28.dp).clip(RoundedCornerShape(12.dp))
                .background(if (on) ink else surface).border(2.dp, ink, RoundedCornerShape(12.dp))
                .semantics { contentDescription = "גרירת $label · $value" }.testTag("ruler_${h.name.lowercase()}"), contentAlignment = Alignment.Center) {
                Text(label, color = if (on) surface else ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Rubik, maxLines = 1)
            }
        }
        if (start != null) handle(TimeHandle.START, start, 0.dp, "התחלה")
        handle(TimeHandle.END, end, 46.dp, "סיום")
        listOf(0, 6, 12, 18, 24).forEach { hour ->
            val x = with(density) { (widthPx - hour / 24f * widthPx).toDp() } - 10.dp
            Text(String.format(java.util.Locale.US, "%02d", hour), Modifier.absoluteOffset(x = x, y = 70.dp).width(20.dp), color = ink.copy(alpha = 0.75f),
                fontSize = 10.sp, fontFamily = Rubik, textAlign = TextAlign.Center)
        }
        // Gesture layer: pick the nearest handle under the finger; ignore touches on the bare track.
        Box(Modifier.matchParentSize().pointerInput(widthPx) {
            val slop = 8.dp.toPx()
            val half = handleW.toPx() / 2 + 6.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val y = down.position.y
                val sMin = currentStart?.let(V2Rules::toMinutes)
                val eMin = V2Rules.toMinutes(currentEnd)
                val target = when {
                    sMin != null && y < trackTop.toPx() + 6.dp.toPx() && abs(down.position.x - xOf(sMin)) <= half -> TimeHandle.START
                    y >= trackTop.toPx() && abs(down.position.x - xOf(eMin)) <= half -> TimeHandle.END
                    else -> null
                } ?: return@awaitEachGesture
                down.consume()
                var live = false
                var last = if (target == TimeHandle.START) sMin!! else eMin
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) break
                    if (!live && abs(pointer.position.x - down.position.x) < slop) continue
                    live = true
                    pointer.consume()
                    val m = minutesAt(pointer.position.x)
                    if (m != last) {
                        last = m
                        settings.haptic(KisSettings.Haptic.TICK)
                        emitChange(target, V2Rules.fromMinutes(m))
                    }
                }
                emitSelect(target)
            }
        })
    }
}

/** −30 −15 +15 +30 for the selected handle. Times wrap around midnight. */
@Composable
fun TimeNudges(label: String, ink: Color, onNudge: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label:", color = ink.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = Rubik, modifier = Modifier.widthIn(min = 48.dp))
        listOf(-30, -15, 15, 30).forEach { n ->
            Box(Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.16f))
                .kisClick(haptic = KisSettings.Haptic.TICK) { onNudge(n) }.testTag("nudge_$n"), contentAlignment = Alignment.Center) {
                Text(if (n > 0) "+$n" else "−${-n}", color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = Rubik)
            }
        }
    }
}

fun nudge(time: String, minutes: Int): String = V2Rules.fromMinutes(V2Rules.toMinutes(time) + minutes)
