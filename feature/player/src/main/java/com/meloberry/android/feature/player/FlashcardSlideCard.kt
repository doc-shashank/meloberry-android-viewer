package com.meloberry.android.feature.player

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.FlashcardSideModel
import com.meloberry.android.core.model.MediaPosition
import com.meloberry.android.core.model.ResolvedSlideLayout
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.components.MeloberryAssetImage
import com.meloberry.android.core.ui.markdown.MarkdownText
import com.meloberry.android.core.ui.theme.LocalMeloberryColors

@Composable
internal fun FlashcardSlideCard(
    slide: SlideUiModel.FlashcardSlide,
    runtimeState: FlashcardSlideRuntimeState,
    onFlip: () -> Unit,
    onRate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val layout = slide.layout
    val card = slide.question
    val flipEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val rotation by animateFloatAsState(
        targetValue = if (runtimeState.isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = layout.flipDurationMs, easing = flipEasing),
        label = "flashcard_flip"
    )
    val showingBack = rotation > 90f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = layout.cardMaxWidthDp.dp)
            .animateContentSize(
                animationSpec = tween(durationMillis = layout.flipDurationMs, easing = flipEasing)
            )
            .graphicsLayer {
                rotationY = if (showingBack) rotation - 180f else rotation
                cameraDistance = 56f * density
            }
            .clickable(onClick = onFlip)
    ) {
        FlashcardFaceContent(
            faceLabel = if (showingBack) "Back" else "Front",
            side = if (showingBack) card.back else card.front,
            layout = layout,
            showRating = showingBack,
            currentRating = runtimeState.rating,
            onRate = onRate
        )
    }
}

@Composable
private fun FlashcardFaceContent(
    faceLabel: String,
    side: FlashcardSideModel,
    layout: ResolvedSlideLayout,
    showRating: Boolean,
    currentRating: String?,
    onRate: (String) -> Unit
) {
    val palette = LocalMeloberryColors.current

    FramedSlideCard(
        tabLabel = "FLASHCARD",
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = layout.cardPaddingDp,
        bottomContentPaddingDp = 16,
        contentSpacingDp = 14,
        minHeightDp = 250
    ) {
        val renderTextAbove = side.position == MediaPosition.ABOVE_IMAGE ||
            (side.position == MediaPosition.BELOW_IMAGE && layout.defaultTextPosition == MediaPosition.ABOVE_IMAGE)
        if (renderTextAbove) {
            SideText(side, layout)
            SideImage(side, layout)
        } else {
            SideImage(side, layout)
            SideText(side, layout)
        }

        if (showRating) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                listOf(
                    Triple("known", "Got It", palette.ok),
                    Triple("needsReview", "Needs Review", palette.bad)
                ).forEach { (key, label, activeColor) ->
                    val isActive = currentRating == key
                    OutlinedButton(
                        onClick = { onRate(key) },
                        shape = RoundedCornerShape(8.dp),
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

        Text(
            text = faceLabel,
            color = Color(0xFF9E9E9E),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        )
    }
}

@Composable
private fun SideText(side: FlashcardSideModel, layout: ResolvedSlideLayout) {
    if (!side.text.isNullOrBlank()) {
        MarkdownText(
            markdown = side.text.orEmpty(),
            baseFontSizeSp = layout.bodyFontSizeSp,
            contentAlign = layout.contentAlign
        )
    }
}

@Composable
private fun SideImage(side: FlashcardSideModel, layout: ResolvedSlideLayout) {
    if (!side.imageFilePath.isNullOrBlank()) {
        MeloberryAssetImage(
            filePath = side.imageFilePath.orEmpty(),
            contentDescription = "Flashcard media",
            maxHeightDp = layout.imageMaxHeightDp
        )
    }
}
