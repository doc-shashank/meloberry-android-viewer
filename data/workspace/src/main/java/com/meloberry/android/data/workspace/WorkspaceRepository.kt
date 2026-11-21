package com.meloberry.android.data.workspace

import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.WorkspaceSummary
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    val workspacesStream: Flow<List<WorkspaceSummary>>
    fun observeWorkspace(id: String): Flow<StudyWorkspace?>
    suspend fun importPackageFromUri(uriString: String): Result<WorkspaceSummary>
    suspend fun ensureSampleWorkspaceSeeded()
    suspend fun deleteWorkspace(id: String)
}
