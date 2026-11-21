package com.meloberry.android.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meloberry.android.core.model.AggregatedWorkspaceStats
import com.meloberry.android.core.model.PendingExportPayload
import com.meloberry.android.core.model.SavedAnalysisRun
import com.meloberry.android.core.model.UserMessage
import com.meloberry.android.core.ui.components.MeloberryPageScaffold
import com.meloberry.android.core.ui.components.PendingDocumentExportEffect
import com.meloberry.android.core.ui.markdown.MarkdownText
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import java.util.Locale

data class AnalysisHistoryUiState(
    val isLoading: Boolean = true,
    val stats: AggregatedWorkspaceStats? = null,
    val runs: List<SavedAnalysisRun> = emptyList(),
    val expandedRunId: String? = null,
    val pendingExport: PendingExportPayload? = null,
    val userMessages: List<UserMessage> = emptyList()
)

@Composable
fun AnalysisHistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: AnalysisHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PendingDocumentExportEffect(
        pendingExport = uiState.pendingExport,
        onExportConsumed = viewModel::onExportConsumed,
        onSaveExportToUri = viewModel::onSaveExportToUri
    )

    AnalysisHistoryScreen(
        uiState = uiState,
        onToggleExpand = viewModel::onToggleExpand,
        onExportJson = viewModel::onExportRunJson,
        onDeleteRun = viewModel::onDeleteRun,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier
    )
}

@Composable
fun AnalysisHistoryScreen(
    uiState: AnalysisHistoryUiState,
    onToggleExpand: (String) -> Unit,
    onExportJson: (SavedAnalysisRun) -> Unit,
    onDeleteRun: (String) -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    MeloberryPageScaffold(
        title = "Analysis History",
        subtitle = "Completed Study Sessions & Quizzes saved offline. Export any run to .analysis.json for mby analyze.",
        isLoading = uiState.isLoading,
        isEmpty = uiState.runs.isEmpty(),
        emptyIcon = Icons.Default.Analytics,
        emptyTitle = "No completed runs yet",
        emptySubtitle = "Complete a session in the Player and click 'Finish & Analyze' to record performance analytics.",
        userMessages = uiState.userMessages,
        onMessageShown = onMessageShown,
        modifier = modifier
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.stats?.let { stats ->
                item(key = "aggregate_stats") {
                    AggregateStatsCard(stats = stats)
                }
            }
            items(uiState.runs, key = { it.runId }) { run ->
                RunHistoryCard(
                    run = run,
                    isExpanded = uiState.expandedRunId == run.runId,
                    onToggleExpand = { onToggleExpand(run.runId) },
                    onExportJson = { onExportJson(run) },
                    onDelete = { onDeleteRun(run.runId) }
                )
            }
        }
    }
}

@Composable
private fun AggregateStatsCard(stats: AggregatedWorkspaceStats) {
    val palette = LocalMeloberryColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(palette.accentSoft, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "AGGREGATE (${stats.totalRuns} RUNS)",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Avg MCQ Accuracy", color = palette.muted, fontSize = 12.sp)
                    Text(
                        text = String.format(Locale.US, "%.1f%%", stats.averageAccuracyPercent),
                        color = palette.accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column {
                    Text("Avg Duration", color = palette.muted, fontSize = 12.sp)
                    Text(
                        text = String.format(Locale.US, "%.1fs", stats.averageDurationSec),
                        color = palette.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            if (stats.recurringWeakItems.isNotEmpty()) {
                HorizontalDivider(color = palette.border)
                Text(
                    text = "Recurring Weak Items",
                    color = palette.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                stats.recurringWeakItems.take(5).forEach { weak ->
                    Text(
                        text = "• ${weak.itemId} (${weak.flaggedCount}/${weak.totalRuns} runs)",
                        color = palette.bad,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RunHistoryCard(
    run: SavedAnalysisRun,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onExportJson: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalMeloberryColors.current
    val summary = run.report.summary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(16.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = run.report.workspaceId,
                        color = palette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "MCQ: ${summary.mcqCorrectFinal}/${summary.mcqTotal} (${String.format(Locale.US, "%.1f%%", summary.mcqAccuracyPercent)}) • Flashcards: ${summary.flashcardKnown}/${summary.flashcardTotal}",
                        color = palette.muted,
                        fontSize = 13.sp
                    )
                    Text(
                        text = run.report.completedAtUtc,
                        color = palette.muted,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete run",
                        tint = palette.muted
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = palette.border)
                MarkdownText(markdown = run.markdown, baseFontSizeSp = 13)
                OutlinedButton(
                    onClick = onExportJson,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = palette.accent)
                    Text("Export .analysis.json", color = palette.text)
                }
            }
        }
    }
}
