package com.meloberry.android.data.analysis

import com.meloberry.android.core.model.SavedAnalysisRun
import com.meloberry.android.core.model.SessionAnalysisReport
import kotlinx.coroutines.flow.Flow

interface AnalysisRepository {
    fun observeAllRuns(): Flow<List<SavedAnalysisRun>>
    suspend fun saveCompletedRun(report: SessionAnalysisReport, markdown: String): SavedAnalysisRun
    suspend fun serializeReportJson(report: SessionAnalysisReport): String
    suspend fun writeExportToUri(uriString: String, content: String): Result<Unit>
    suspend fun deleteRun(runId: String)
}
