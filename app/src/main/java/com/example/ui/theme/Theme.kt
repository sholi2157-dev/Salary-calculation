package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WorkDarkColorScheme = darkColorScheme(
    primary = WorkPalette.AccentText,
    onPrimary = WorkPalette.Canvas,
    primaryContainer = WorkPalette.Accent,
    onPrimaryContainer = WorkPalette.Text,
    secondary = WorkPalette.AccentText,
    secondaryContainer = WorkPalette.Selected,
    onSecondaryContainer = WorkPalette.Text,
    background = WorkPalette.Canvas,
    onBackground = WorkPalette.Text,
    surface = WorkPalette.Overlay,
    onSurface = WorkPalette.Text,
    surfaceVariant = FormSurface,
    onSurfaceVariant = WorkPalette.SecondaryText,
    surfaceTint = Color.Transparent,
    outline = WorkPalette.Outline,
    error = Color(0xFFF59090)
)

// Retain the public entry point; the canvas is intentionally still while logging work.
@Composable
fun AnimatedDarkBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(WorkPalette.Canvas)) { content() }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
        MaterialTheme(
            colorScheme = WorkDarkColorScheme,
            typography = Typography,
            shapes = androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
        ) {
            AnimatedDarkBackground { content() }
        }
    }
}
