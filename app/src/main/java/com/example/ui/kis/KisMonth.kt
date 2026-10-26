package com.example.ui.kis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.WorkEntry

/** Month key (HistoryFilter.monthKey) of a "YYYY-MM" string, or null. */
fun monthKeyOf(month: String): Int? = runCatching { val (y, m) = month.split("-").map { it.toInt() }; y * 12 + m - 1 }.getOrNull()

/** The way into a recap: the app's purple card with its name and the logo. */
@Composable
fun RecapEntry(title: String, subtitle: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Workplaces[0].from, Workplaces[4].from)))
        .kisClick(onClick = onClick).testTag(tag).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KisType.title, color = Color.White)
            Text(subtitle, style = KisType.caption, color = Color.White.copy(alpha = .85f))
        }
        KisLogo(Modifier.size(34.dp))
    }
}

private fun hasShifts(done: List<WorkEntry>, scope: String) = done.any { com.example.domain.V2Summary.row(it).date.startsWith(scope) }
fun recapTitle(scope: String) = if (scope.length == 4) "השנה $scope בכיס" else "${KisFormat.MONTHS[(monthKeyOf(scope) ?: 0) % 12]} ${scope.take(4)} בכיס"

/** The recaps that are new today and have shifts (Home shows them from the 30th for 4 more days). */
fun freshRecaps(entries: List<WorkEntry>, today: String = com.example.domain.V2Summary.today()): List<String> {
    val done = KisHistoryLogic.done(entries)
    val n = com.example.domain.V2Summary.recapNew(today)
    return listOfNotNull(n.year, n.month).filter { hasShifts(done, it) }
}

/** "הסיכומים שלי" (Settings): every recap that has opened and has shifts, newest first. */
@Composable
fun RecapArchiveSheet(entries: List<WorkEntry>, onStory: (String) -> Unit, onDismiss: () -> Unit, today: String = com.example.domain.V2Summary.today()) {
    val c = Kis.colors
    val done = remember(entries) { KisHistoryLogic.done(entries) }
    val list = remember(done, today) { com.example.domain.V2Summary.recapArchive(today).filter { hasShifts(done, it) } }
    KisSheet(onDismiss = onDismiss, tag = "recaps_sheet") {
        Text("הסיכומים שלי", style = KisType.title, color = c.ink)
        if (list.isEmpty()) {
            val first = com.example.domain.V2Summary.FIRST_RECAP
            Text("הסיכום הראשון יגיע ב-${com.example.domain.V2Summary.recapDay(first).takeLast(2).toInt()} ב${KisFormat.MONTHS[(monthKeyOf(first) ?: 0) % 12]}.",
                style = KisType.body, color = c.muted, modifier = Modifier.testTag("recaps_empty"))
        } else Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { scope ->
                val s = remember(done, scope) { com.example.domain.V2Summary.summary(done.map { com.example.domain.V2Summary.row(it) }, scope, today) }
                RecapEntry(recapTitle(scope), "${if (s.count == 1) "משמרת אחת" else "${s.count} משמרות"} · ${KisFormat.hours(s.hours)} שעות", "recap_$scope") { onStory(scope) }
            }
        }
    }
}
