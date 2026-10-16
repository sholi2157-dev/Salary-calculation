package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.FormSurface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.WorkEntry

@Composable
fun HistoryToolbar(
    entries: List<WorkEntry>,
    currencyFilter: String,
    activeFilterCount: Int,
    onFilters: () -> Unit,
    onCurrencyFilterChange: (String) -> Unit,
    onSort: (String) -> Unit,
    onCopyExcel: () -> Unit,
    onCopyWhatsApp: () -> Unit
) {
    var showCopyMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().testTag("history_toolbar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The total can wrap for mixed currencies; actions retain 48dp touch targets.
        CompactHistoryTotal(entries, Modifier.weight(1f).padding(end = 4.dp))
        BadgedBox(badge = {
            if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) }
        }) {
            IconButton(onClick = onFilters, modifier = Modifier.testTag("history_filters")) {
                Icon(Icons.Outlined.FilterList, if (activeFilterCount == 0) "סינון" else "סינון · $activeFilterCount פעילים", tint = Color(0xFFC7D2FE))
            }
        }
        Box {
            IconButton(onClick = { showCopyMenu = true }, modifier = Modifier.testTag("copy_menu_btn")) {
                Icon(Icons.Outlined.ContentCopy, "שתף דוח", tint = Color(0xFFC7D2FE))
            }
            DropdownMenu(expanded = showCopyMenu, onDismissRequest = { showCopyMenu = false }, modifier = Modifier.background(FormSurface)) {
                DropdownMenuItem(text = { Text("Excel (ייצוא)", color = Color.White) }, onClick = {
                    showCopyMenu = false
                    onCopyExcel()
                })
                DropdownMenuItem(text = { Text("WhatsApp", color = Color.White) }, onClick = {
                    showCopyMenu = false
                    onCopyWhatsApp()
                })
            }
        }
        Box {
            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.testTag("history_sort")) {
                Icon(Icons.Outlined.Sort, "מיון", tint = Color(0xFFC7D2FE))
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }, modifier = Modifier.background(FormSurface)) {
                listOf("newest" to "תאריך: מהחדש לישן", "oldest" to "תאריך: מהישן לחדש", "latest_added" to "נוסף לאחרונה").forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(label, color = Color.White) }, onClick = {
                        showSortMenu = false
                        onSort(value)
                    })
                }
            }
        }
        TextButton(
            onClick = {
                onCurrencyFilterChange(when (currencyFilter) {
                    "הכל" -> "₪"
                    "₪" -> "$"
                    else -> "הכל"
                })
            },
            modifier = Modifier.sizeIn(minWidth = 48.dp).testTag("currency_filter_btn"),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { Text(currencyFilter) }
    }
}
