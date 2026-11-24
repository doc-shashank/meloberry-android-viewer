package com.meloberry.android.domain

import com.meloberry.android.core.model.AggregatedWorkspaceStats
import com.meloberry.android.core.model.DefaultDispatcher
import com.meloberry.android.core.model.RecurringWeakItem
import com.meloberry.android.core.model.SavedAnalysisRun
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AggregateAnalysisHistoryUseCase @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(runs: List<SavedAnalysisRun>): AggregatedWorkspaceStats =
        withContext(defaultDispatcher) {
            if (runs.isEmpty()) {
                return@withContext AggregatedWorkspaceStats(
                    totalRuns = 0,
                    averageAccuracyPercent = 0.0,
                    averageDurationSec = 0.0,
                    recurringWeakItems = emptyList(),
                    gradedRunCount = 0
                )
            }

            val gradedRuns = runs.filter { it.report.summary.mcqTotal > 0 }
            val avgAcc = if (gradedRuns.isEmpty()) {
                0.0
            } else {
                gradedRuns.map { it.report.summary.mcqAccuracyPercent }.average()
            }
            val avgDurSec = runs.map { it.report.totalDurationMs / 1000.0 }.average()

            val weakCounts = LinkedHashMap<String, Int>()
            for (run in runs) {
                for (weakId in run.report.weakItemIds.distinct()) {
                    weakCounts[weakId] = (weakCounts[weakId] ?: 0) + 1
                }
            }

            val recurring = weakCounts.entries
                .sortedByDescending { it.value }
                .map { (id, count) ->
                    RecurringWeakItem(
                        itemId = id,
                        flaggedCount = count,
                        totalRuns = runs.size
                    )
                }

            AggregatedWorkspaceStats(
                totalRuns = runs.size,
                averageAccuracyPercent = avgAcc,
                averageDurationSec = avgDurSec,
                recurringWeakItems = recurring,
                gradedRunCount = gradedRuns.size
            )
        }
}
