package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.meloberry.android.core.model.ContentAlign
import com.meloberry.android.core.model.ResolvedSlideLayout
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.markdown.MarkdownText
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.domain.EvaluateFillBlankUseCase
import com.meloberry.android.domain.RecallMatchKind

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FillBlankSlideCard(
    slide: SlideUiModel.FillBlankSlide,
    runtimeState: FillBlankSlideRuntimeState,
    onUpdateInput: (Int, String) -> Unit,
    onCheckAnswer: () -> Unit,
    onOverrideSlot: (Int) -> Unit,
    onOverrideCorrect: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val layout = slide.layout
    val payload = slide.question.payload
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val effectivePaddingDp = if (isCompact) layout.cardPaddingDp.coerceAtMost(24) else layout.cardPaddingDp

    val evaluator = remember { EvaluateFillBlankUseCase() }
    val evalResult = remember(payload, runtimeState.inputsBySlot, runtimeState.overriddenSlotIndices, runtimeState.overriddenCorrect, runtimeState.isChecked) {
        if (runtimeState.isChecked) {
            evaluator(
                payload = payload,
                userInputsBySlotIndex = runtimeState.inputsBySlot,
                overriddenSlotIndices = runtimeState.overriddenSlotIndices,
                overrideQuestionCorrect = runtimeState.overriddenCorrect && runtimeState.overriddenSlotIndices.isEmpty()
            )
        } else {
            null
        }
    }
    val renderedPrompt = remember(payload.template) {
        formatFillBlankTemplateWithBadges(payload.template)
    }

    FramedSlideCard(
        tabLabel = "FILL IN THE BLANK",
        modifier = modifier,
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = effectivePaddingDp,
        contentSpacingDp = 16
    ) {
        MarkdownText(
            markdown = renderedPrompt,
            baseFontSizeSp = layout.promptFontSizeSp,
            contentAlign = layout.contentAlign
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            payload.blanks.forEach { slot ->
                val slotEval = evalResult?.slotResults?.firstOrNull { it.slotIndex == slot.index }
                val inputValue = runtimeState.inputsBySlot[slot.index].orEmpty()
                val (borderColor, statusLabel, statusColor) = when {
                    slotEval == null -> Triple(palette.border, null, palette.muted)
                    slotEval.isOverridden -> Triple(palette.ok, "Overridden Correct", palette.ok)
                    slotEval.matchResult.kind == RecallMatchKind.EXACT -> Triple(palette.ok, "Exact", palette.ok)
                    slotEval.matchResult.kind == RecallMatchKind.FUZZY -> Triple(palette.fuzzyAccent, "Accepted (Fuzzy)", palette.fuzzyAccent)
                    else -> Triple(palette.bad, "Missed", palette.bad)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Blank #${slot.index}",
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (statusLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = statusColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { onUpdateInput(slot.index, it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = payload.placeholder?.takeIf { it.isNotBlank() } ?: "Type answer for blank #${slot.index}...",
                                color = palette.muted,
                                fontSize = 14.sp
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (slotEval == null) palette.accent else borderColor,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = palette.text,
                            unfocusedTextColor = palette.text,
                            cursorColor = palette.accent
                        )
                    )

                    if (slotEval != null && !slotEval.isCorrect) {
                        val expectedText = slot.answers.joinToString(" / ")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Expected: $expectedText",
                                color = palette.ok,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onOverrideSlot(slot.index) }) {
                                Text("Mark Correct", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Check Answer", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            if (runtimeState.isChecked && runtimeState.isCorrect != true) {
                OutlinedButton(
                    onClick = onOverrideCorrect,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.ok)
                ) {
                    Text("Override: Mark Correct", color = palette.ok, fontWeight = FontWeight.SemiBold)
                }
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
            val (msg, msgColor) = when {
                runtimeState.overriddenCorrect -> "Marked correct (override)." to palette.ok
                runtimeState.isCorrect == true && runtimeState.anyFuzzy -> "Correct (accepted fuzzy spelling match)." to palette.fuzzyAccent
                runtimeState.isCorrect == true -> "All blanks correct!" to palette.ok
                else -> "Review expected answers above." to palette.bad
            }
            Text(text = msg, color = msgColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
internal fun QuestionExplanationBox(
    explanation: String,
    layout: ResolvedSlideLayout,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val expShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
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
                markdown = explanation,
                baseFontSizeSp = (layout.bodyFontSizeSp - 1).coerceAtLeast(12),
                contentAlign = ContentAlign.LEFT
            )
        }
    }
}

internal fun formatFillBlankTemplateWithBadges(template: String): String {
    val sb = StringBuilder(template.length)
    var i = 0
    var blankNum = 0
    while (i < template.length) {
        // Skip inline code spans
        if (template[i] == '`') {
            var tickLen = 0
            while (i + tickLen < template.length && template[i + tickLen] == '`') tickLen++
            val closeIdx = findClosingTicks(template, i + tickLen, tickLen)
            if (closeIdx >= 0) {
                sb.append(template, i, closeIdx + tickLen)
                i = closeIdx + tickLen
                continue
            }
        }
        // Skip display or inline math spans
        if (template[i] == '$' && (i == 0 || template[i - 1] != '\\')) {
            if (i + 1 < template.length && template[i + 1] == '$') {
                val close = template.indexOf("$$", i + 2)
                if (close > i + 1) {
                    sb.append(template, i, close + 2)
                    i = close + 2
                    continue
                }
            } else {
                val close = template.indexOf('$', i + 1)
                if (close > i + 1) {
                    sb.append(template, i, close + 1)
                    i = close + 1
                    continue
                }
            }
        }
        if (i + 1 < template.length && template[i] == '{' && template[i + 1] == '{') {
            val close = template.indexOf("}}", i + 2)
            if (close > i + 1) {
                blankNum++
                sb.append("`[ #$blankNum ]`")
                i = close + 2
                continue
            }
        }
        sb.append(template[i])
        i++
    }
    return sb.toString()
}

private fun findClosingTicks(text: String, start: Int, tickLen: Int): Int {
    var j = start
    while (j < text.length) {
        if (text[j] == '`') {
            var run = 0
            while (j + run < text.length && text[j + run] == '`') run++
            if (run == tickLen) return j
            j += run
        } else {
            j++
        }
    }
    return -1
}
