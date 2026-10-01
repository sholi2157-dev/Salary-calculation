package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val HeeboFontFamily = FontFamily(androidx.compose.ui.text.font.Font(R.font.heebo))
val RubikFontFamily = HeeboFontFamily
val AssistantFontFamily = HeeboFontFamily

// Assign every Material text role to bundled Hebrew type, including metadata and dialogs.
private fun ledgerText(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = HeeboFontFamily, fontWeight = weight, fontSize = size.sp,
    lineHeight = line.sp, letterSpacing = 0.sp
)
val Typography = Typography(
    displayLarge = ledgerText(57, 64),
    displayMedium = ledgerText(45, 52),
    displaySmall = ledgerText(36, 44),
    headlineSmall = ledgerText(24, 32, FontWeight.SemiBold),
    titleSmall = ledgerText(14, 20, FontWeight.Medium),
    bodySmall = ledgerText(12, 18),
    labelSmall = ledgerText(11, 16, FontWeight.Medium),
    headlineLarge = TextStyle(
        fontFamily = RubikFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = RubikFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = RubikFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = RubikFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AssistantFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AssistantFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AssistantFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AssistantFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)
