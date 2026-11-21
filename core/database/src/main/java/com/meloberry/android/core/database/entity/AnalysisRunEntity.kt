package com.meloberry.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis_runs")
data class AnalysisRunEntity(
    @PrimaryKey val runId: String,
    val workspaceId: String,
    val title: String,
    val completedAtUtc: String,
    val totalDurationMs: Long,
    val mcqAccuracyPercent: Double,
    val mcqCorrectFinal: Int,
    val mcqTotal: Int,
    val flashcardKnown: Int,
    val flashcardTotal: Int,
    val reportJson: String,
    val markdown: String,
    val savedAtEpochMs: Long
)
