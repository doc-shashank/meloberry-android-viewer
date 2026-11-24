package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.ContentAlign
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.markdown.MarkdownText
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.domain.DeterministicShuffler
import com.meloberry.android.domain.DiffCellVerdict
import com.meloberry.android.domain.EvaluateDifferentiationUseCase

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DifferentiationSlideCard(
    slide: SlideUiModel.DifferentiationSlide,
    runtimeState: DifferentiationSlideRuntimeState,
    onUpdateFillInput: (Int, Int, String) -> Unit,
    onToggleErrorCell: (Int, Int) -> Unit,
    onToggleNoError: () -> Unit,
    onUpdateRearrangeRows: (List<List<String>>) -> Unit,
    onCheckAnswer: () -> Unit,
    onOverrideCorrect: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val layout = slide.layout
    val payload = slide.question.payload
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val effectivePaddingDp = if (isCompact) layout.cardPaddingDp.coerceAtMost(24) else layout.cardPaddingDp
    val cellFontSize = (layout.bodyFontSizeSp - 1).coerceAtLeast(12)

    val specialByCoord = remember(payload.specialCells) {
        payload.specialCells.associateBy { it.rowIndex to it.colIndex }
    }
    val blankCoords = remember(payload.specialCells) {
        payload.specialCells.filter { it.isBlank }.map { it.rowIndex to it.colIndex }
    }
    var focusedBlankCoord by remember(slide.id, blankCoords) { mutableStateOf(blankCoords.firstOrNull()) }
    var selectedSwapCell by remember(slide.id) { mutableStateOf<Pair<Int, Int>?>(null) }

    val initialRearrangeRows = remember(slide.id, payload.rows, payload.mode) {
        if (payload.mode == "rearrange") {
            payload.rows.mapIndexed { rIdx, row ->
                if (row.size <= 1) row
                else listOf(row.first()) + DeterministicShuffler.deterministicShuffle(
                    row.drop(1),
                    "${slide.id}:r$rIdx"
                ) { it }
            }
        } else {
            emptyList()
        }
    }
    val currentRearrangeRows = runtimeState.rearrangeRows.ifEmpty { initialRearrangeRows }

    val wordBank = remember(slide.id, payload.specialCells, payload.bank) {
        if (payload.mode == "fill" && payload.bank) {
            val labels = payload.specialCells
                .filter { it.isBlank && !it.blankAnswers.isNullOrEmpty() }
                .map { it.blankAnswers!!.first() }
            DeterministicShuffler.deterministicShuffle(labels, "${slide.id}:bank") { it }
        } else {
            emptyList()
        }
    }

    val evaluator = remember { EvaluateDifferentiationUseCase() }
    val evalResult = remember(
        payload,
        runtimeState.fillInputsByCell,
        runtimeState.selectedErrorCells,
        runtimeState.pickedNoError,
        currentRearrangeRows,
        runtimeState.overriddenCorrect,
        runtimeState.isChecked
    ) {
        if (!runtimeState.isChecked) null
        else when (payload.mode) {
            "fill" -> {
                val inputs = runtimeState.fillInputsByCell.mapNotNull { (k, v) ->
                    val parts = k.split(':')
                    if (parts.size == 2) {
                        val r = parts[0].toIntOrNull()
                        val c = parts[1].toIntOrNull()
                        if (r != null && c != null) (r to c) to v else null
                    } else null
                }.toMap()
                evaluator.evaluateFill(payload, inputs, overrideCorrect = runtimeState.overriddenCorrect)
            }
            "spot-wrong" -> {
                val sel = runtimeState.selectedErrorCells.mapNotNull { k ->
                    val parts = k.split(':')
                    if (parts.size == 2) {
                        val r = parts[0].toIntOrNull()
                        val c = parts[1].toIntOrNull()
                        if (r != null && c != null) r to c else null
                    } else null
                }.toSet()
                evaluator.evaluateSpotWrong(payload, sel, runtimeState.pickedNoError)
            }
            "rearrange" -> evaluator.evaluateRearrange(payload, currentRearrangeRows)
            else -> null
        }
    }

    val colCount = payload.headers.size.coerceAtLeast(1)
    val colWidths = remember(payload, cellFontSize) {
        List(colCount) { col ->
            val minWidth = if (payload.mode == "fill" && col > 0) 140.dp else 104.dp
            val maxChars = maxOf(
                payload.headers.getOrNull(col)?.length ?: 0,
                payload.rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
            )
            (maxChars * cellFontSize * 0.60f + 32f).dp.coerceIn(minWidth, 240.dp)
        }
    }

    val modeLabel = when (payload.mode) {
        "fill" -> "FILL BLANKS"
        "spot-wrong" -> "SPOT WRONG"
        "rearrange" -> "REARRANGE"
        else -> payload.mode.uppercase()
    }

    FramedSlideCard(
        tabLabel = "DIFFERENTIATION • $modeLabel",
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
            text = when (payload.mode) {
                "fill" -> if (payload.bank) "Tap a blank cell and pick from the word bank, or type your answer:" else "Fill in the missing comparison cells:"
                "spot-wrong" -> "Tap any erroneous cells in the comparison table:"
                "rearrange" -> "Tap two cells in the same row to swap them into their correct columns:"
                else -> ""
            },
            color = palette.muted,
            fontSize = 13.sp
        )

        if (wordBank.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = palette.background,
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("WORD BANK", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        wordBank.forEach { term ->
                            val isUsed = runtimeState.fillInputsByCell.values.any { it.equals(term, ignoreCase = true) }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isUsed) palette.accentSoft else palette.surface,
                                border = BorderStroke(1.dp, if (isUsed) palette.accent else palette.border),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        val target = focusedBlankCoord
                                            ?: blankCoords.firstOrNull { (r, c) ->
                                                runtimeState.fillInputsByCell["$r:$c"].isNullOrBlank()
                                            }
                                            ?: blankCoords.firstOrNull()
                                        if (target != null) {
                                            onUpdateFillInput(target.first, target.second, term)
                                            val nextEmpty = blankCoords.firstOrNull { coord ->
                                                coord != target && runtimeState.fillInputsByCell["${coord.first}:${coord.second}"].isNullOrBlank()
                                            }
                                            focusedBlankCoord = nextEmpty ?: target
                                        }
                                    }
                            ) {
                                Text(
                                    text = term,
                                    color = if (isUsed) palette.accent else palette.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        val tableShape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tableShape)
                .border(1.dp, palette.border, tableShape)
                .horizontalScroll(rememberScrollState())
        ) {
            Row(modifier = Modifier.background(palette.accentSoft)) {
                payload.headers.forEachIndexed { col, header ->
                    val align = when (payload.alignments.getOrNull(col)?.lowercase()) {
                        "center" -> ContentAlign.CENTER
                        "right" -> ContentAlign.RIGHT
                        else -> ContentAlign.LEFT
                    }
                    Box(
                        modifier = Modifier
                            .width(colWidths[col])
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        MarkdownText(
                            markdown = header,
                            baseFontSizeSp = cellFontSize,
                            contentAlign = align,
                            isBoldByDefault = true
                        )
                    }
                }
            }

            payload.rows.forEachIndexed { rIdx, row ->
                HorizontalDivider(color = palette.border)
                val rowBg = if (rIdx % 2 == 1) palette.background else palette.surface
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .background(rowBg)
                ) {
                    for (cIdx in 0 until colCount) {
                        val coord = rIdx to cIdx
                        val cellKey = "$rIdx:$cIdx"
                        val spec = specialByCoord[coord]
                        val verdict = evalResult?.cellVerdicts?.get(coord)
                        val align = when (payload.alignments.getOrNull(cIdx)?.lowercase()) {
                            "center" -> ContentAlign.CENTER
                            "right" -> ContentAlign.RIGHT
                            else -> ContentAlign.LEFT
                        }

                        when {
                            payload.mode == "fill" && spec?.isBlank == true -> {
                                val isFocused = focusedBlankCoord == coord
                                val borderColor = when (verdict) {
                                    DiffCellVerdict.CORRECT -> palette.ok
                                    DiffCellVerdict.FUZZY_CORRECT -> palette.fuzzyAccent
                                    DiffCellVerdict.WRONG -> palette.bad
                                    else -> if (isFocused) palette.accent else palette.border
                                }
                                Column(
                                    modifier = Modifier
                                        .width(colWidths[cIdx])
                                        .fillMaxHeight()
                                        .clickable { focusedBlankCoord = coord }
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = runtimeState.fillInputsByCell[cellKey].orEmpty(),
                                        onValueChange = {
                                            focusedBlankCoord = coord
                                            onUpdateFillInput(rIdx, cIdx, it)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("...", color = palette.muted, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = borderColor,
                                            unfocusedBorderColor = borderColor,
                                            focusedTextColor = palette.text,
                                            unfocusedTextColor = palette.text
                                        )
                                    )
                                    if (verdict == DiffCellVerdict.WRONG && !spec.blankAnswers.isNullOrEmpty()) {
                                        Text(
                                            text = "→ ${spec.blankAnswers!!.first()}",
                                            color = palette.ok,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            payload.mode == "spot-wrong" && cIdx > 0 -> {
                                val isSelected = cellKey in runtimeState.selectedErrorCells
                                val (cellBg, cellBorder) = when (verdict) {
                                    DiffCellVerdict.CORRECT -> palette.ok.copy(alpha = 0.14f) to palette.ok
                                    DiffCellVerdict.MISSED_ERROR -> palette.bad.copy(alpha = 0.10f) to palette.bad
                                    DiffCellVerdict.WRONG -> palette.bad.copy(alpha = 0.16f) to palette.bad
                                    else -> if (isSelected) palette.accentSoft to palette.accent else Color.Transparent to Color.Transparent
                                }
                                val displayText = if (spec?.isError == true && !spec.wrongText.isNullOrBlank()) {
                                    spec.wrongText.orEmpty()
                                } else {
                                    row.getOrNull(cIdx).orEmpty()
                                }

                                Column(
                                    modifier = Modifier
                                        .width(colWidths[cIdx])
                                        .fillMaxHeight()
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cellBg)
                                        .border(
                                            width = if (cellBorder == Color.Transparent) 0.dp else 1.5.dp,
                                            color = cellBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onToggleErrorCell(rIdx, cIdx) }
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    val showDiff = runtimeState.isChecked && spec?.isError == true
                                    Text(
                                        text = displayText,
                                        color = if (showDiff) palette.bad else palette.text,
                                        fontSize = cellFontSize.sp,
                                        textDecoration = if (showDiff) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    if (showDiff && !spec?.corrections.isNullOrEmpty()) {
                                        Text(
                                            text = "→ ${spec!!.corrections!!.joinToString(" / ")}",
                                            color = palette.ok,
                                            fontSize = (cellFontSize - 1).coerceAtLeast(11).sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            payload.mode == "rearrange" && cIdx > 0 -> {
                                val cellText = currentRearrangeRows.getOrNull(rIdx)?.getOrNull(cIdx)
                                    ?: row.getOrNull(cIdx).orEmpty()
                                val isSwapSelected = selectedSwapCell == coord
                                val (cellBg, cellBorder) = when (verdict) {
                                    DiffCellVerdict.CORRECT -> palette.ok.copy(alpha = 0.14f) to palette.ok
                                    DiffCellVerdict.WRONG -> palette.bad.copy(alpha = 0.14f) to palette.bad
                                    else -> if (isSwapSelected) palette.accentSoft to palette.accent else palette.surface to palette.border
                                }
                                Column(
                                    modifier = Modifier
                                        .width(colWidths[cIdx])
                                        .fillMaxHeight()
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cellBg)
                                        .border(1.5.dp, cellBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            val first = selectedSwapCell
                                            if (first == null) {
                                                selectedSwapCell = coord
                                            } else if (first == coord) {
                                                selectedSwapCell = null
                                            } else if (first.first == rIdx) {
                                                // Swap within same row
                                                val updated = currentRearrangeRows.mapIndexed { rr, existingRow ->
                                                    if (rr != rIdx) existingRow
                                                    else existingRow.toMutableList().also { mut ->
                                                        val tmp = mut[first.second]
                                                        mut[first.second] = mut[cIdx]
                                                        mut[cIdx] = tmp
                                                    }
                                                }
                                                onUpdateRearrangeRows(updated)
                                                selectedSwapCell = null
                                            } else {
                                                selectedSwapCell = coord
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    MarkdownText(
                                        markdown = cellText,
                                        baseFontSizeSp = cellFontSize,
                                        contentAlign = align
                                    )
                                    if (verdict == DiffCellVerdict.WRONG) {
                                        Text(
                                            text = "Expected: ${row.getOrNull(cIdx).orEmpty()}",
                                            color = palette.ok,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .width(colWidths[cIdx])
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    MarkdownText(
                                        markdown = row.getOrNull(cIdx).orEmpty(),
                                        baseFontSizeSp = cellFontSize,
                                        contentAlign = align,
                                        isBoldByDefault = cIdx == 0
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (payload.mode == "spot-wrong" && payload.allowNoError) {
            val picked = runtimeState.pickedNoError
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (picked) palette.accentSoft else palette.surface,
                border = BorderStroke(1.5.dp, if (picked) palette.accent else palette.border),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleNoError)
            ) {
                Text(
                    text = "No error in this table",
                    color = if (picked) palette.accent else palette.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
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

            if (payload.mode == "fill" && runtimeState.isChecked && runtimeState.isCorrect != true) {
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
                    onClick = {
                        selectedSwapCell = null
                        onRetry()
                    },
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
                runtimeState.isCorrect == true -> "Correct!" to palette.ok
                else -> "Review highlighted cells above." to palette.bad
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
