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
    /** M-28 (Kis 2): the edge that is a control's only mark (outline button, field, off switch); [line] is for dividers. */
    val lineStrong: Color,
    /** Brand (+ button, glow, focus, message action). Never a workplace colour. */
    val accent: Color, val accentInk: Color, val barAction: Color,
    /** Text on a filled danger or paid surface. */
    val onDanger: Color, val onPaid: Color,
) {
    /** Text/icon colour on an ink (primary action) surface. */
    val onInk get() = bg
    /** Filled primary actions: ink in dark; in light a softer slate, so a filled block does not shout on white (FB23-F2). */
    val inkFill get() = if (dark) ink else Color(0xFF232A3A)
    /** A switch that is on is ink, not the money green; its thumb is the page colour. */
    val controlOn get() = inkFill
    val controlKnob get() = if (dark) bg else surface
}

// FB23-F2 (2.3.0): light chrome is calmer still: the chosen tab is a soft brand tint (not a navy pill), filled
// actions and switches use a slate a step lighter than the text ink, and sunk wells are paler. Text ink is unchanged.
// Light (2.0.8): ink is a deep navy, not black, and the page a softer grey-blue, so text keeps its contrast (about 15:1 on
// white) without the hard black-on-white look; chosen days, tabs and currencies are white with an ink edge, not black tiles.
// 2.3.0 (owner's choice "אפור עשן", same as web): no pure-white glare. A mid-grey page with off-white cards, deeper ink and
// muted so text keeps its contrast, paid/pending/danger a step darker so every text pair still passes 4.5:1.
val KisLight = KisColors(
    dark = false,
    bg = Color(0xFFD9DCE2), surface = Color(0xFFEDEFF2), sunk = Color(0xFFCDD1D8), ink = Color(0xFF141925), muted = Color(0xFF4C5466), line = Color(0xFFC3C8D1),
    pending = Color(0xFF8A4900), pendingSoft = Color(0xFFF3DFB6), paid = Color(0xFF00664A), danger = Color(0xFFB02A24),
    // Light floating surfaces are frosted glass (translucent off-white, hairline edge, soft shadow), not black slabs.
    edit = Color(0xFF141925), editInk = Color.White, nav = Color(0xF0EDEFF2), navInk = Color(0xFF3F475A), navOn = Color(0xFFD7D4F7), navOnInk = Color(0xFF2A2480),
    bar = Color(0xF7EDEFF2), onBar = Color(0xFF141925),
    lineStrong = Color(0xFF6C7384), accent = Color(0xFF4A3ED6), accentInk = Color.White, barAction = Color(0xFF4A3ED6),
    onDanger = Color.White, onPaid = Color.White,
)

val KisDark = KisColors(
    dark = true,
    // 2.3.0 (approved choice B): a very dark blue-black instead of neutral grey; text contrast kept (muted on surface > 9:1).
    bg = Color(0xFF0F1216), surface = Color(0xFF181B20), sunk = Color(0xFF282C34), ink = Color(0xFFEDF0F6), muted = Color(0xFFB3B9C5), line = Color(0xFF32373F),
    pending = Color(0xFFF6B75C), pendingSoft = Color(0xFF3E2B0F), paid = Color(0xFF45D09E), danger = Color(0xFFFF9183),
    edit = Color(0xFF282C34), editInk = Color(0xFFEDF0F6), nav = Color(0xEB21252B), navInk = Color(0xFFB3B9C5), navOn = Color(0xFFEDF0F6), navOnInk = Color(0xFF0F1216),
    bar = Color(0xFF2D323A), onBar = Color(0xFFEDF0F6),
    lineStrong = Color(0xFF717681), accent = Color(0xFF9398FF), accentInk = Color(0xFF15153A), barAction = Color(0xFFB0B7FF),
    onDanger = Color(0xFF2B0E0B), onPaid = Color(0xFF011C11),
)

/** The 8 fixed workplace colours. Blocks run from [from] (amount side) to the darker [to] (name side). */
@Immutable
data class Workplace(val name: String, val from: Color, val to: Color, val on: Color, val accentDark: Color, val accentDarkInk: Color) {
    /** Left (amount side in RTL) is the vivid end; the right (name side) is darker so text stays readable. */
    val brush get() = Brush.horizontalGradient(listOf(from, to))
    fun accent(dark: Boolean) = if (dark) accentDark else from
    fun accentInk(dark: Boolean) = if (dark) accentDarkInk else on
}

// M-28 (Kis 2): same order and names, so a saved colour index stays valid; each colour moves inside its own hue so the
// close pairs (indigo/purple, rose/burgundy) stay apart for colour-blind people, and indigo is no longer the brand colour.
val Workplaces = listOf(
    Workplace("אינדיגו", Color(0xFF393ECA), Color(0xFF150A6B), Color.White, Color(0xFF7D9BFF), Color(0xFF0E142A)),
    Workplace("ירוק", Color(0xFF117D15), Color(0xFF013E03), Color.White, Color(0xFF44E994), Color(0xFF021C0D)),
    Workplace("ורד", Color(0xFFC50E70), Color(0xFF690039), Color.White, Color(0xFFFA74A5), Color(0xFF260C16)),
    Workplace("חרדל", Color(0xFFF5B82E), Color(0xFFD18F1F), Color(0xFF1A1200), Color(0xFFFFC732), Color(0xFF1E1400)),
    Workplace("סגול", Color(0xFF9F4AE4), Color(0xFF5B2187), Color.White, Color(0xFFC874C8), Color(0xFF200E21)),
    Workplace("תכלת", Color(0xFF0777A9), Color(0xFF003D59), Color.White, Color(0xFF8BC7FE), Color(0xFF031729)),
    Workplace("טורקיז", Color(0xFF065E63), Color(0xFF012527), Color.White, Color(0xFF18AFA4), Color(0xFF001B19)),
    Workplace("בורדו", Color(0xFF8B032A), Color(0xFF33000A), Color.White, Color(0xFFF05963), Color(0xFF270C0D)),
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
    /** Profile: the user's own name, shown only inside the app (greeting, own line in a group report). Device-only. */
    var name by mutableStateOf(prefs?.getString("name", "") ?: "")
        private set
    fun updateName(value: String) { name = value.take(40); prefs?.edit()?.putString("name", name.trim())?.apply() }
    /** Kept with the profile only; greetings and messages use the first name. */
    var lastName by mutableStateOf(prefs?.getString("lastName", "") ?: "")
        private set
    fun updateLastName(value: String) { lastName = value.take(40); prefs?.edit()?.putString("lastName", lastName.trim())?.apply() }

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
        if (old in placeOrder) updatePlaceOrder(placeOrder.map { if (it == old) new else it })
        colorVersion++
    }
    /** The order the user gave the places (settings → places → עריכה). Device-only, like the colours; new places come last. */
    var placeOrder by mutableStateOf(runCatching { org.json.JSONArray(prefs?.getString("placeOrder", "[]") ?: "[]").let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList()))
        private set
    fun updatePlaceOrder(names: List<String>) { placeOrder = names; prefs?.edit()?.putString("placeOrder", org.json.JSONArray(names).toString())?.apply() }
    fun <T> ordered(places: List<T>, name: (T) -> String): List<T> = orderPlaces(places, placeOrder, name)

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
        tertiary = c.accent, background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.muted, surfaceTint = Color.Transparent, outline = c.line, outlineVariant = c.line,
        error = c.danger, onError = c.onDanger, inverseSurface = c.ink, inverseOnSurface = c.bg,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface, surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        surfaceBright = c.sunk, surfaceDim = c.bg, scrim = Color.Black,
    ) else lightColorScheme(
        primary = c.ink, onPrimary = c.bg, primaryContainer = c.sunk, onPrimaryContainer = c.ink,
        secondary = c.muted, onSecondary = c.bg, secondaryContainer = c.sunk, onSecondaryContainer = c.ink,
        tertiary = c.accent, background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.muted, surfaceTint = Color.Transparent, outline = c.line, outlineVariant = c.line,
        error = c.danger, onError = c.onDanger, inverseSurface = c.ink, inverseOnSurface = c.bg,
        surfaceContainerLowest = Color.White, surfaceContainerLow = c.surface, surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        surfaceBright = c.surface, surfaceDim = c.sunk, scrim = Color.Black,
    )
    CompositionLocalProvider(LocalKis provides c, LocalKisSettings provides settings) {
        MaterialTheme(colorScheme = scheme, typography = KisMaterialType, content = content)
    }
}

/** [places] in the user's [order]; places not in it keep their own order after the ordered ones. */
fun <T> orderPlaces(places: List<T>, order: List<String>, name: (T) -> String): List<T> {
    val at = order.withIndex().associate { it.value to it.index }
    return places.withIndex().sortedWith(compareBy({ at[name(it.value)] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
}
