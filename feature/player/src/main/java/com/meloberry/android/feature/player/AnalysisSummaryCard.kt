package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.SessionAnalysisReport
import com.meloberry.android.core.model.SlideAnalysisEntry
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.domain.BuildAnalysisReportUseCase
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnalysisSummaryCard(
    report: SessionAnalysisReport?,
    onExportJson: () -> Unit,
    onExportMarkdown: () -> Unit,
    onShareMarkdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current

    if (report == null) {
        Surface(
            modifier = modifier.fillMaxWidth().widthIn(max = 840.dp),
            shape = RoundedCornerShape(18.dp),
            color = palette.surface,
            border = BorderStroke(1.dp, palette.border),
            shadowElevation = 3.dp
        ) {
            Text(
                text = "Calculating session summary...",
                color = palette.muted,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    val slidesById = report.slides.associateBy { it.id }

    FramedSlideCard(
        tabLabel = "SESSION SUMMARY",
        modifier = modifier,
        contentPaddingDp = 24,
        topContentPaddingDp = 16,
        contentSpacingDp = 20
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = report.title.ifBlank { report.workspaceId },
                color = palette.text,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "${report.workspaceId}  •  Completed in ${String.format(Locale.US, "%.1fs", report.totalDurationMs / 1000.0)}",
                color = palette.muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("GRADED SCORE", "${report.summary.mcqCorrectFinal} / ${report.summary.mcqTotal}", Modifier.weight(1f))
                StatBox("ACCURACY", String.format(Locale.US, "%.1f%%", report.summary.mcqAccuracyPercent), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("FLASHCARDS KNOWN", "${report.summary.flashcardKnown} / ${report.summary.flashcardTotal}", Modifier.weight(1f))
                StatBox("ARTICLES VIEWED", "${report.summary.articleSlidesViewed}", Modifier.weight(1f))
            }
        }

        HorizontalDivider(color = palette.border)

        if (report.weakItemIds.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = palette.ok.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, palette.ok.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = palette.ok, modifier = Modifier.size(20.dp))
                    Text(
                        text = "All graded questions & flashcards look solid — no review flags raised.",
                        color = palette.ok,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = palette.bad.copy(alpha = 0.07f),
                border = BorderStroke(1.dp, palette.bad.copy(alpha = 0.30f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = palette.bad, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Needs Review (${report.weakItemIds.size})",
                            color = palette.bad,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        report.weakItemIds.forEach { weakId ->
                            val entry = slidesById[weakId]
                            val chipLabel = if (entry != null) "#${entry.slideIndex} ${entry.kind.uppercase()} • $weakId" else weakId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.surface,
                                border = BorderStroke(1.dp, palette.bad.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = chipLabel,
                                    color = palette.bad,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Item-by-Item Breakdown (${report.slides.size})",
                color = palette.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            report.slides.forEach { entry ->
                SlideBreakdownItemCard(entry = entry)
            }
        }

        HorizontalDivider(color = palette.border)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Export & Share Report", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = onExportJson,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export .analysis.json", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onExportMarkdown,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Text("Export .md", color = palette.text, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onShareMarkdown,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = palette.text, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", color = palette.text, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SlideBreakdownItemCard(entry: SlideAnalysisEntry) {
    val palette = LocalMeloberryColors.current

    val isGraded = entry.kind in BuildAnalysisReportUseCase.GRADED_QUESTION_KINDS
    val (statusLabel, statusColor) = when {
        isGraded -> if (entry.isCorrect == true) "Correct" to palette.ok else "Missed" to palette.bad
        entry.kind == "flashcard" -> when (entry.flashcardRating) {
            "known" -> "Known" to palette.ok
            "needsReview" -> "Review" to palette.bad
            else -> "Unrated" to palette.muted
        }
        else -> if (entry.visitedCount > 0) "Viewed" to palette.accent else "Skipped" to palette.muted
    }

    val activityMeta = when {
        isGraded -> "Checks: ${entry.checkAttempts ?: 0}  •  Visits: ${entry.visitedCount}"
        entry.kind == "flashcard" -> "Flips: ${entry.flipCount ?: 0}  •  Visits: ${entry.visitedCount}"
        else -> "Visits: ${entry.visitedCount}"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = palette.background,
        border = BorderStroke(1.dp, palette.border)
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
                    .background(palette.accentSoft, RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("#${entry.slideIndex}", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.kind.uppercase(), color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(activityMeta, color = palette.muted, fontSize = 12.sp)
                Text(
                    text = entry.id,
                    color = palette.muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = RoundedCornerShape(999.dp), color = statusColor.copy(alpha = 0.14f)) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1fs", entry.timeSpentMs / 1000.0),
                    color = palette.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = palette.background,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                text = value,
                color = palette.accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
