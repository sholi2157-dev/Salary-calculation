package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val GlassmorphicDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Clean modern indigo accent
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x331E293B),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF818CF8),
    secondaryContainer = Color(0x331E293B),
    onSecondaryContainer = Color(0xFFFFFFFF),
    background = Color.Transparent, // Pure transparent to show animated background
    onBackground = Color(0xFFE5E5EA),
    surface = Color(0x331E293B), // Translucent glassmorphic surface
    onSurface = Color(0xFFE5E5EA),
    surfaceVariant = Color(0x40181824), // Translucent card variant
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceTint = Color.Transparent, // Wipe out elevation color overlays completely
    outline = Color(0x26FFFFFF),
    error = Color(0xFFEF4444)
)

@Composable
fun AnimatedDarkBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dark_bg_transition")
    
    val xOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x_offset"
    )
    
    val yOffset by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y_offset"
    )

    val angleOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle_offset"
    )

    // Deep, luxurious dark palette moving continuously:
    // Midnight Obsidian (#090B10), Deep Indigo Navy (#0F172A), Rich Cosmic Violet (#1E1B4B), Dark Slate Charcoal (#111827), Soft Purple (#2E1065)
    val midnightObsidian = Color(0xFF090B10)
    val deepNavy = Color(0xFF0F172A)
    val cosmicViolet = Color(0xFF1E1B4B)
    val darkSlateCharcoal = Color(0xFF111827)
    val softPurple = Color(0xFF2E1065)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        midnightObsidian,
                        deepNavy,
                        cosmicViolet,
                        softPurple,
                        darkSlateCharcoal,
                        midnightObsidian
                    ),
                    start = Offset(xOffset, yOffset),
                    end = Offset(xOffset + 1400f, yOffset + 1400f)
                )
            )
    ) {
        content()
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAbsoluteTonalElevation provides 0.dp
    ) {
        MaterialTheme(
            colorScheme = GlassmorphicDarkColorScheme,
            typography = Typography
        ) {
            AnimatedDarkBackground {
                content()
            }
        }
    }
}
