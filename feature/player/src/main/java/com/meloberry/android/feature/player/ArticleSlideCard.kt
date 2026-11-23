package com.meloberry.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.components.MeloberryAssetImage
import com.meloberry.android.core.ui.markdown.MarkdownText

@Composable
internal fun ArticleSlideCard(
    slide: SlideUiModel.ArticleSlide,
    modifier: Modifier = Modifier
) {
    val layout = slide.layout
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val effectivePaddingDp = if (isCompact) layout.cardPaddingDp.coerceAtMost(24) else layout.cardPaddingDp
    val tabLabel = slide.title?.takeIf { it.isNotBlank() }?.let { "ARTICLE • $it" } ?: "ARTICLE"

    FramedSlideCard(
        tabLabel = tabLabel,
        modifier = modifier,
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = effectivePaddingDp,
        contentSpacingDp = layout.blockSpacingDp
    ) {
        slide.blocks.forEach { block ->
            if (block.kind == ArticleBlockKind.TEXT && !block.textContent.isNullOrBlank()) {
                MarkdownText(
                    markdown = block.textContent.orEmpty(),
                    baseFontSizeSp = layout.bodyFontSizeSp,
                    promptFontSizeSp = layout.promptFontSizeSp,
                    contentAlign = layout.contentAlign
                )
            } else if (!block.imageFilePath.isNullOrBlank()) {
                MeloberryAssetImage(
                    filePath = block.imageFilePath.orEmpty(),
                    contentDescription = "Article diagram",
                    maxHeightDp = layout.imageMaxHeightDp
                )
            }
        }
    }
}
