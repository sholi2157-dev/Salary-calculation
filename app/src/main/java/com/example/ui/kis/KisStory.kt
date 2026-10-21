package com.example.ui.kis

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.domain.V2Summary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private val StoryDark = Color(0xFF121212)
private val InkOnMustard = Color(0xFF1A1200)
private fun place(i: Int) = Workplaces[i % Workplaces.size].from
private const val SLIDE_MS = 6_000

private class Slide(val bg: Color, val a: Int, val b: Int, val auto: Boolean = true, val tag: String, val content: @Composable () -> Unit)

/** "החודש/השנה שלך בכיס" for [scope] ("YYYY-MM" or "YYYY"): story slides that move on by themselves; a tap on the left
 * moves forward, on the right back, holding pauses. Amounts are hidden until asked for and included in the shared card. */
@Composable
fun KisStory(scope: String, entries: List<WorkEntry>, categories: List<WorkCategory>, onClose: () -> Unit, onMessage: (String) -> Unit) {
    val s = remember(entries, scope) { V2Summary.summary(KisHistoryLogic.done(entries).map { V2Summary.row(it) }, scope, V2Summary.today()) }
    val isYear = scope.length == 4
    val label = if (isYear) "השנה $scope" else HistoryFilter.monthName(monthKeyOf(scope) ?: 0)
    val whose = if (isYear) "השנה" else "החודש"
    var hidden by rememberSaveable { mutableStateOf(true) }
    val hrs = s.hours
    val top = s.places.firstOrNull()
    val persona = s.persona?.let { V2Summary.PERSONAS[it] }
    val personaLine = persona?.line?.replace("#", when (s.persona) {
        "marathon" -> String.format(java.util.Locale.US, "%.1f", hrs / maxOf(1, s.count)); else -> s.places.size.toString() })
    val slides = buildList {
        add(Slide(StoryDark, 0, 2, tag = "story_intro") {
            Kick("${androidx.compose.ui.res.stringResource(com.example.R.string.app_name)} · $label")
            Big("$whose שלך\nבכיס.")
            Para("מוכן? יש לנו כמה מספרים שאתה לא יודע על עצמך.")
        })
        add(Slide(place(0), 3, 4, tag = "story_hours") {
            Kick("הזמן שלך")
            CountUp(Math.round(hrs).toInt(), Modifier.testTag("story_hours_value"))
            Head("שעות עבודה ב-${s.count} משמרות.")
            s.comparison?.let { Para("זה בערך ${it.count} ${V2Summary.FUN[it.key]}. סתם שתדע.") }
        })
        if (s.places.isNotEmpty()) add(Slide(StoryDark, 1, 0, tag = "story_places") {
            Kick("המקומות המובילים שלך")
            s.places.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${i + 1}", style = TextStyle(fontFamily = SecularOne, fontSize = if (i == 0) 28.sp else 22.sp), color = LocalStoryInk.current.copy(alpha = if (i == 0) 1f else .7f))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(workplaceOf(p.name, categories).from))
                    Text(p.name, style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = if (i == 0) 26.sp else 20.sp), color = LocalStoryInk.current, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${KisFormat.hours(p.hours)} ש׳", style = TextStyle(fontFamily = Rubik, fontSize = 16.sp), color = LocalStoryInk.current.copy(alpha = .75f))
                }
            }
            top?.let { Para("${it.name} קיבל ממך ${Math.round(it.hours / maxOf(0.01, hrs) * 100)}% מהזמן. שיהיה ברור מי האהוב.") }
        })
        if (persona != null) add(Slide(place(if (s.persona == "owl") 4 else if (s.persona == "bird") 3 else 2), 4, 3, tag = "story_persona") {
            Kick("טיפוס העבודה שלך")
            Big(persona.name, Modifier.testTag("story_persona_name"))
            Para(personaLine ?: "")
        })
        add(Slide(place(2), 4, 3, tag = "story_longest") {
            val l = s.longest
            if (l == null) Big("$whose רגוע.")
            else {
                Kick("המשמרת הארוכה")
                Huge(hm(l.hours))
                Head(dayLabel(millisOf(l.date)) + (if (l.start != null && l.end != null) ", ⁦${l.start}–${l.end}⁩." else "."))
                Para("מגיע לך קפה. אולי שניים." + if (s.nights > 0) " ועוד ${s.nights} פעמים סיימת אחרי 23:00." else "")
            }
        })
        s.bestDay?.let { best -> add(Slide(StoryDark, 5, 1, tag = "story_days") {
            Kick("הקצב שלך")
            Head("יום ${KisFormat.DAY_LONG[best]} הוא היום שלך.")
            Bars(s.days, best, KisFormat.DAY_SHORT, place(3), 180.dp)
        }) }
        s.peakHour?.let { h -> add(Slide(place(5), 3, 4, tag = "story_hour") {
            Kick("השעה הכי פעילה שלך")
            Huge("%02d:00".format(h))
            Bars(s.hourMinutes.map { it.toDouble() }, h, null, Color.White, 90.dp)
            Para(when { h >= 22 || h < 5 -> "בזמן שכולם רואים סדרה במיטה, אתה בשטח."; h < 11 -> "קפה ראשון? אתה כבר באמצע העבודה."
                h < 17 -> "שעות השיא של העולם. וגם שלך."; else -> "השעה שבה כולם חוזרים הביתה, ואתה בדיוק בשיא." })
        }) }
        add(Slide(place(1), 3, 0, tag = "story_money") {
            Kick("השורה התחתונה")
            if (s.currencies.isEmpty()) Head("עוד חסר תעריף.")
            s.currencies.forEach { cur ->
                if (hidden) Text("${cur.currency}•••", style = KisType.hero.copy(fontSize = 64.sp, lineHeight = 70.sp), color = LocalStoryInk.current, modifier = Modifier.testTag("story_money_hidden"))
                else MoneyText(cur.total, cur.currency, KisType.hero.copy(fontSize = 64.sp, lineHeight = 70.sp), color = LocalStoryInk.current, modifier = Modifier.testTag("story_money_${cur.currency}"))
            }
            Para((if (s.pending > 0) "ועוד ${if (s.pending == 1) "משמרת אחת" else "${s.pending} משמרות"} שמחכות לתעריף. " else "") + "הסכומים מופיעים גם בכרטיס לשיתוף.")
            if (s.currencies.isNotEmpty()) StoryChip(if (hidden) "הצג לי" else "הסתר", "story_reveal") { hidden = !hidden }
        })
        add(Slide(StoryDark, 0, 3, auto = false, tag = "story_card") {
            val context = LocalContext.current
            Kick("$whose שלך בכיס")
            Poster(label, s, persona?.name)
            val storyRows = KisHistoryLogic.done(entries).filter { com.example.domain.V2Summary.row(it).date.startsWith(scope) }
            val text = com.example.data.WorkShareText.summary(storyRows)
            KisButton("העתקת סיכום", {
                val rows = KisHistoryLogic.done(entries).filter { com.example.domain.V2Summary.row(it).date.startsWith(scope) }
                KisShare.copy(context, com.example.data.WorkShareText.copy(rows)); onMessage("הועתק ✓")
            }, Modifier.fillMaxWidth(), KisButtonKind.GHOST, height = 48.dp, tag = "story_copy")
            KisButton("שיתוף הכרטיס", {
                val ok = runCatching { shareImage(context, cardBitmap(context, label, posterCells(s, persona?.name)), text) }.getOrDefault(false)
                if (!ok) KisShare.shareOrCopy(context, text, "$whose שלך בכיס", onMessage)
            }, Modifier.fillMaxWidth(), height = 54.dp, tag = "story_share")
        })
    }

    var index by rememberSaveable { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember(index) { Animatable(0f) }
    fun go(d: Int) { val n = index + d; if (n < 0) return; if (n >= slides.size) onClose() else index = n }
    LaunchedEffect(index, paused) {
        if (!paused && slides[index.coerceIn(slides.indices)].auto) {
            progress.animateTo(1f, tween(((1f - progress.value) * SLIDE_MS).toInt().coerceAtLeast(1), easing = LinearEasing))
            go(1)
        }
    }
    run {
        val slide = slides[index.coerceIn(slides.indices)]
        val ink = if (slide.bg == place(3)) InkOnMustard else Color.White
        val bg by animateColorAsState(slide.bg, tween(450), label = "story bg")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStoryInk provides ink) {
            BackHandler(onBack = onClose)
            // Covers the app: nothing underneath can be touched while the story is open.
            Box(Modifier.fillMaxSize().background(bg).pointerInput(Unit) { detectTapGestures { } }
                .semantics { stateDescription = "${index + 1}/${slides.size}" }.testTag("story_root")) {
                Blobs(place(slide.a), place(slide.b), progress.value)
                // Tap areas under the content: the left half moves forward (RTL), the right half back; holding pauses.
                Row(Modifier.fillMaxSize()) {
                    val scopeCo = rememberCoroutineScope()
                    for ((tag, step) in listOf("story_prev" to -1, "story_next" to 1)) Box(Modifier.weight(1f).fillMaxHeight()
                        .pointerInput(index) { detectTapGestures(onPress = { val hold = scopeCo.launch { delay(220); paused = true }; tryAwaitRelease(); hold.cancel(); paused = false },
                            onTap = { go(step) }) }
                        .semantics { contentDescription = if (step > 0) "הבא" else "הקודם"; onClick { go(step); true } }.testTag(tag))
                }
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        slides.indices.forEach { j ->
                            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = .28f))) {
                                val f = if (j < index) 1f else if (j == index) progress.value else 0f
                                Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(ink))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        KisLogo(Modifier.size(26.dp)); Spacer(Modifier.width(8.dp))
                        Text(label, style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = ink, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Close, "סגירה", tint = ink, modifier = Modifier.size(40.dp).clip(CircleShape).background(ink.copy(alpha = .14f))
                            .kisClick(onClick = onClose).padding(9.dp).testTag("story_close"))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        key(index) {
                            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag(slide.tag), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                val rise = remember { Animatable(0f) }
                                LaunchedEffect(Unit) { rise.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
                                Column(Modifier.graphicsLayer { alpha = rise.value; translationY = (1f - rise.value) * 24.dp.toPx() }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    slide.content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val LocalStoryInk = staticCompositionLocalOf { Color.White }

@Composable private fun Kick(text: String) = Text(text, style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 15.sp), color = LocalStoryInk.current.copy(alpha = .85f))
@Composable private fun Big(text: String, modifier: Modifier = Modifier) = Text(text, style = TextStyle(fontFamily = SecularOne, fontSize = 52.sp, lineHeight = 56.sp), color = LocalStoryInk.current, modifier = modifier)
@Composable private fun Head(text: String) = Text(text, style = TextStyle(fontFamily = SecularOne, fontSize = 30.sp, lineHeight = 36.sp), color = LocalStoryInk.current)
@Composable private fun Huge(text: String) = Text(text, style = TextStyle(fontFamily = SecularOne, fontSize = 110.sp, lineHeight = 104.sp, letterSpacing = (-2).sp), color = LocalStoryInk.current)
@Composable private fun Para(text: String) = Text(text, style = TextStyle(fontFamily = Rubik, fontSize = 18.sp, lineHeight = 26.sp), color = LocalStoryInk.current.copy(alpha = .92f))

@Composable private fun CountUp(to: Int, modifier: Modifier) {
    val v = remember { Animatable(0f) }
    LaunchedEffect(to) { v.animateTo(to.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    Text("${v.value.toInt()}", style = TextStyle(fontFamily = SecularOne, fontSize = 130.sp, lineHeight = 120.sp, letterSpacing = (-3).sp), color = LocalStoryInk.current, modifier = modifier)
}

@Composable private fun StoryChip(text: String, tag: String, onClick: () -> Unit) {
    val ink = LocalStoryInk.current
    Box(Modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(99.dp)).border(2.dp, ink, RoundedCornerShape(99.dp)).kisClick(onClick = onClick).testTag(tag)
        .padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 15.sp), color = ink)
    }
}

/** Bars from the bottom; the [best] one is highlighted. Labels under the bars when given. */
@Composable private fun Bars(values: List<Double>, best: Int, labels: List<String>?, highlight: Color, height: androidx.compose.ui.unit.Dp) {
    val ink = LocalStoryInk.current
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    // Time runs left to right for hours (like a clock), and in reading order for weekdays.
    CompositionLocalProvider(LocalLayoutDirection provides if (labels == null) LayoutDirection.Ltr else LayoutDirection.Rtl) {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(if (labels == null) 3.dp else 8.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(((v / max).toFloat() * grow.value).coerceAtLeast(0.03f) * if (labels != null) 0.85f else 1f)
                        .clip(RoundedCornerShape(if (labels == null) 3.dp else 8.dp)).background(if (i == best) highlight else ink.copy(alpha = if (labels == null) .35f else .22f)))
                    labels?.let { Text(it[i], style = TextStyle(fontFamily = Rubik, fontSize = 13.sp), color = ink.copy(alpha = .75f), modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
    }
}

/** Soft shapes that drift with the slide's own progress (no endless animation, so pausing holds them too). */
@Composable private fun Blobs(a: Color, b: Color, move: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val m = size.minDimension
        drawCircle(a.copy(alpha = .5f), radius = m * .31f, center = Offset(size.width * (.08f + .1f * move), size.height * (.12f + .08f * move)))
        drawCircle(b.copy(alpha = .45f), radius = m * .23f, center = Offset(size.width * (.95f - .08f * move), size.height * (.9f - .06f * move)))
        drawCircle(b.copy(alpha = .5f), radius = m * .15f, center = Offset(size.width * (.82f - .05f * move), size.height * .14f), style = Stroke(10.dp.toPx()))
    }
}

/** The shareable card includes the exact amounts per currency. */
@Composable private fun Poster(label: String, s: V2Summary.Summary, persona: String?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(place(0), place(4)))).padding(20.dp).testTag("story_poster"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(fontFamily = SecularOne, fontSize = 22.sp), color = Color.White, modifier = Modifier.weight(1f))
            KisLogo(Modifier.size(36.dp))
        }
        val cells = posterCells(s, persona)
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (k, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(k, style = TextStyle(fontFamily = Rubik, fontSize = 13.sp), color = Color.White.copy(alpha = .8f))
                        Text(v, style = TextStyle(fontFamily = Rubik, fontSize = 22.sp, lineHeight = 26.sp), color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun posterCells(s: V2Summary.Summary, persona: String?) = s.currencies.map { "סכום (${it.currency})" to KisFormat.money(it.total, it.currency) } +
    (if (s.pending > 0) listOf("סכום חסר" to "${s.pending} משמרות") else emptyList()) + listOf("שעות" to "${Math.round(s.hours)}", "משמרות" to "${s.count}",
    "מקום מוביל" to (s.places.firstOrNull()?.name ?: "—"), "טיפוס" to (persona ?: "—"), "היום שלך" to (s.bestDay?.let { KisFormat.DAY_LONG[it] } ?: "—"),
    "הכי ארוכה" to (s.longest?.let { hm(it.hours) } ?: "—"))

/** The shared picture (1080×1080), drawn directly so it never depends on what is on screen. Includes amounts per currency. */
internal fun cardBitmap(context: Context, label: String, cells: List<Pair<String, String>>): android.graphics.Bitmap {
    val size = 1080f
    val height = maxOf(1080, 350 + ((cells.size + 1) / 2) * 180)
    val bmp = android.graphics.Bitmap.createBitmap(size.toInt(), height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(0f, 0f, size, size, place(0).toArgb(), place(4).toArgb(), android.graphics.Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(0f, 0f, size, height.toFloat(), 64f, 64f, bg)
    val display = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, com.example.R.font.secular_one) }.getOrNull()
    val body = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, com.example.R.font.rubik_regular) }.getOrNull()
    fun paint(face: android.graphics.Typeface?, px: Float, alpha: Int = 255) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; this.alpha = alpha; textSize = px; typeface = face; textAlign = android.graphics.Paint.Align.RIGHT
    }
    canvas.drawText(label, size - 80f, 170f, paint(display, 64f))
    canvas.drawText("בכיס", size - 80f, 240f, paint(display, 44f))
    cells.forEachIndexed { n, (k, v) ->
        val right = if (n % 2 == 0) size - 80f else size / 2 - 20f
        val top = 330f + (n / 2) * 180f
        canvas.drawText(k, right, top, paint(body, 36f, 205))
        val value = paint(body, 56f)
        if (k.startsWith("סכום")) while (value.measureText(v) > size / 2 - 120f && value.textSize > 12f) value.textSize -= 1f
        var text = v
        while (value.measureText(text) > size / 2 - 120f && text.length > 1) text = text.dropLast(1)
        canvas.drawText(if (text == v) v else "$text…", right, top + 86f, value)
    }
    return bmp
}

private fun hm(hours: Double): String { val m = Math.round(hours * 60); return "${m / 60}:${"%02d".format(m % 60)}" }
private fun millisOf(date: String) = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** The card as a picture through the Android share sheet. False when nothing can receive it. */
private fun shareImage(context: Context, bitmap: android.graphics.Bitmap, text: String): Boolean {
    val file = java.io.File(context.cacheDir, "kis-card.png")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, text); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    return try { context.startActivity(Intent.createChooser(send, "שיתוף הכרטיס").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
    catch (_: android.content.ActivityNotFoundException) { false }
}
