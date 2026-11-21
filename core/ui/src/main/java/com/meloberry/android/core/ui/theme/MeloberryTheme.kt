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
    val isDark: Boolean = false
)

val BerryPalette = MeloberryColors(
    background = Color(0xFFFDF6F9),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1F1722),
    muted = Color(0xFF6C5B70),
    accent = Color(0xFFB8295B),
    accentSoft = Color(0xFFFCE8EF),
    border = Color(0xFFECD7E0)
)

val LightPalette = MeloberryColors(
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF0F172A),
    muted = Color(0xFF475569),
    accent = Color(0xFF2563EB),
    accentSoft = Color(0xFFDBEAFE),
    border = Color(0xFFE2E8F0)
)

val DarkPalette = MeloberryColors(
    background = Color(0xFF0F1117),
    surface = Color(0xFF181B24),
    text = Color(0xFFF1F5F9),
    muted = Color(0xFF94A3B8),
    accent = Color(0xFFF43F5E),
    accentSoft = Color(0xFF311520),
    border = Color(0xFF2A2F3D),
    isDark = true
)

val PaperPalette = MeloberryColors(
    background = Color(0xFFF7F3E9),
    surface = Color(0xFFFFFDF8),
    text = Color(0xFF2C241B),
    muted = Color(0xFF6F6254),
    accent = Color(0xFF9A3412),
    accentSoft = Color(0xFFFFEDD5),
    border = Color(0xFFE7DCC8)
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
