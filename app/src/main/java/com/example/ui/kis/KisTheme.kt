package com.example.ui.kis

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.WorkCategory

/*
 * "כיס" design system (approved 2026-10-16). Source of truth: design/v2-design-system/tokens.json
 * on the planning branch. Colour belongs to workplaces; actions are ink.
 */

@Immutable
data class KisColors(
    val dark: Boolean,
    val bg: Color, val surface: Color, val sunk: Color, val ink: Color, val muted: Color, val line: Color,
    val pending: Color, val pendingSoft: Color, val paid: Color, val danger: Color,
    val edit: Color, val editInk: Color, val nav: Color, val navInk: Color, val navOn: Color, val navOnInk: Color,
    /** Floating bars and messages: dark in both themes (never a white slab in dark mode). */
    val bar: Color, val onBar: Color,
) {
    /** Text/icon colour on an ink (primary action) surface. */
    val onInk get() = bg
}

val KisLight = KisColors(
    dark = false,
    bg = Color(0xFFEDF0F5), surface = Color.White, sunk = Color(0xFFE2E6EE), ink = Color(0xFF0D1015), muted = Color(0xFF5D6573), line = Color(0xFFC6CDD8),
    pending = Color(0xFF9A5000), pendingSoft = Color(0xFFFFEBC7), paid = Color(0xFF0B7F60), danger = Color(0xFFC2362B),
    // Light floating surfaces are frosted glass (translucent white, hairline edge, soft shadow), not black slabs.
    edit = Color(0xFF0D1015), editInk = Color.White, nav = Color(0xE6FFFFFF), navInk = Color(0xFF3B4250), navOn = Color(0xFF0D1015), navOnInk = Color.White,
    bar = Color(0xF2FFFFFF), onBar = Color(0xFF0D1015),
)

val KisDark = KisColors(
    dark = true,
    bg = Color(0xFF121212), surface = Color(0xFF1E1E1E), sunk = Color(0xFF2A2A2A), ink = Color.White, muted = Color(0xFFB3B3B3), line = Color(0xFF303030),
    pending = Color(0xFFFFC266), pendingSoft = Color(0xFF3A2D12), paid = Color(0xFF34C79A), danger = Color(0xFFFF8A7A),
    edit = Color(0xFF2A2A2A), editInk = Color(0xFFF1F3F7), nav = Color(0xFF181818), navInk = Color(0xFFB3B3B3), navOn = Color.White, navOnInk = Color(0xFF121212),
    bar = Color(0xFF2C2E33), onBar = Color(0xFFF1F3F7),
)

/** The 8 fixed workplace colours. Blocks run from [from] (amount side) to the darker [to] (name side). */
@Immutable
data class Workplace(val name: String, val from: Color, val to: Color, val on: Color, val accentDark: Color, val accentDarkInk: Color) {
    /** Left (amount side in RTL) is the vivid end; the right (name side) is darker so text stays readable. */
    val brush get() = Brush.horizontalGradient(listOf(from, to))
    fun accent(dark: Boolean) = if (dark) accentDark else from
    fun accentInk(dark: Boolean) = if (dark) accentDarkInk else on
}

val Workplaces = listOf(
    Workplace("אינדיגו", Color(0xFF5145E8), Color(0xFF1E1873), Color.White, Color(0xFF8F87FF), Color(0xFF120F3A)),
    Workplace("ירוק", Color(0xFF0E8059), Color(0xFF073D2C), Color.White, Color(0xFF3DDC97), Color(0xFF03140E)),
    Workplace("ורד", Color(0xFFD6336C), Color(0xFF5C1230), Color.White, Color(0xFFFF6B9A), Color(0xFF2E0A18)),
    Workplace("חרדל", Color(0xFFF5B82E), Color(0xFFC98A10), Color(0xFF1A1200), Color(0xFFFFC94D), Color(0xFF2A1C00)),
    Workplace("סגול", Color(0xFF7D3CF0), Color(0xFF2E1466), Color.White, Color(0xFFB48CFF), Color(0xFF1E0B3D)),
    Workplace("תכלת", Color(0xFF1F72E0), Color(0xFF0C2F66), Color.White, Color(0xFF5AA9FF), Color(0xFF06203F)),
    Workplace("טורקיז", Color(0xFF0E8A86), Color(0xFF063B3A), Color.White, Color(0xFF3CD3CB), Color(0xFF04201F)),
    Workplace("בורדו", Color(0xFFB5294E), Color(0xFF4A0F21), Color.White, Color(0xFFFF6F8E), Color(0xFF2E0812)),
)

// ---- Type ----

val Rubik = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_semibold, FontWeight.SemiBold),
    Font(R.font.rubik_bold, FontWeight.Bold),
)
/** Display face for headings and amounts. Never for ₪/$: it draws ₪ as letters. */
val SecularOne = FontFamily(Font(R.font.secular_one, FontWeight.Normal))

object KisType {
    val hero = TextStyle(fontFamily = SecularOne, fontSize = 60.sp, lineHeight = 62.sp, letterSpacing = (-0.5).sp)
    val amount = TextStyle(fontFamily = SecularOne, fontSize = 26.sp, lineHeight = 29.sp)
    val title = TextStyle(fontFamily = SecularOne, fontSize = 22.sp, lineHeight = 28.sp)
    val page = TextStyle(fontFamily = SecularOne, fontSize = 30.sp, lineHeight = 36.sp)
    val body = TextStyle(fontFamily = Rubik, fontSize = 16.sp, lineHeight = 23.sp)
    val label = TextStyle(fontFamily = Rubik, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
    val caption = TextStyle(fontFamily = Rubik, fontSize = 13.sp, lineHeight = 17.sp)
    val micro = TextStyle(fontFamily = Rubik, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
}

private val KisMaterialType = Typography().let { base ->
    fun TextStyle.rubik() = copy(fontFamily = Rubik, letterSpacing = 0.sp)
    Typography(
        displayLarge = base.displayLarge.rubik(), displayMedium = base.displayMedium.rubik(), displaySmall = base.displaySmall.rubik(),
        headlineLarge = base.headlineLarge.rubik(), headlineMedium = base.headlineMedium.rubik(), headlineSmall = base.headlineSmall.rubik(),
        titleLarge = base.titleLarge.rubik(), titleMedium = base.titleMedium.rubik(), titleSmall = base.titleSmall.rubik(),
        bodyLarge = base.bodyLarge.rubik(), bodyMedium = base.bodyMedium.rubik(), bodySmall = base.bodySmall.rubik(),
        labelLarge = base.labelLarge.rubik(), labelMedium = base.labelMedium.rubik(), labelSmall = base.labelSmall.rubik(),
    )
}

// ---- Device-local appearance settings (not part of payroll backups) ----

enum class ThemeChoice(val key: String, val label: String) { SYSTEM("system", "אוטומטי"), LIGHT("light", "בהיר"), DARK("dark", "כהה") }

@Stable
class KisSettings internal constructor(private val context: Context?) {
    private val prefs = context?.getSharedPreferences("kis_ui", Context.MODE_PRIVATE)
    var theme by mutableStateOf(ThemeChoice.entries.firstOrNull { it.key == prefs?.getString("theme", null) } ?: ThemeChoice.SYSTEM)
        private set
    var haptics by mutableStateOf(prefs?.getBoolean("haptics", true) ?: true)
        private set
    fun updateTheme(choice: ThemeChoice) { theme = choice; prefs?.edit()?.putString("theme", choice.key)?.apply() }
    fun updateHaptics(on: Boolean) { haptics = on; prefs?.edit()?.putBoolean("haptics", on)?.apply() }

    /** Optional per-workplace colour picked by the user; otherwise colours follow category order. */
    fun workplaceIndex(category: String, categories: List<WorkCategory>): Int {
        prefs?.getInt("color:$category", -1)?.takeIf { it in Workplaces.indices }?.let { return it }
        val i = categories.sortedBy { it.id }.indexOfFirst { it.name == category }
        return if (i >= 0) i % Workplaces.size else (category.hashCode() and 0x7fffffff) % Workplaces.size
    }
    var colorVersion by mutableStateOf(0)
        private set
    fun setWorkplaceColor(category: String, index: Int) { prefs?.edit()?.putInt("color:$category", index)?.apply(); colorVersion++ }
    fun renameWorkplaceColor(old: String, new: String) {
        val p = prefs ?: return
        val i = p.getInt("color:$old", -1)
        if (i >= 0) p.edit().remove("color:$old").putInt("color:$new", i).apply()
        colorVersion++
    }

    // Haptics. The patterns follow the design system table; the switch turns all of them off.
    enum class Haptic { TAP, TICK, SELECT, SUCCESS, WARN }
    fun haptic(kind: Haptic) {
        if (!haptics || context == null) return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val effect = when (kind) {
                    Haptic.TAP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    Haptic.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    Haptic.SELECT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    Haptic.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 12, 60, 22), -1)
                    Haptic.WARN -> VibrationEffect.createWaveform(longArrayOf(0, 35, 50, 35), -1)
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= 26) {
                val effect = when (kind) {
                    Haptic.TAP -> VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE)
                    Haptic.TICK -> VibrationEffect.createOneShot(4, VibrationEffect.DEFAULT_AMPLITUDE)
                    Haptic.SELECT -> VibrationEffect.createOneShot(16, VibrationEffect.DEFAULT_AMPLITUDE)
                    Haptic.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 12, 60, 22), -1)
                    Haptic.WARN -> VibrationEffect.createWaveform(longArrayOf(0, 35, 50, 35), -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (kind) {
                    Haptic.SUCCESS -> vibrator.vibrate(longArrayOf(0, 12, 60, 22), -1)
                    Haptic.WARN -> vibrator.vibrate(longArrayOf(0, 35, 50, 35), -1)
                    else -> vibrator.vibrate(if (kind == Haptic.TICK) 4L else 8L)
                }
            }
        } catch (_: Exception) { /* Haptics are optional; never break an action. */ }
    }

    companion object {
        /** For previews/tests without a Context. */
        val Inert = KisSettings(null)
    }
}

val LocalKis = staticCompositionLocalOf { KisLight }
val LocalKisSettings = staticCompositionLocalOf { KisSettings.Inert }

object Kis {
    val colors: KisColors @Composable get() = LocalKis.current
    val settings: KisSettings @Composable get() = LocalKisSettings.current
}

/** The workplace colour of a category; recomposes when the user picks a different colour. */
@Composable
fun workplaceOf(category: String, categories: List<WorkCategory>): Workplace {
    val settings = LocalKisSettings.current
    @Suppress("UNUSED_VARIABLE") val version = settings.colorVersion
    return Workplaces[settings.workplaceIndex(category, categories)]
}

@Composable
fun rememberKisSettings(): KisSettings {
    val context = LocalContext.current.applicationContext
    return remember { KisSettings(context) }
}

/** Resolves light/dark from the user's choice and provides tokens to both Kis and Material components. */
@Composable
fun KisTheme(settings: KisSettings = rememberKisSettings(), darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: when (settings.theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val c = if (dark) KisDark else KisLight
    val scheme = if (dark) darkColorScheme(
        primary = c.ink, onPrimary = c.bg, primaryContainer = c.sunk, onPrimaryContainer = c.ink,
        secondary = c.muted, onSecondary = c.bg, secondaryContainer = c.sunk, onSecondaryContainer = c.ink,
        tertiary = Workplaces[0].accentDark, background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.muted, surfaceTint = Color.Transparent, outline = c.line, outlineVariant = c.line,
        error = c.danger, onError = c.bg, inverseSurface = c.ink, inverseOnSurface = c.bg,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface, surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        surfaceBright = c.sunk, surfaceDim = c.bg, scrim = Color.Black,
    ) else lightColorScheme(
        primary = c.ink, onPrimary = c.bg, primaryContainer = c.sunk, onPrimaryContainer = c.ink,
        secondary = c.muted, onSecondary = c.bg, secondaryContainer = c.sunk, onSecondaryContainer = c.ink,
        tertiary = Workplaces[0].from, background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.muted, surfaceTint = Color.Transparent, outline = c.line, outlineVariant = c.line,
        error = c.danger, onError = Color.White, inverseSurface = c.ink, inverseOnSurface = c.bg,
        surfaceContainerLowest = Color.White, surfaceContainerLow = c.surface, surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        surfaceBright = c.surface, surfaceDim = c.sunk, scrim = Color.Black,
    )
    CompositionLocalProvider(LocalKis provides c, LocalKisSettings provides settings) {
        MaterialTheme(colorScheme = scheme, typography = KisMaterialType, content = content)
    }
}
