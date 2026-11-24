package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.markdown.MarkdownText
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.domain.EvaluateAssertionReasonUseCase

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssertionReasonSlideCard(
    slide: SlideUiModel.AssertionReasonSlide,
    runtimeState: AssertionReasonSlideRuntimeState,
    onSelectCode: (String) -> Unit,
    onCheckAnswer: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val layout = slide.layout
    val payload = slide.question.payload
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val effectivePaddingDp = if (isCompact) layout.cardPaddingDp.coerceAtMost(24) else layout.cardPaddingDp
    val options = remember(payload.scheme) { EvaluateAssertionReasonUseCase.optionsForScheme(payload.scheme) }
    val normalizedCorrect = remember(payload.answer) { payload.answer.trim().lowercase() }

    FramedSlideCard(
        tabLabel = "ASSERTION — REASON",
        modifier = modifier,
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = effectivePaddingDp,
        contentSpacingDp = 16
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatementBannerCard(
                badge = "ASSERTION (A)",
                content = payload.assertion,
                accentColor = palette.accent,
                bgColor = palette.accentSoft,
                bodyFontSizeSp = layout.bodyFontSizeSp
            )
            StatementBannerCard(
                badge = "REASON (R)",
                content = payload.reason,
                accentColor = palette.clinicalAccent,
                bgColor = palette.clinicalBg,
                bodyFontSizeSp = layout.bodyFontSizeSp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(layout.optionGapDp.dp)) {
            options.forEachIndexed { idx, opt ->
                val isSelected = runtimeState.selectedCode.equals(opt.code, ignoreCase = true)
                val isCorrectOption = opt.code.equals(normalizedCorrect, ignoreCase = true)
                val style = when {
                    runtimeState.isChecked && isCorrectOption ->
                        AssertionOptionVisualStyle(palette.ok, palette.ok.copy(alpha = 0.12f), palette.ok, Color.White)
                    runtimeState.isChecked && isSelected && !isCorrectOption ->
                        AssertionOptionVisualStyle(palette.bad, palette.bad.copy(alpha = 0.12f), palette.bad, Color.White)
                    isSelected ->
                        AssertionOptionVisualStyle(palette.accent, palette.accentSoft, palette.accent, Color.White)
                    else ->
                        AssertionOptionVisualStyle(palette.border, Color.Transparent, palette.background, palette.muted)
                }
                val borderColor = style.borderColor
                val bgColor = style.bgColor
                val badgeBg = style.badgeBg
                val badgeText = style.badgeText
                val letter = ('A' + idx).toString()

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectCode(opt.code) },
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor,
                    border = BorderStroke(2.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(badgeBg)
                                .border(1.dp, borderColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                color = badgeText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = opt.label,
                            color = palette.text,
                            fontSize = layout.bodyFontSizeSp.sp,
                            lineHeight = (layout.bodyFontSizeSp * 1.38f).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCheckAnswer,
                enabled = !runtimeState.selectedCode.isNullOrBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Check Answer", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            if (runtimeState.isChecked) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Text("Retry", color = palette.text, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (runtimeState.isChecked) {
            Text(
                text = if (runtimeState.isCorrect == true) "Correct!" else "Incorrect — review the highlighted choice.",
                color = if (runtimeState.isCorrect == true) palette.ok else palette.bad,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (runtimeState.isChecked && !payload.explanation.isNullOrBlank()) {
            QuestionExplanationBox(
                explanation = payload.explanation.orEmpty(),
                layout = layout
            )
        }
    }
}

@Composable
private fun StatementBannerCard(
    badge: String,
    content: String,
    accentColor: Color,
    bgColor: Color,
    bodyFontSizeSp: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = badge,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )
            MarkdownText(
                markdown = content,
                baseFontSizeSp = bodyFontSizeSp
            )
        }
    }
}

private data class AssertionOptionVisualStyle(
    val borderColor: Color,
    val bgColor: Color,
    val badgeBg: Color,
    val badgeText: Color
)
