package com.meloberry.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    FramedSlideCard(
        tabLabel = "ARTICLE",
        modifier = modifier,
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = layout.cardPaddingDp,
        contentSpacingDp = layout.blockSpacingDp
    ) {
        slide.blocks.forEach { block ->
            if (block.kind == ArticleBlockKind.TEXT && !block.textContent.isNullOrBlank()) {
                MarkdownText(
                    markdown = block.textContent.orEmpty(),
                    baseFontSizeSp = layout.bodyFontSizeSp,
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
