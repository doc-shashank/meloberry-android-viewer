package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.meloberry.android.domain.DeterministicShuffler
import com.meloberry.android.domain.EvaluateMechanismUseCase
import com.meloberry.android.domain.MechanismSlotVerdict

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MechanismSlideCard(
    slide: SlideUiModel.MechanismSlide,
    runtimeState: MechanismSlideRuntimeState,
    onUpdateSequence: (bankStepIds: List<Int>, placedStepIds: List<Int>) -> Unit,
    onCheckAnswer: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val layout = slide.layout
    val payload = slide.question.payload
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val effectivePaddingDp = if (isCompact) layout.cardPaddingDp.coerceAtMost(24) else layout.cardPaddingDp

    val stepById = remember(payload.steps) { payload.steps.associateBy { it.stepId } }
    val defaultShuffledBank = remember(slide.id, payload.steps) {
        DeterministicShuffler.deterministicShuffle(payload.steps, "${slide.id}:mech") { it.stepId.toString() }
            .map { it.stepId }
    }
    val hasDistractors = remember(payload.steps) { payload.steps.any { it.isDistractor } }

    val bankIds = if (runtimeState.bankStepIds.isEmpty() && runtimeState.placedStepIds.isEmpty()) {
        defaultShuffledBank
    } else {
        runtimeState.bankStepIds
    }
    val placedIds = runtimeState.placedStepIds

    val evaluator = remember { EvaluateMechanismUseCase() }
    val evalResult = remember(payload, placedIds, runtimeState.isChecked) {
        if (runtimeState.isChecked) evaluator(payload, placedIds) else null
    }
    val verdictByIndex = remember(evalResult) {
        evalResult?.slotResults?.mapIndexed { idx, slot -> idx to slot.verdict }?.toMap().orEmpty()
    }

    FramedSlideCard(
        tabLabel = "MECHANISM ARRANGEMENT",
        modifier = modifier,
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = effectivePaddingDp,
        contentSpacingDp = 14
    ) {
        if (!payload.title.isNullOrBlank()) {
            Text(
                text = payload.title.orEmpty(),
                color = palette.text,
                fontSize = layout.promptFontSizeSp.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = if (hasDistractors) {
                "Tap steps from the bank to build the sequence in order. Leave distractor steps excluded."
            } else {
                "Tap steps from the bank to build the correct sequence, then fine-tune with Up / Down."
            },
            color = palette.muted,
            fontSize = 13.sp
        )

        // Placed sequence tray
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = palette.background,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "YOUR SEQUENCE (${placedIds.size})",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (placedIds.isEmpty()) {
                    Text(
                        text = "Tap steps below to place them here in order.",
                        color = palette.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    placedIds.forEachIndexed { idx, stepId ->
                        val step = stepById[stepId] ?: return@forEachIndexed
                        val verdict = verdictByIndex[idx]
                        val (bg, border, badgeColor) = when (verdict) {
                            MechanismSlotVerdict.CORRECT -> Triple(palette.ok.copy(alpha = 0.12f), palette.ok, palette.ok)
                            MechanismSlotVerdict.WRONG_POSITION -> Triple(palette.fuzzyBg, palette.fuzzyAccent, palette.fuzzyAccent)
                            MechanismSlotVerdict.DISTRACTOR_PLACED -> Triple(palette.bad.copy(alpha = 0.14f), palette.bad, palette.bad)
                            null -> Triple(palette.surface, palette.border, palette.accent)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = bg,
                            border = BorderStroke(1.5.dp, border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        color = badgeColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    MarkdownText(
                                        markdown = step.text,
                                        baseFontSizeSp = (layout.bodyFontSizeSp - 1).coerceAtLeast(13)
                                    )
                                    if (verdict == MechanismSlotVerdict.DISTRACTOR_PLACED) {
                                        Text("Distractor (exclude)", color = palette.bad, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    } else if (verdict == MechanismSlotVerdict.WRONG_POSITION) {
                                        Text("Expected step group ${step.orderGroup}", color = palette.fuzzyAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (idx > 0) {
                                                val nextPlaced = placedIds.toMutableList().also {
                                                    val tmp = it[idx - 1]
                                                    it[idx - 1] = it[idx]
                                                    it[idx] = tmp
                                                }
                                                onUpdateSequence(bankIds, nextPlaced)
                                            }
                                        },
                                        enabled = idx > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = palette.text, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (idx < placedIds.lastIndex) {
                                                val nextPlaced = placedIds.toMutableList().also {
                                                    val tmp = it[idx + 1]
                                                    it[idx + 1] = it[idx]
                                                    it[idx] = tmp
                                                }
                                                onUpdateSequence(bankIds, nextPlaced)
                                            }
                                        },
                                        enabled = idx < placedIds.lastIndex,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = palette.text, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            onUpdateSequence(bankIds + stepId, placedIds - stepId)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove from sequence", tint = palette.muted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Remaining bank / excluded tray
        if (bankIds.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (hasDistractors) "STEP BANK / EXCLUDED (${bankIds.size})" else "STEP BANK (${bankIds.size})",
                    color = palette.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                bankIds.forEach { stepId ->
                    val step = stepById[stepId] ?: return@forEach
                    val showUnplacedDistractorOk = runtimeState.isChecked && step.isDistractor
                    val showMissedStep = runtimeState.isChecked && !step.isDistractor
                    val borderColor = when {
                        showUnplacedDistractorOk -> palette.ok
                        showMissedStep -> palette.bad
                        else -> palette.border
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onUpdateSequence(bankIds - stepId, placedIds + stepId)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add step",
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                MarkdownText(
                                    markdown = step.text,
                                    baseFontSizeSp = (layout.bodyFontSizeSp - 1).coerceAtLeast(13)
                                )
                                if (showUnplacedDistractorOk) {
                                    Text("Distractor (correctly excluded)", color = palette.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else if (showMissedStep) {
                                    Text("Missed required step (group ${step.orderGroup})", color = palette.bad, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
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
                enabled = placedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Check Answer", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            if (runtimeState.isChecked || placedIds.isNotEmpty()) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Text("Reset", color = palette.text, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (runtimeState.isChecked) {
            Text(
                text = if (runtimeState.isCorrect == true) "Correct sequence!" else "Sequence needs adjustment — review canonical order below.",
                color = if (runtimeState.isCorrect == true) palette.ok else palette.bad,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (runtimeState.isCorrect != true && evalResult != null) {
                val groupedCounts = evalResult.canonicalSteps.groupingBy { it.orderGroup }.eachCount()
                val seenInGroup = mutableMapOf<Int, Int>()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = palette.ok.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, palette.ok.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("CANONICAL ORDER", color = palette.ok, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        evalResult.canonicalSteps.forEach { step ->
                            val countInGroup = groupedCounts[step.orderGroup] ?: 1
                            val label = if (countInGroup > 1) {
                                val subIdx = seenInGroup.getOrDefault(step.orderGroup, 0)
                                seenInGroup[step.orderGroup] = subIdx + 1
                                "${step.orderGroup}${'a' + subIdx}."
                            } else {
                                "${step.orderGroup}."
                            }
                            Text(
                                text = "$label ${step.text}",
                                color = palette.text,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        if (runtimeState.isChecked && !payload.explanation.isNullOrBlank()) {
            QuestionExplanationBox(
                explanation = payload.explanation.orEmpty(),
                layout = layout
            )
        }
    }
}
