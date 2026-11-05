package com.example.ui.kis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * R-33: where the ruler draws a shift. The ruler is a 24-hour window, right to left. A night shift that crosses
 * midnight inside noon→noon (20:30–00:30, 22:00–06:00) is drawn on a noon-to-noon window, so it is one span with 00 in
 * the middle, not two pieces at the edges. Every other shift keeps the 00→24 window. Only the drawing changes: the
 * times, hours and money are the same. Same rule as the web editor (`rulerLayout` in web/kis/editor.js).
 */
data class RulerLayout(val origin: Int, val spans: List<Pair<Int, Int>>, val ticks: List<Pair<Int, String>>) {
    /** Real minutes (0..1439) → position on the window (0..1439). */
    fun at(minutes: Int): Int = Math.floorMod(minutes - origin, 1440)
    /** Position on the window → real minutes. */
    fun fromWindow(position: Int): Int = Math.floorMod(position + origin, 1440)
}

/** [keepOrigin]: the window chosen when a drag began, kept until the finger lifts so the handle stays under it. */
fun rulerLayout(start: String?, end: String, keepOrigin: Int? = null): RulerLayout {
    val a = start?.let(V2Rules::toMinutes); val b = V2Rules.toMinutes(end)
    val origin = keepOrigin ?: if (a != null && a >= 720 && b < 720) 720 else 0
    fun at(m: Int) = Math.floorMod(m - origin, 1440)
    val spans = when {
        a == null -> emptyList()
        at(b) > at(a) -> listOf(at(a) to at(b))
        else -> listOf(at(a) to 1440, 0 to at(b)).filter { it.second > it.first }
    }
    val ticks = listOf(0, 6, 12, 18, 24).map { h ->
        h * 60 to String.format(java.util.Locale.US, "%02d", if (h == 24) (if (origin != 0) 12 else 24) else (h + origin / 60) % 24)
    }
    return RulerLayout(origin, spans, ticks)
}

/**
 * A 24-hour ruler, right to left (00 on the right; 12 on the right for a night shift, see [rulerLayout]). Only the handles move, only after a deliberate
 * 8dp drag, and always in 15-minute steps with a tick haptic — a stray touch never changes a time.
 * Start sits above the track and end below it, so the two never cover each other.
 */
@Composable
fun TimeRuler(start: String?, end: String, selected: TimeHandle, ink: Color, surface: Color,
              onChange: (TimeHandle, String) -> Unit, onSelect: (TimeHandle) -> Unit, modifier: Modifier = Modifier, accent: Color = ink) {
    val density = LocalDensity.current
    val settings = Kis.settings
    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)
    val emitChange by rememberUpdatedState(onChange)
    val emitSelect by rememberUpdatedState(onSelect)
    // The window is fixed for the whole drag (taken when the finger goes down), so the handle does not jump mid-drag.
    var dragOrigin by remember { mutableStateOf<Int?>(null) }
    // Children are positioned with absolute x (00 at the right edge), so align them from the absolute left.
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 26.dp).height(86.dp).testTag("time_ruler"), contentAlignment = AbsoluteAlignment.TopLeft) {
        val widthPx = with(density) { maxWidth.toPx() }
        fun xOf(minutes: Int): Float = widthPx - minutes / 1440f * widthPx // RTL: 00 at the right edge
        fun minutesAt(x: Float): Int = ((((widthPx - x) / widthPx) * 96f).roundToInt() * 15).coerceIn(0, 1425)
        val layout = rulerLayout(start, end, dragOrigin)
        val handleW = 52.dp
        val trackTop = 32.dp
        // Track and the worked span (one span for a night shift on the noon-to-noon window, see [rulerLayout]).
        Box(Modifier.absoluteOffset(y = trackTop).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.12f)))
        layout.spans.forEach { (from, to) ->
            val left = with(density) { xOf(to).toDp() }
            val w = with(density) { (xOf(from) - xOf(to)).toDp() }
            Box(Modifier.absoluteOffset(x = left, y = trackTop).width(w).height(12.dp).clip(RoundedCornerShape(8.dp)).background(accent))
        }
        @Composable fun handle(h: TimeHandle, value: String, top: Dp, label: String) {
            val on = selected == h
            val x = with(density) { xOf(layout.at(V2Rules.toMinutes(value))).toDp() } - handleW / 2
            Box(Modifier.absoluteOffset(x = x, y = top).size(width = handleW, height = 28.dp).clip(RoundedCornerShape(12.dp))
                .background(if (on) ink else surface).border(2.dp, ink, RoundedCornerShape(12.dp))
                .semantics { contentDescription = "גרירת $label · $value" }.testTag("ruler_${h.name.lowercase()}"), contentAlignment = Alignment.Center) {
                Text(label, color = if (on) surface else ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Rubik, maxLines = 1)
            }
        }
        if (start != null) handle(TimeHandle.START, start, 0.dp, "התחלה")
        handle(TimeHandle.END, end, 46.dp, "סיום")
        layout.ticks.forEach { (at, label) ->
            val x = with(density) { xOf(at).toDp() } - 10.dp
            Text(label, Modifier.absoluteOffset(x = x, y = 70.dp).width(20.dp), color = ink.copy(alpha = 0.75f),
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
                val window = rulerLayout(currentStart, currentEnd)
                val target = when {
                    sMin != null && y < trackTop.toPx() + 6.dp.toPx() && abs(down.position.x - xOf(window.at(sMin))) <= half -> TimeHandle.START
                    y >= trackTop.toPx() && abs(down.position.x - xOf(window.at(eMin))) <= half -> TimeHandle.END
                    else -> null
                } ?: return@awaitEachGesture
                down.consume()
                dragOrigin = window.origin
                var live = false
                var last = if (target == TimeHandle.START) sMin!! else eMin
                try { while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) break
                    if (!live && abs(pointer.position.x - down.position.x) < slop) continue
                    live = true
                    pointer.consume()
                    val m = window.fromWindow(minutesAt(pointer.position.x))
                    if (m != last) {
                        last = m
                        settings.haptic(KisSettings.Haptic.TICK)
                        emitChange(target, V2Rules.fromMinutes(m))
                    }
                } } finally { dragOrigin = null }
                emitSelect(target)
            }
        })
    }
}

/** [time] moved by [minutes], wrapping around midnight. */
fun nudge(time: String, minutes: Int): String = V2Rules.fromMinutes(V2Rules.toMinutes(time) + minutes)
