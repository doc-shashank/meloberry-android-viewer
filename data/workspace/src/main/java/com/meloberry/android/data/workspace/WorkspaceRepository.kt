package com.meloberry.android.data.workspace

import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.WorkspaceSummary
import kotlinx.coroutines.flow.Flow

sealed interface WorkspaceLoadResult {
    data object NotFound : WorkspaceLoadResult
    data class Loaded(val workspace: StudyWorkspace) : WorkspaceLoadResult
    data class Corrupt(val workspaceId: String, val message: String) : WorkspaceLoadResult
}

interface WorkspaceRepository {
    val workspacesStream: Flow<List<WorkspaceSummary>>
    fun observeWorkspace(id: String): Flow<WorkspaceLoadResult>
    suspend fun importPackageFromUri(uriString: String): Result<WorkspaceSummary>
    suspend fun ensureSampleWorkspaceSeeded()
    suspend fun deleteWorkspace(id: String)
}
