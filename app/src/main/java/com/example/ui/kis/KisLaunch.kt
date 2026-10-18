package com.example.ui.kis

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The 2.0 launch, once per installation (new or updated): the new look says hello in a few words, then asks for the
 * name (first and last; only the first is ever shown). [onDone] then starts the short tour from Home.
 */
@Composable
fun KisLaunch(onDone: () -> Unit) {
    val c = Kis.colors
    val settings = Kis.settings
    var step by rememberSaveable { mutableIntStateOf(0) }
    var first by rememberSaveable { mutableStateOf(settings.name) }
    var last by rememberSaveable { mutableStateOf(settings.lastName) }
    BackHandler(enabled = step == 1) { step = 0 }
    // Nothing underneath is reachable while the launch is on screen. A tap detector (not a consume-everything loop):
    // consuming every move made a real finger's slight movement cancel the buttons' taps.
    Box(Modifier.fillMaxSize().background(c.bg).pointerInput(Unit) { detectTapGestures { } }.testTag("launch_root")) {
        KisGlow(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Spacer(Modifier.weight(1f))
            if (step == 0) {
                Rise("עיצוב חדש.", 150, "launch_line_1")
                Rise("התחלה חדשה.", 700, "launch_line_2")
                Spacer(Modifier.weight(1f))
                KisButton("הבא", { step = 1 }, Modifier.fillMaxWidth(), height = 56.dp, tag = "launch_next")
            } else {
                Text("איך קוראים לך?", style = KisType.page.copy(fontSize = 34.sp, lineHeight = 40.sp), color = c.ink, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(18.dp))
                KisTextField(first, { first = it.take(40) }, "שם פרטי", Modifier.fillMaxWidth().testTag("launch_first"))
                Spacer(Modifier.height(10.dp))
                KisTextField(last, { last = it.take(40) }, "שם משפחה", Modifier.fillMaxWidth().testTag("launch_last"))
                Spacer(Modifier.weight(1f))
                KisButton("המשך", { settings.updateName(first.trim()); settings.updateLastName(last.trim()); onDone() }, Modifier.fillMaxWidth(), height = 56.dp,
                    enabled = first.isNotBlank(), tag = "launch_done")
                KisButton("אחר כך", onDone, Modifier.fillMaxWidth().padding(top = 6.dp), KisButtonKind.GHOST, height = 48.dp, tag = "launch_later")
            }
        }
    }
}

/** A line of the slogan that rises into place. */
@Composable
private fun Rise(text: String, delayMs: Long, tag: String) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs); shown.animateTo(1f, tween(650)) }
    Text(text, style = KisType.hero.copy(fontSize = 54.sp, lineHeight = 60.sp), color = Kis.colors.ink,
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * 28.dp.toPx() }.testTag(tag))
}
