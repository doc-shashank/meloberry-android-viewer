package com.meloberry.android.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.meloberry.android.core.model.ThemeMode

@Immutable
data class MeloberryColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val border: Color,
    val ok: Color = Color(0xFF198754),
    val bad: Color = Color(0xFFDC3545),
    val pearlAccent: Color = Color(0xFF7C3AED),
    val pearlBg: Color = Color(0xFFF3EEFF),
    val clinicalAccent: Color = Color(0xFF0E7490),
    val clinicalBg: Color = Color(0xFFECFEFF),
    val warningAccent: Color = Color(0xFFB45309),
    val warningBg: Color = Color(0xFFFFFBEB),
    val fuzzyAccent: Color = Color(0xFFD97706),
    val fuzzyBg: Color = Color(0xFFFEF3C7),
    val isDark: Boolean = false
)

val BerryPalette = MeloberryColors(
    background = Color(0xFFFDF6F9),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1F1722),
    muted = Color(0xFF6C5B70),
    accent = Color(0xFFB8295B),
    accentSoft = Color(0xFFFCE8EF),
    border = Color(0xFFECD7E0),
    pearlAccent = Color(0xFF8B2F7D),
    pearlBg = Color(0xFFFBF0FA),
    clinicalAccent = Color(0xFF0E7490),
    clinicalBg = Color(0xFFECFEFF),
    warningAccent = Color(0xFFB45309),
    warningBg = Color(0xFFFFF7ED),
    fuzzyAccent = Color(0xFFB45309),
    fuzzyBg = Color(0xFFFEF3C7)
)

val LightPalette = MeloberryColors(
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF0F172A),
    muted = Color(0xFF475569),
    accent = Color(0xFF2563EB),
    accentSoft = Color(0xFFDBEAFE),
    border = Color(0xFFE2E8F0),
    pearlAccent = Color(0xFF6D28D9),
    pearlBg = Color(0xFFF5F3FF),
    clinicalAccent = Color(0xFF0284C7),
    clinicalBg = Color(0xFFF0F9FF),
    warningAccent = Color(0xFFD97706),
    warningBg = Color(0xFFFFFBEB),
    fuzzyAccent = Color(0xFFB45309),
    fuzzyBg = Color(0xFFFEF3C7)
)

val DarkPalette = MeloberryColors(
    background = Color(0xFF0F1117),
    surface = Color(0xFF181B24),
    text = Color(0xFFF1F5F9),
    muted = Color(0xFF94A3B8),
    accent = Color(0xFFF43F5E),
    accentSoft = Color(0xFF311520),
    border = Color(0xFF2A2F3D),
    ok = Color(0xFF34D399),
    bad = Color(0xFFF87171),
    pearlAccent = Color(0xFFC084FC),
    pearlBg = Color(0xFF231934),
    clinicalAccent = Color(0xFF38BDF8),
    clinicalBg = Color(0xFF112433),
    warningAccent = Color(0xFFFBBF24),
    warningBg = Color(0xFF2C2210),
    fuzzyAccent = Color(0xFFFBBF24),
    fuzzyBg = Color(0xFF2D2310),
    isDark = true
)

val PaperPalette = MeloberryColors(
    background = Color(0xFFF7F3E9),
    surface = Color(0xFFFFFDF8),
    text = Color(0xFF2C241B),
    muted = Color(0xFF6F6254),
    accent = Color(0xFF9A3412),
    accentSoft = Color(0xFFFFEDD5),
    border = Color(0xFFE7DCC8),
    pearlAccent = Color(0xFF7E22CE),
    pearlBg = Color(0xFFF8F1FA),
    clinicalAccent = Color(0xFF0F766E),
    clinicalBg = Color(0xFFEEF7F5),
    warningAccent = Color(0xFFB45309),
    warningBg = Color(0xFFFEF3DF),
    fuzzyAccent = Color(0xFFB45309),
    fuzzyBg = Color(0xFFFEF0D1)
)

val LocalMeloberryColors = staticCompositionLocalOf { BerryPalette }

fun paletteFor(themeMode: ThemeMode): MeloberryColors = when (themeMode) {
    ThemeMode.BERRY -> BerryPalette
    ThemeMode.LIGHT -> LightPalette
    ThemeMode.DARK -> DarkPalette
    ThemeMode.PAPER -> PaperPalette
}

@Composable
fun MeloberryTheme(
    themeMode: ThemeMode = ThemeMode.BERRY,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themeMode)
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.text,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.background,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = palette.bad
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accent,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.accentSoft,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = palette.bad
        )
    }

    CompositionLocalProvider(LocalMeloberryColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
