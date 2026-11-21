package com.meloberry.android.data.workspace.repository

import com.meloberry.android.core.database.dao.AnalysisRunDao
import com.meloberry.android.core.database.dao.WorkspaceDao
import com.meloberry.android.core.database.datastore.PreferencesDataStoreSource
import com.meloberry.android.core.database.entity.WorkspaceEntity
import com.meloberry.android.core.model.ApplicationScope
import com.meloberry.android.core.model.IoDispatcher
import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.WorkspaceKind
import com.meloberry.android.core.model.WorkspaceSummary
import com.meloberry.android.data.workspace.WorkspaceRepository
import com.meloberry.android.data.workspace.source.BundledSampleDataSource
import com.meloberry.android.data.workspace.source.DocumentStreamDataSource
import com.meloberry.android.data.workspace.source.MbypkgArchiveDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorkspaceDataModule {
    @Binds
    abstract fun bindWorkspaceRepository(impl: DefaultWorkspaceRepository): WorkspaceRepository
}

@Singleton
internal class DefaultWorkspaceRepository @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val analysisRunDao: AnalysisRunDao,
    private val preferencesSource: PreferencesDataStoreSource,
    private val documentStreamDataSource: DocumentStreamDataSource,
    private val archiveDataSource: MbypkgArchiveDataSource,
    private val bundledSampleDataSource: BundledSampleDataSource,
    @ApplicationScope private val externalScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WorkspaceRepository {

    private val writeMutex = Mutex()

    override val workspacesStream: Flow<List<WorkspaceSummary>> = workspaceDao.observeAll()
        .map { entities -> entities.map { it.toSummary() } }
        .flowOn(ioDispatcher)

    override fun observeWorkspace(id: String): Flow<StudyWorkspace?> = workspaceDao.observeById(id)
        .map { entity -> entity?.let { archiveDataSource.deserializeEntity(it) } }
        .flowOn(ioDispatcher)

    override suspend fun importPackageFromUri(uriString: String): Result<WorkspaceSummary> =
        externalScope.async(ioDispatcher) {
            writeMutex.withLock {
                runCatching {
                    val stream = documentStreamDataSource.openInputStream(uriString)
                        ?: error("Unable to open selected package.")
                    stream.use { input ->
                        val extracted = archiveDataSource.extractAndParse(input) { workspaceId ->
                            documentStreamDataSource.getWorkspaceDir(workspaceId)
                        }
                        workspaceDao.upsert(extracted.entity)
                        extracted.entity.toSummary()
                    }
                }
            }
        }.await()

    override suspend fun ensureSampleWorkspaceSeeded() {
        externalScope.async(ioDispatcher) {
            writeMutex.withLock {
                val alreadySeeded = preferencesSource.sampleSeeded.first()
                if (!alreadySeeded && workspaceDao.count() == 0) {
                    val bytes = bundledSampleDataSource.buildSampleMbypkgBytes()
                    val extracted = archiveDataSource.extractAndParse(ByteArrayInputStream(bytes)) { id ->
                        documentStreamDataSource.getWorkspaceDir(id)
                    }
                    workspaceDao.upsert(extracted.entity)
                    preferencesSource.setSampleSeeded(true)
                }
            }
        }.await()
    }

    override suspend fun deleteWorkspace(id: String) {
        externalScope.async(ioDispatcher) {
            writeMutex.withLock {
                workspaceDao.deleteById(id)
                analysisRunDao.deleteByWorkspaceId(id)
                val dir = documentStreamDataSource.getWorkspaceDir(id)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
                documentStreamDataSource.deleteWorkspaceReports(id)
            }
        }.await()
    }

    private fun WorkspaceEntity.toSummary(): WorkspaceSummary = WorkspaceSummary(
        id = id,
        title = title,
        description = description,
        kind = if (kind.equals("session", ignoreCase = true)) WorkspaceKind.SESSION else WorkspaceKind.QUIZ,
        subType = subType,
        itemCount = itemCount,
        importedAtEpochMs = importedAtEpochMs
    )
}
