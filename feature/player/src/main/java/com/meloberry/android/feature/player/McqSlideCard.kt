package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.McqOptionModel
import com.meloberry.android.core.model.ResolvedSlideLayout
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.components.MeloberryAssetImage
import com.meloberry.android.core.ui.markdown.MarkdownText
import com.meloberry.android.core.ui.theme.LocalMeloberryColors

@Composable
internal fun McqSlideCard(
    slide: SlideUiModel.McqSlide,
    runtimeState: McqSlideRuntimeState,
    onToggleOption: (Int) -> Unit,
    onCheckAnswer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val layout = slide.layout
    val mcq = slide.question
    val gap = layout.optionGapDp.dp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isCompact = screenWidthDp < 600
    val effectivePaddingDp = if (isCompact) layout.cardPaddingDp.coerceAtMost(24) else layout.cardPaddingDp
    val columns = if (screenWidthDp < 440) 1 else layout.optionColumns.coerceIn(1, 2)

    FramedSlideCard(
        tabLabel = if (mcq.isMultiCorrect) "MCQ (MULTI-CORRECT)" else "MCQ",
        modifier = modifier,
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = effectivePaddingDp,
        contentSpacingDp = 16
    ) {
        MarkdownText(
            markdown = mcq.promptText,
            baseFontSizeSp = layout.promptFontSizeSp,
            contentAlign = layout.contentAlign,
            isBoldByDefault = true
        )

        if (!mcq.promptImageFilePath.isNullOrBlank()) {
            MeloberryAssetImage(
                filePath = mcq.promptImageFilePath.orEmpty(),
                contentDescription = "Question prompt diagram",
                maxHeightDp = layout.imageMaxHeightDp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            mcq.options.chunked(columns).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    rowOptions.forEach { opt ->
                        OptionCard(
                            option = opt,
                            layout = layout,
                            isSelected = opt.index in runtimeState.selectedIndices,
                            isChecked = runtimeState.isChecked,
                            onClick = { onToggleOption(opt.index) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (columns == 2 && rowOptions.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCheckAnswer,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Check Answer", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            if (runtimeState.isChecked) {
                Text(
                    text = if (runtimeState.isCorrect == true) "Correct!" else "Review highlighted options.",
                    color = if (runtimeState.isCorrect == true) palette.ok else palette.bad,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (runtimeState.isChecked && !mcq.explanation.isNullOrBlank()) {
            val expShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(expShape)
                    .background(palette.background)
                    .border(1.dp, palette.border, expShape)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(palette.accent)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "EXPLANATION",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    )
                    MarkdownText(
                        markdown = mcq.explanation.orEmpty(),
                        baseFontSizeSp = (layout.bodyFontSizeSp - 1).coerceAtLeast(12),
                        contentAlign = layout.contentAlign
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    option: McqOptionModel,
    layout: ResolvedSlideLayout,
    isSelected: Boolean,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val (borderColor, bgColor) = when {
        isChecked && option.isCorrect -> palette.ok to palette.ok.copy(alpha = 0.12f)
        isChecked && isSelected && !option.isCorrect -> palette.bad to palette.bad.copy(alpha = 0.12f)
        isSelected -> palette.accent to palette.accentSoft
        else -> palette.border to Color.Transparent
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!option.imageFilePath.isNullOrBlank()) {
                MeloberryAssetImage(
                    filePath = option.imageFilePath.orEmpty(),
                    contentDescription = "Option ${option.index}",
                    maxHeightDp = layout.imageMaxHeightDp
                )
            }
            if (!option.text.isNullOrBlank()) {
                MarkdownText(
                    markdown = option.text.orEmpty(),
                    baseFontSizeSp = layout.bodyFontSizeSp,
                    contentAlign = layout.contentAlign
                )
            }
        }
    }
}
