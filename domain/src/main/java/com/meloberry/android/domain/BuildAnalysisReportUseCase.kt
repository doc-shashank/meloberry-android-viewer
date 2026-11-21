package com.meloberry.android.domain

import com.meloberry.android.core.model.DefaultDispatcher
import com.meloberry.android.core.model.SessionAnalysisReport
import com.meloberry.android.core.model.SessionAnalysisSummary
import com.meloberry.android.core.model.SlideAnalysisEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.roundToInt

data class BuiltAnalysisOutput(
    val report: SessionAnalysisReport,
    val markdown: String
)

class BuildAnalysisReportUseCase @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(
        workspaceId: String,
        title: String,
        startedAtEpochMs: Long,
        completedAtEpochMs: Long,
        slideEntries: List<SlideAnalysisEntry>
    ): BuiltAnalysisOutput = withContext(defaultDispatcher) {
        val mcqs = slideEntries.filter { it.kind == "mcq" }
        val cards = slideEntries.filter { it.kind == "flashcard" }
        val articles = slideEntries.filter { it.kind == "article" }

        val mcqCorrectFinal = mcqs.count { it.isCorrect == true }
        val mcqCorrectFirstTry = mcqs.count { it.firstTryCorrect == true }
        val mcqAccuracyPercent = if (mcqs.isEmpty()) {
            100.0
        } else {
            ((mcqCorrectFinal.toDouble() / mcqs.size) * 1000.0).roundToInt() / 10.0
        }

        val flashcardKnown = cards.count { it.flashcardRating == "known" }
        val flashcardNeedsReview = cards.count { it.flashcardRating == "needsReview" }
        val articleSlidesViewed = articles.count { it.visitedCount > 0 }

        val weakItemIds = buildList {
            mcqs.filter { it.isCorrect != true }.forEach { add(it.id) }
            cards.filter { it.flashcardRating == "needsReview" }.forEach { add(it.id) }
        }

        val report = SessionAnalysisReport(
            schemaVersion = "1.0",
            workspaceId = workspaceId,
            title = title,
            startedAtUtc = formatUtcIso(startedAtEpochMs),
            completedAtUtc = formatUtcIso(completedAtEpochMs),
            totalDurationMs = (completedAtEpochMs - startedAtEpochMs).coerceAtLeast(0L),
            summary = SessionAnalysisSummary(
                totalSlides = slideEntries.size,
                mcqTotal = mcqs.size,
                mcqCorrectFirstTry = mcqCorrectFirstTry,
                mcqCorrectFinal = mcqCorrectFinal,
                mcqAccuracyPercent = mcqAccuracyPercent,
                flashcardTotal = cards.size,
                flashcardKnown = flashcardKnown,
                flashcardNeedsReview = flashcardNeedsReview,
                articleSlidesViewed = articleSlidesViewed
            ),
            slides = slideEntries,
            weakItemIds = weakItemIds
        )

        BuiltAnalysisOutput(
            report = report,
            markdown = formatMarkdownReport(report)
        )
    }

    fun formatMarkdownReport(report: SessionAnalysisReport): String {
        val sb = StringBuilder()
        val durationSec = String.format(Locale.US, "%.1f", report.totalDurationMs / 1000.0)
        val accuracyStr = String.format(Locale.US, "%.1f", report.summary.mcqAccuracyPercent)

        sb.appendLine("# Meloberry Session Analysis — ${report.workspaceId}")
        sb.appendLine()
        sb.appendLine("- **Title:** ${report.title}")
        sb.appendLine("- **Started (UTC):** ${report.startedAtUtc}")
        sb.appendLine("- **Completed (UTC):** ${report.completedAtUtc}")
        sb.appendLine("- **Total Duration:** ${durationSec}s")
        sb.appendLine()
        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine("- **Total Content Slides:** ${report.summary.totalSlides}")
        sb.appendLine(
            "- **MCQs:** ${report.summary.mcqCorrectFinal} / ${report.summary.mcqTotal} correct ($accuracyStr% | First-try: ${report.summary.mcqCorrectFirstTry})"
        )
        sb.appendLine(
            "- **Flashcards:** ${report.summary.flashcardKnown} known, ${report.summary.flashcardNeedsReview} needs review (of ${report.summary.flashcardTotal})"
        )
        sb.appendLine("- **Articles Viewed:** ${report.summary.articleSlidesViewed}")
        sb.appendLine()

        if (report.weakItemIds.isNotEmpty()) {
            sb.appendLine("## Weak / Review Items")
            sb.appendLine()
            report.weakItemIds.forEach { id -> sb.appendLine("- `$id`") }
            sb.appendLine()
        }

        sb.appendLine("## Per-Slide Breakdown")
        sb.appendLine()
        sb.appendLine("| # | ID | Kind | Time (s) | Visits | Result / Details |")
        sb.appendLine("| :---: | :--- | :--- | ---: | ---: | :--- |")
        report.slides.forEach { s ->
            val secs = String.format(Locale.US, "%.1f", s.timeSpentMs / 1000.0)
            val details = when (s.kind) {
                "mcq" -> "Correct: ${if (s.isCorrect == true) "Yes" else "No"}, Checks: ${s.checkAttempts ?: 0}, Selected: [${(s.selectedOptionIndices ?: emptyList()).joinToString(",")}]"
                "flashcard" -> "Flips: ${s.flipCount ?: 0}, Rating: ${s.flashcardRating ?: "unrated"}"
                else -> "Viewed"
            }
            sb.appendLine("| ${s.slideIndex} | `${s.id}` | ${s.kind} | $secs | ${s.visitedCount} | $details |")
        }

        return sb.toString().trimEnd()
    }

    private fun formatUtcIso(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }
}
