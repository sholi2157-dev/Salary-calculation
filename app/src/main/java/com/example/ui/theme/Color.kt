package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Light high density palette
val HighDensityPrimary = Color(0xFF3B5BA9)
val HighDensityOnPrimary = Color(0xFFFFFFFF)
val HighDensityPrimaryContainer = Color(0xFFDEE1F9)
val HighDensityOnPrimaryContainer = Color(0xFF1B1B1F)

val HighDensitySecondary = Color(0xFF4F5D84)
val HighDensitySecondaryContainer = Color(0xFFE1E2EC)
val HighDensityOnSecondaryContainer = Color(0xFF1B1B1F)

val HighDensityBackground = Color(0xFFF3F4F9)
val HighDensitySurface = Color(0xFFFFFFFF)
val HighDensityOutline = Color(0xFFC5C6D0)
val HighDensityError = Color(0xFFBA1A1A)

val HighDensityTodayBg = Color(0xFFDEE1F9)
val HighDensityWeekBg = Color(0xFFDEE1F9)
val HighDensityMonthBg = Color(0xFFD3E3FD)
val HighDensityTotalBg = Color(0xFFE1E2EC)

// Dark version just in case system handles dark mode
val DarkPrimary = Color(0xFFAEC6FF)
val DarkOnPrimary = Color(0xFF002E6C)
val DarkPrimaryContainer = Color(0xFF1E3F8F)
val DarkBackground = Color(0xFF111318)
val DarkSurface = Color(0xFF1A1C1E)

// A quiet work ledger: ink canvas, paper-like layers and a single blue action hue.
object WorkPalette {
    val Canvas = Color(0xFF10151E)
    val Card = Color(0xFF1A2230)
    val Overlay = Color(0xFF242E3E)
    val Control = Color(0xFF141C28)
    val Selected = Color(0xFF293D59)
    val Accent = Color(0xFF476EA8)
    val AccentText = Color(0xFFA7C7F4)
    val Text = Color(0xFFF1F4F8)
    val SecondaryText = Color(0xFFB2BDCC)
    val MutedText = Color(0xFF9AA8BC)
    val Outline = Color(0x24FFFFFF)
}
val FormSurface = WorkPalette.Card
