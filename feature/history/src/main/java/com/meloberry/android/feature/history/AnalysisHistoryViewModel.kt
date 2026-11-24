package com.meloberry.android.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meloberry.android.core.model.PendingExportPayload
import com.meloberry.android.core.model.SavedAnalysisRun
import com.meloberry.android.core.model.UserMessage
import com.meloberry.android.core.model.appendMessage
import com.meloberry.android.core.model.dismissMessage
import com.meloberry.android.data.analysis.AnalysisRepository
import com.meloberry.android.domain.AggregateAnalysisHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkspaceFilterOption(
    val workspaceId: String,
    val label: String
)

@HiltViewModel
class AnalysisHistoryViewModel @Inject constructor(
    private val analysisRepository: AnalysisRepository,
    private val aggregateUseCase: AggregateAnalysisHistoryUseCase
) : ViewModel() {

    private val selectedWorkspaceId = MutableStateFlow<String?>(null)
    private val expandedRunId = MutableStateFlow<String?>(null)
    private val pendingExport = MutableStateFlow<PendingExportPayload?>(null)
    private val pendingShareText = MutableStateFlow<String?>(null)
    private val userMessages = MutableStateFlow<List<UserMessage>>(emptyList())

    private val filteredRunsFlow = combine(
        analysisRepository.observeAllRuns(),
        selectedWorkspaceId
    ) { allRuns, selectedId ->
        val options = allRuns
            .distinctBy { it.report.workspaceId }
            .map { run ->
                WorkspaceFilterOption(
                    workspaceId = run.report.workspaceId,
                    label = run.report.title.ifBlank { run.report.workspaceId }
                )
            }
        val effectiveFilter = selectedId?.takeIf { id -> options.any { it.workspaceId == id } }
        val filtered = if (effectiveFilter == null) {
            allRuns
        } else {
            allRuns.filter { it.report.workspaceId == effectiveFilter }
        }
        val stats = if (filtered.isEmpty()) null else aggregateUseCase(filtered)
        Triple(options, effectiveFilter, Triple(filtered, stats, allRuns.isEmpty()))
    }

    val uiState: StateFlow<AnalysisHistoryUiState> = combine(
        filteredRunsFlow,
        expandedRunId,
        pendingExport,
        pendingShareText,
        userMessages
    ) { (options, activeFilter, runTriple), expanded, exportPayload, shareText, messages ->
        val (runs, stats, _) = runTriple
        AnalysisHistoryUiState(
            isLoading = false,
            availableWorkspaces = options,
            selectedWorkspaceId = activeFilter,
            stats = stats,
            runs = runs,
            expandedRunId = expanded,
            pendingExport = exportPayload,
            pendingShareText = shareText,
            userMessages = messages
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalysisHistoryUiState(isLoading = true)
    )

    fun onSelectWorkspaceFilter(workspaceId: String?) {
        selectedWorkspaceId.value = workspaceId
    }

    fun onToggleExpand(runId: String) {
        expandedRunId.update { cur -> if (cur == runId) null else runId }
    }

    fun onExportRunJson(run: SavedAnalysisRun) {
        viewModelScope.launch {
            val encoded = analysisRepository.serializeReportJson(run.report)
            pendingExport.value = PendingExportPayload(
                suggestedFileName = "${run.runId}.analysis.json",
                mimeType = "application/json",
                content = encoded
            )
        }
    }

    fun onExportRunMarkdown(run: SavedAnalysisRun) {
        pendingExport.value = PendingExportPayload(
            suggestedFileName = "${run.runId}.analysis.md",
            mimeType = "text/plain",
            content = run.markdown
        )
    }

    fun onShareRunMarkdown(run: SavedAnalysisRun) {
        pendingShareText.value = run.markdown
    }

    fun onShareConsumed() {
        pendingShareText.value = null
    }

    fun onSaveExportToUri(uriString: String, content: String) {
        viewModelScope.launch {
            analysisRepository.writeExportToUri(uriString, content)
                .onSuccess { userMessages.update { it.appendMessage("Exported report successfully.") } }
                .onFailure { err -> userMessages.update { it.appendMessage(err.message ?: "Failed to export file.") } }
        }
    }

    fun onExportConsumed() {
        pendingExport.value = null
    }

    fun onDeleteRun(runId: String) {
        viewModelScope.launch {
            runCatching { analysisRepository.deleteRun(runId) }
                .onSuccess { userMessages.update { it.appendMessage("Deleted run.") } }
                .onFailure { err -> userMessages.update { it.appendMessage(err.message ?: "Delete failed.") } }
        }
    }

    fun onMessageShown(messageId: Long) {
        userMessages.update { it.dismissMessage(messageId) }
    }
}
