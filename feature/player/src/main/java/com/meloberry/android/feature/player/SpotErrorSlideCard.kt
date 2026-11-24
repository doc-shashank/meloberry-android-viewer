package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.components.MeloberryAssetImage
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.domain.EvaluateSpotErrorUseCase
import com.meloberry.android.domain.SpotErrorSpanVerdict

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpotErrorSlideCard(
    slide: SlideUiModel.SpotErrorSlide,
    runtimeState: SpotErrorSlideRuntimeState,
    onToggleSpan: (Int) -> Unit,
    onToggleNoError: () -> Unit,
    onUpdateFix: (Int, String) -> Unit,
    onCheckAnswer: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val layout = slide.layout
    val payload = slide.question.payload
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val effectivePaddingDp = if (isCompact) layout.cardPaddingDp.coerceAtMost(24) else layout.cardPaddingDp

    val evaluator = remember { EvaluateSpotErrorUseCase() }
    val evalResult = remember(payload, runtimeState.selectedSpans, runtimeState.pickedNoError, runtimeState.fixesBySpan, runtimeState.isChecked) {
        if (runtimeState.isChecked) {
            evaluator(
                payload = payload,
                selectedSpanIndices = runtimeState.selectedSpans,
                pickedNoError = runtimeState.pickedNoError,
                fixesBySpanIndex = runtimeState.fixesBySpan
            )
        } else {
            null
        }
    }

    val candidateBySpan = remember(payload.segments) {
        payload.segments.filter { it.isCandidate && it.spanIndex != null }.associateBy { it.spanIndex!! }
    }

    FramedSlideCard(
        tabLabel = if (payload.requireFix) "SPOT & FIX THE ERROR" else "SPOT THE ERROR",
        modifier = modifier,
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = effectivePaddingDp,
        contentSpacingDp = 16
    ) {
        Text(
            text = if (payload.requireFix) {
                "Tap the erroneous phrase(s) below and enter the correction:"
            } else {
                "Tap the erroneous phrase(s) in the statement below:"
            },
            color = palette.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        if (!payload.imageFilePath.isNullOrBlank()) {
            MeloberryAssetImage(
                filePath = payload.imageFilePath.orEmpty(),
                contentDescription = "Spot the error diagram",
                maxHeightDp = layout.imageMaxHeightDp
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = palette.background,
            border = BorderStroke(1.dp, palette.border)
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                payload.segments.forEach { seg ->
                    if (!seg.isCandidate || seg.spanIndex == null) {
                        val trimmed = seg.text.trim()
                        if (trimmed.isNotEmpty()) {
                            Text(
                                text = trimmed,
                                color = palette.text,
                                fontSize = layout.bodyFontSizeSp.sp,
                                lineHeight = (layout.bodyFontSizeSp * 1.45f).sp,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    } else {
                        val idx = seg.spanIndex!!
                        val isSelected = idx in runtimeState.selectedSpans
                        val verdict = evalResult?.spanVerdicts?.get(idx)
                        val (chipBg, chipBorder, chipText) = when (verdict) {
                            SpotErrorSpanVerdict.CORRECT_ERROR ->
                                Triple(palette.ok.copy(alpha = 0.14f), palette.ok, palette.ok)
                            SpotErrorSpanVerdict.WRONG_FIX ->
                                Triple(palette.fuzzyBg, palette.fuzzyAccent, palette.fuzzyAccent)
                            SpotErrorSpanVerdict.MISSED_ERROR ->
                                Triple(palette.bad.copy(alpha = 0.10f), palette.bad, palette.bad)
                            SpotErrorSpanVerdict.FALSE_POSITIVE ->
                                Triple(palette.bad.copy(alpha = 0.14f), palette.bad, palette.bad)
                            else -> if (isSelected) {
                                Triple(palette.accentSoft, palette.accent, palette.accent)
                            } else {
                                Triple(palette.surface, palette.border, palette.text)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = chipBg,
                            border = BorderStroke(1.5.dp, chipBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleSpan(idx) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val showStrike = runtimeState.isChecked && seg.isError
                                Text(
                                    text = seg.text,
                                    color = chipText,
                                    fontSize = layout.bodyFontSizeSp.sp,
                                    fontWeight = if (isSelected || showStrike) FontWeight.Bold else FontWeight.Medium,
                                    textDecoration = if (showStrike) TextDecoration.LineThrough else TextDecoration.None
                                )
                                if (showStrike && !seg.corrections.isNullOrEmpty()) {
                                    Text(
                                        text = "→ ${seg.corrections!!.joinToString(" / ")}",
                                        color = palette.ok,
                                        fontSize = (layout.bodyFontSizeSp - 1).coerceAtLeast(13).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (payload.allowNoError || payload.noError) {
            val noErrSelected = runtimeState.pickedNoError
            val (noErrBg, noErrBorder, noErrText) = when {
                runtimeState.isChecked && payload.noError && noErrSelected ->
                    Triple(palette.ok.copy(alpha = 0.14f), palette.ok, palette.ok)
                runtimeState.isChecked && !payload.noError && noErrSelected ->
                    Triple(palette.bad.copy(alpha = 0.14f), palette.bad, palette.bad)
                runtimeState.isChecked && payload.noError && !noErrSelected ->
                    Triple(palette.ok.copy(alpha = 0.08f), palette.ok, palette.ok)
                noErrSelected ->
                    Triple(palette.accentSoft, palette.accent, palette.accent)
                else ->
                    Triple(palette.surface, palette.border, palette.text)
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = noErrBg,
                border = BorderStroke(1.5.dp, noErrBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleNoError)
            ) {
                Text(
                    text = "No error in this statement",
                    color = noErrText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }

        if (payload.requireFix && !runtimeState.pickedNoError && runtimeState.selectedSpans.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                runtimeState.selectedSpans.sorted().forEach { spanIdx ->
                    val seg = candidateBySpan[spanIdx] ?: return@forEach
                    val fixValue = runtimeState.fixesBySpan[spanIdx].orEmpty()
                    val fixMatch = evalResult?.fixMatchResults?.get(spanIdx)
                    val borderColor = when {
                        fixMatch == null -> palette.border
                        fixMatch.isMatch -> palette.ok
                        else -> palette.bad
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Correction for \"${seg.text}\":",
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = fixValue,
                            onValueChange = { onUpdateFix(spanIdx, it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Enter correction...", color = palette.muted, fontSize = 14.sp) },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (fixMatch == null) palette.accent else borderColor,
                                unfocusedBorderColor = borderColor,
                                focusedTextColor = palette.text,
                                unfocusedTextColor = palette.text,
                                cursorColor = palette.accent
                            )
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
                enabled = runtimeState.selectedSpans.isNotEmpty() || runtimeState.pickedNoError,
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
                text = if (runtimeState.isCorrect == true) "Correct!" else "Review highlighted spans and corrections above.",
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
