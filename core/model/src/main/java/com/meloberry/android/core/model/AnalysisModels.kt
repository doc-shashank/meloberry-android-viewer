package com.meloberry.android.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SessionAnalysisSummary(
    val totalSlides: Int,
    val mcqTotal: Int,
    val mcqCorrectFirstTry: Int,
    val mcqCorrectFinal: Int,
    val mcqAccuracyPercent: Double,
    val flashcardTotal: Int,
    val flashcardKnown: Int,
    val flashcardNeedsReview: Int,
    val articleSlidesViewed: Int
)

@Serializable
data class SlideAnalysisEntry(
    val slideIndex: Int,
    val id: String,
    val kind: String,
    val timeSpentMs: Long,
    val visitedCount: Int,
    val checkAttempts: Int? = null,
    val selectedOptionIndices: List<Int>? = null,
    val correctOptionIndices: List<Int>? = null,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null,
    val flipCount: Int? = null,
    val flashcardRating: String? = null
)

@Serializable
data class SessionAnalysisReport(
    val schemaVersion: String,
    val workspaceId: String,
    val title: String,
    val startedAtUtc: String,
    val completedAtUtc: String,
    val totalDurationMs: Long,
    val summary: SessionAnalysisSummary,
    val slides: List<SlideAnalysisEntry>,
    val weakItemIds: List<String>
)

data class SavedAnalysisRun(
    val runId: String,
    val report: SessionAnalysisReport,
    val markdown: String,
    val savedAtEpochMs: Long
)

data class RecurringWeakItem(
    val itemId: String,
    val flaggedCount: Int,
    val totalRuns: Int
)

data class AggregatedWorkspaceStats(
    val totalRuns: Int,
    val averageAccuracyPercent: Double,
    val averageDurationSec: Double,
    val recurringWeakItems: List<RecurringWeakItem>,
    val gradedRunCount: Int = totalRuns
)

data class UserMessage(
    val id: Long,
    val text: String
)

fun List<UserMessage>.appendMessage(text: String): List<UserMessage> =
    this + UserMessage(id = UUID.randomUUID().mostSignificantBits, text = text)

fun List<UserMessage>.dismissMessage(id: Long): List<UserMessage> =
    filterNot { it.id == id }

data class PendingExportPayload(
    val suggestedFileName: String,
    val mimeType: String,
    val content: String
)
