package com.meloberry.android.domain

import com.meloberry.android.core.model.ContentAlign
import com.meloberry.android.core.model.LayoutTargetType
import com.meloberry.android.core.model.MediaPosition
import com.meloberry.android.core.model.ResolvedSlideLayout
import com.meloberry.android.core.model.ThemeMode

object LayoutBoundsCatalog {

    private fun intBounds(raw: String?, min: Int, max: Int, defaultValue: Int): Int =
        raw?.trim()?.toIntOrNull()?.coerceIn(min, max) ?: defaultValue

    fun parseThemeMode(raw: String?): ThemeMode = ThemeMode.fromRaw(raw)

    fun parseContentAlign(raw: String?): ContentAlign = ContentAlign.fromRaw(raw)

    fun parseMediaPosition(raw: String?): MediaPosition = MediaPosition.fromRaw(raw)

    fun parseSlideTransitionMs(raw: String?): Int = intBounds(raw, 100, 1200, 380)

    fun merge(
        globalLayouts: Map<LayoutTargetType, Map<String, String>>,
        targetType: LayoutTargetType,
        componentOverrides: Map<String, String> = emptyMap(),
        questionOverrides: Map<String, String> = emptyMap()
    ): ResolvedSlideLayout {
        val merged = LinkedHashMap<String, String>()
        globalLayouts[targetType]?.forEach { (k, v) -> merged[k.lowercase()] = v }
        componentOverrides.forEach { (k, v) -> merged[k.lowercase()] = v }
        questionOverrides.forEach { (k, v) -> merged[k.lowercase()] = v }

        return ResolvedSlideLayout(
            cardMaxWidthDp = intBounds(merged["cardmaxwidthpx"], 480, 1400, 840),
            cardPaddingDp = intBounds(merged["cardpaddingpx"], 12, 64, 32),
            promptFontSizeSp = intBounds(merged["promptfontsizepx"], 14, 42, 22),
            bodyFontSizeSp = intBounds(merged["bodyfontsizepx"], 12, 28, 16),
            imageMaxHeightDp = intBounds(merged["imagemaxheightpx"], 120, 800, 360),
            slideTransitionMs = parseSlideTransitionMs(merged["slidetransitionms"]),
            contentAlign = parseContentAlign(merged["contentalign"]),
            themeMode = parseThemeMode(merged["thememode"]),
            optionColumns = intBounds(merged["optioncolumns"], 1, 2, 1),
            optionGapDp = intBounds(merged["optiongappx"], 6, 32, 12),
            defaultTextPosition = parseMediaPosition(merged["defaulttextposition"]),
            flipDurationMs = intBounds(merged["flipdurationms"], 150, 1000, 450),
            blockSpacingDp = intBounds(merged["blockspacingpx"], 8, 48, 20)
        )
    }
}
