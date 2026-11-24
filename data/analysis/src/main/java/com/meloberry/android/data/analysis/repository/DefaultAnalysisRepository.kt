package com.meloberry.android.data.analysis.repository

import com.meloberry.android.core.database.dao.AnalysisRunDao
import com.meloberry.android.core.database.entity.AnalysisRunEntity
import com.meloberry.android.core.model.ApplicationScope
import com.meloberry.android.core.model.IoDispatcher
import com.meloberry.android.core.model.SavedAnalysisRun
import com.meloberry.android.core.model.SessionAnalysisReport
import com.meloberry.android.data.analysis.AnalysisRepository
import com.meloberry.android.data.analysis.source.AnalysisFileDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AnalysisDataModule {
    @Binds
    abstract fun bindAnalysisRepository(impl: DefaultAnalysisRepository): AnalysisRepository
}

@Singleton
internal class DefaultAnalysisRepository @Inject constructor(
    private val analysisRunDao: AnalysisRunDao,
    private val fileDataSource: AnalysisFileDataSource,
    @ApplicationScope private val externalScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AnalysisRepository {

    private val writeMutex = Mutex()

    override fun observeAllRuns(): Flow<List<SavedAnalysisRun>> = analysisRunDao.observeAll()
        .map { entities -> entities.map { it.toSavedRun() } }
        .flowOn(ioDispatcher)

    override suspend fun saveCompletedRun(
        report: SessionAnalysisReport,
        markdown: String,
        existingRunId: String?
    ): SavedAnalysisRun = externalScope.async(ioDispatcher) {
        writeMutex.withLock {
            val nowMs = System.currentTimeMillis()
            val runId = existingRunId?.takeIf { it.isNotBlank() } ?: run {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(nowMs))
                "${report.workspaceId}_${stamp}_${UUID.randomUUID().toString().take(4)}"
            }
            val reportJson = fileDataSource.encodeReport(report)

            val entity = AnalysisRunEntity(
                runId = runId,
                workspaceId = report.workspaceId,
                title = report.title,
                completedAtUtc = report.completedAtUtc,
                totalDurationMs = report.totalDurationMs,
                mcqAccuracyPercent = report.summary.mcqAccuracyPercent,
                mcqCorrectFinal = report.summary.mcqCorrectFinal,
                mcqTotal = report.summary.mcqTotal,
                flashcardKnown = report.summary.flashcardKnown,
                flashcardTotal = report.summary.flashcardTotal,
                reportJson = reportJson,
                markdown = markdown,
                savedAtEpochMs = nowMs
            )

            analysisRunDao.insert(entity)
            fileDataSource.saveLocalFiles(runId, report.workspaceId, reportJson, markdown)

            SavedAnalysisRun(
                runId = runId,
                report = report,
                markdown = markdown,
                savedAtEpochMs = nowMs
            )
        }
    }.await()

    override suspend fun serializeReportJson(report: SessionAnalysisReport): String =
        fileDataSource.encodeReport(report)

    override suspend fun writeExportToUri(uriString: String, content: String): Result<Unit> =
        externalScope.async(ioDispatcher) {
            runCatching {
                fileDataSource.writeToUri(uriString, content)
            }
        }.await()

    override suspend fun deleteRun(runId: String) {
        externalScope.async(ioDispatcher) {
            writeMutex.withLock {
                val existing = analysisRunDao.getById(runId)
                analysisRunDao.deleteById(runId)
                if (existing != null) {
                    fileDataSource.deleteLocalRunFiles(existing.workspaceId, runId)
                }
            }
        }.await()
    }

    private suspend fun AnalysisRunEntity.toSavedRun(): SavedAnalysisRun = SavedAnalysisRun(
        runId = runId,
        report = fileDataSource.decodeReport(reportJson),
        markdown = markdown,
        savedAtEpochMs = savedAtEpochMs
    )
}
