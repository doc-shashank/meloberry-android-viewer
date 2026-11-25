package com.meloberry.android.feature.player

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.meloberry.android.core.model.RelabelBox
import com.meloberry.android.core.model.RelabelImagePayload
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.components.SharedSvgImageLoader
import com.meloberry.android.core.ui.markdown.MarkdownText
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.domain.DeterministicShuffler
import com.meloberry.android.domain.EvaluateRelabelImageUseCase
import com.meloberry.android.domain.RelabelBoxVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RelabelImageSlideCard(
    slide: SlideUiModel.RelabelImageSlide,
    runtimeState: RelabelImageSlideRuntimeState,
    onToggleRevealBox: (Int) -> Unit,
    onRateReveal: (String) -> Unit,
    onAssignBankLabel: (Int, String) -> Unit,
    onUpdateRecallInput: (Int, String) -> Unit,
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

    var focusedBoxIndex by remember(slide.id, payload.boxes) { mutableStateOf(payload.boxes.firstOrNull()?.index) }
    var isLightboxOpen by remember(slide.id) { mutableStateOf(false) }

    val imageAspectRatio by produceState(initialValue = 16f / 9f, key1 = payload.imageFilePath) {
        value = withContext(Dispatchers.IO) {
            detectImageAspectRatio(payload.imageFilePath)
        }
    }

    val shuffledBank = remember(slide.id, payload.boxes, payload.distractors) {
        if (payload.mode == "bank") {
            val allItems = (payload.boxes.map { it.label } + payload.distractors)
                .mapIndexed { idx, lbl -> idx to lbl }
            DeterministicShuffler.deterministicShuffle(allItems, "${slide.id}:relbank") { "${it.first}:${it.second}" }
                .map { it.second }
        } else {
            emptyList()
        }
    }

    val evaluator = remember { EvaluateRelabelImageUseCase() }
    val evalResult = remember(
        payload,
        runtimeState.revealedBoxIndices,
        runtimeState.assignedLabelsByBox,
        runtimeState.recallInputsByBox,
        runtimeState.rating,
        runtimeState.overriddenCorrect,
        runtimeState.isChecked
    ) {
        if (!runtimeState.isChecked) null
        else when (payload.mode) {
            "bank" -> evaluator.evaluateBank(payload, runtimeState.assignedLabelsByBox)
            "recall" -> evaluator.evaluateRecall(payload, runtimeState.recallInputsByBox, overrideCorrect = runtimeState.overriddenCorrect)
            else -> evaluator.evaluateReveal(payload, runtimeState.revealedBoxIndices, runtimeState.rating)
        }
    }

    FramedSlideCard(
        tabLabel = "RELABEL IMAGE • ${payload.mode.uppercase()}",
        modifier = modifier,
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = effectivePaddingDp,
        contentSpacingDp = 14
    ) {
        if (!payload.prompt.isNullOrBlank()) {
            MarkdownText(
                markdown = payload.prompt.orEmpty(),
                baseFontSizeSp = layout.promptFontSizeSp,
                contentAlign = layout.contentAlign,
                isBoldByDefault = true
            )
        } else {
            Text(
                text = when (payload.mode) {
                    "reveal" -> "Tap each masked box to reveal the label, then rate your recall:"
                    "bank" -> "Tap a masked box, then select its label from the bank:"
                    else -> "Tap a masked box and type its label below:"
                },
                color = palette.muted,
                fontSize = 13.sp
            )
        }

        // Aspect-ratio locked diagram + mask overlays + zoom button
        Box(modifier = Modifier.fillMaxWidth()) {
            RelabelDiagramCanvas(
                payload = payload,
                aspectRatio = imageAspectRatio,
                runtimeState = runtimeState,
                focusedBoxIndex = focusedBoxIndex,
                boxVerdicts = evalResult?.boxVerdicts.orEmpty(),
                onTapBox = { boxIdx ->
                    focusedBoxIndex = boxIdx
                    if (payload.mode == "reveal") {
                        onToggleRevealBox(boxIdx)
                    }
                }
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .clickable { isLightboxOpen = true },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom diagram",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(7.dp)
                        .size(18.dp)
                )
            }
        }

        // Mode-specific interactive controls
        when (payload.mode) {
            "reveal" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    listOf(
                        Triple("known", "Got It", palette.ok),
                        Triple("needsReview", "Needs Review", palette.bad)
                    ).forEach { (key, label, activeColor) ->
                        val isActive = runtimeState.rating == key
                        OutlinedButton(
                            onClick = { onRateReveal(key) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, if (isActive) activeColor else palette.border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isActive) activeColor.copy(alpha = 0.14f) else Color.Transparent,
                                contentColor = if (isActive) activeColor else palette.text
                            )
                        ) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            "bank" -> {
                val activeBox = payload.boxes.firstOrNull { it.index == focusedBoxIndex } ?: payload.boxes.firstOrNull()
                if (activeBox != null) {
                    Text(
                        text = "Selected Box #${activeBox.index}" +
                            (runtimeState.assignedLabelsByBox[activeBox.index]?.let { " → $it" } ?: " (tap a label below)"),
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = palette.background,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    FlowRow(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        shuffledBank.forEach { label ->
                            val isAssignedAnywhere = runtimeState.assignedLabelsByBox.values.any { it == label }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isAssignedAnywhere) palette.accentSoft else palette.surface,
                                border = BorderStroke(1.dp, if (isAssignedAnywhere) palette.accent else palette.border),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        val targetIdx = focusedBoxIndex ?: payload.boxes.firstOrNull()?.index ?: return@clickable
                                        onAssignBankLabel(targetIdx, label)
                                        val nextUnassigned = payload.boxes.firstOrNull { box ->
                                            box.index != targetIdx && runtimeState.assignedLabelsByBox[box.index].isNullOrBlank()
                                        }
                                        if (nextUnassigned != null) {
                                            focusedBoxIndex = nextUnassigned.index
                                        }
                                    }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isAssignedAnywhere) palette.accent else palette.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
            "recall" -> {
                val activeBox = payload.boxes.firstOrNull { it.index == focusedBoxIndex } ?: payload.boxes.firstOrNull()
                if (activeBox != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            payload.boxes.forEach { box ->
                                val isSel = box.index == activeBox.index
                                val verdict = evalResult?.boxVerdicts?.get(box.index)
                                val pillColor = when (verdict) {
                                    RelabelBoxVerdict.EXACT, RelabelBoxVerdict.OVERRIDDEN -> palette.ok
                                    RelabelBoxVerdict.FUZZY -> palette.fuzzyAccent
                                    RelabelBoxVerdict.WRONG -> palette.bad
                                    null -> if (isSel) palette.accent else palette.border
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) pillColor.copy(alpha = 0.16f) else palette.surface,
                                    border = BorderStroke(1.5.dp, pillColor),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { focusedBoxIndex = box.index }
                                ) {
                                    Text(
                                        text = "#${box.index}",
                                        color = if (isSel) pillColor else palette.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = runtimeState.recallInputsByBox[activeBox.index].orEmpty(),
                            onValueChange = { onUpdateRecallInput(activeBox.index, it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Label for #${activeBox.index}") },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.accent,
                                unfocusedBorderColor = palette.border,
                                focusedTextColor = palette.text,
                                unfocusedTextColor = palette.text
                            )
                        )
                    }
                }
            }
        }

        // Action row for bank & recall modes (plus Retry for reveal)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (payload.mode != "reveal") {
                Button(
                    onClick = onCheckAnswer,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Check Answer", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            if (payload.mode == "recall" && runtimeState.isChecked && runtimeState.isCorrect != true) {
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
                payload.mode == "reveal" -> {
                    if (runtimeState.rating == "known") "Marked as Got It." to palette.ok
                    else "Marked as Needs Review." to palette.bad
                }
                runtimeState.overriddenCorrect -> "Marked correct (override)." to palette.ok
                runtimeState.isCorrect == true && runtimeState.anyFuzzy -> "Correct (accepted fuzzy spelling match)." to palette.fuzzyAccent
                runtimeState.isCorrect == true -> "All labels correct!" to palette.ok
                else -> "Review expected labels below." to palette.bad
            }
            Text(text = msg, color = msgColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

            if (payload.mode != "reveal" && evalResult != null && !evalResult.isCorrect) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    payload.boxes.forEach { box ->
                        val v = evalResult.boxVerdicts[box.index]
                        if (v == RelabelBoxVerdict.WRONG) {
                            Text(
                                text = "#${box.index}: ${box.label}" +
                                    if (box.aliases.isNotEmpty()) " (${box.aliases.joinToString(", ")})" else "",
                                color = palette.ok,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
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

    if (isLightboxOpen) {
        RelabelZoomLightboxDialog(
            payload = payload,
            aspectRatio = imageAspectRatio,
            runtimeState = runtimeState,
            focusedBoxIndex = focusedBoxIndex,
            boxVerdicts = evalResult?.boxVerdicts.orEmpty(),
            onDismiss = { isLightboxOpen = false }
        )
    }
}

@Composable
private fun RelabelDiagramCanvas(
    payload: RelabelImagePayload,
    aspectRatio: Float,
    runtimeState: RelabelImageSlideRuntimeState,
    focusedBoxIndex: Int?,
    boxVerdicts: Map<Int, RelabelBoxVerdict>,
    onTapBox: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalMeloberryColors.current
    val imageLoader = remember(context) { SharedSvgImageLoader.get(context) }
    val model = remember(payload.imageFilePath, context) {
        ImageRequest.Builder(context)
            .data(File(payload.imageFilePath))
            .crossfade(false)
            .build()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.4f, 3.0f))
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .background(palette.background)
    ) {
        val boxW = maxWidth
        val boxH = maxHeight

        AsyncImage(
            model = model,
            imageLoader = imageLoader,
            contentDescription = payload.prompt ?: "Relabel diagram",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        payload.boxes.forEach { box ->
            RelabelMaskOverlay(
                box = box,
                style = payload.style,
                mode = payload.mode,
                isRevealed = box.index in runtimeState.revealedBoxIndices,
                assignedOrTyped = when (payload.mode) {
                    "bank" -> runtimeState.assignedLabelsByBox[box.index]
                    "recall" -> runtimeState.recallInputsByBox[box.index]
                    else -> null
                },
                isFocused = focusedBoxIndex == box.index,
                verdict = boxVerdicts[box.index],
                isChecked = runtimeState.isChecked,
                onClick = { onTapBox(box.index) },
                modifier = Modifier
                    .offset(x = boxW * box.x.toFloat(), y = boxH * box.y.toFloat())
                    .size(width = boxW * box.w.toFloat(), height = boxH * box.h.toFloat())
            )
        }
    }
}

@Composable
private fun RelabelMaskOverlay(
    box: RelabelBox,
    style: String,
    mode: String,
    isRevealed: Boolean,
    assignedOrTyped: String?,
    isFocused: Boolean,
    verdict: RelabelBoxVerdict?,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val showAnswer = (mode == "reveal" && isRevealed) || (isChecked && verdict != null)
    val borderColor = when (verdict) {
        RelabelBoxVerdict.EXACT, RelabelBoxVerdict.OVERRIDDEN -> palette.ok
        RelabelBoxVerdict.FUZZY -> palette.fuzzyAccent
        RelabelBoxVerdict.WRONG -> palette.bad
        null -> if (isFocused) palette.accent else palette.accent.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (showAnswer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when (verdict) {
                            RelabelBoxVerdict.EXACT, RelabelBoxVerdict.OVERRIDDEN -> palette.ok.copy(alpha = 0.90f)
                            RelabelBoxVerdict.FUZZY -> palette.fuzzyAccent.copy(alpha = 0.90f)
                            RelabelBoxVerdict.WRONG -> palette.bad.copy(alpha = 0.90f)
                            null -> palette.surface.copy(alpha = 0.92f)
                        }
                    )
                    .border(2.dp, borderColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = box.label,
                    color = if (verdict != null) Color.White else palette.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // Render mask style
            when (style) {
                "whiteout" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFCFBF7))
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = borderColor,
                            style = Stroke(
                                width = 3f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                            )
                        )
                    }
                }
                "pixelate" -> {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cols = 6
                        val rows = 3
                        val cw = size.width / cols
                        val ch = size.height / rows
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                val shade = if ((r + c) % 2 == 0) palette.accent.copy(alpha = 0.72f) else palette.surface.copy(alpha = 0.85f)
                                drawRect(color = shade, topLeft = Offset(c * cw, r * ch), size = Size(cw + 1f, ch + 1f))
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize().border(2.dp, borderColor, RoundedCornerShape(6.dp)))
                }
                "blur" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(palette.surface.copy(alpha = 0.95f))
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val step = 10.dp.toPx()
                        var x = -size.height
                        while (x < size.width + size.height) {
                            drawLine(
                                color = palette.muted.copy(alpha = 0.22f),
                                start = Offset(x, 0f),
                                end = Offset(x + size.height, size.height),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            x += step
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
                    )
                }
                else -> {
                    // solid
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isFocused) palette.accent else palette.accent.copy(alpha = 0.88f))
                            .border(2.dp, if (isFocused) Color.White else borderColor, RoundedCornerShape(6.dp))
                    )
                }
            }

            val badgeText = if (!assignedOrTyped.isNullOrBlank()) {
                "#${box.index} ${assignedOrTyped.trim()}"
            } else {
                "#${box.index}"
            }
            val textColor = when (style) {
                "whiteout", "blur" -> palette.text
                else -> Color.White
            }
            Text(
                text = badgeText,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun RelabelZoomLightboxDialog(
    payload: RelabelImagePayload,
    aspectRatio: Float,
    runtimeState: RelabelImageSlideRuntimeState,
    focusedBoxIndex: Int?,
    boxVerdicts: Map<Int, RelabelBoxVerdict>,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(candidate: Offset, targetScale: Float): Offset {
        if (targetScale <= 1f || containerSize == IntSize.Zero) return Offset.Zero
        val maxX = ((containerSize.width * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
        val maxY = ((containerSize.height * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged {
                        containerSize = it
                        offset = clampOffset(offset, scale)
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (nextScale > 1f) {
                                clampOffset(Offset(offset.x + pan.x, offset.y + pan.y), nextScale)
                            } else Offset.Zero
                            scale = nextScale
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1.05f) 1f else 2.5f
                            offset = Offset.Zero
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                RelabelDiagramCanvas(
                    payload = payload,
                    aspectRatio = aspectRatio,
                    runtimeState = runtimeState,
                    focusedBoxIndex = focusedBoxIndex,
                    boxVerdicts = boxVerdicts,
                    onTapBox = {},
                    modifier = Modifier
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.14f)) {
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (scale > 1.01f) {
                        TextButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                            Text("Reset", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close zoom", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun detectImageAspectRatio(filePath: String): Float {
    val file = File(filePath)
    if (!file.isFile) return 16f / 9f
    if (file.extension.equals("svg", ignoreCase = true)) {
        runCatching {
            val buf = ByteArray(2048)
            val bytesRead = FileInputStream(file).use { input ->
                var total = 0
                while (total < buf.size) {
                    val r = input.read(buf, total, buf.size - total)
                    if (r <= 0) break
                    total += r
                }
                total
            }
            if (bytesRead > 0) {
                val head = String(buf, 0, bytesRead, Charsets.UTF_8)
                val vbMatch = Regex(
                    """viewBox\s*=\s*["']\s*[0-9.+-]+[\s,]+[0-9.+-]+[\s,]+([0-9.]+)[\s,]+([0-9.]+)\s*["']""",
                    RegexOption.IGNORE_CASE
                ).find(head)
                if (vbMatch != null) {
                    val w = vbMatch.groupValues[1].toFloatOrNull() ?: 0f
                    val h = vbMatch.groupValues[2].toFloatOrNull() ?: 0f
                    if (w > 0f && h > 0f) return w / h
                }
            }
        }
        return 16f / 9f
    }
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(filePath, opts)
    return if (opts.outWidth > 0 && opts.outHeight > 0) {
        opts.outWidth.toFloat() / opts.outHeight.toFloat()
    } else {
        16f / 9f
    }
}
