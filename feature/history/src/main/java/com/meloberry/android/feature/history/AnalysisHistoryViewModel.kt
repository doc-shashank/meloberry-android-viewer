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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalysisHistoryViewModel @Inject constructor(
    private val analysisRepository: AnalysisRepository,
    private val aggregateUseCase: AggregateAnalysisHistoryUseCase
) : ViewModel() {

    private val expandedRunId = MutableStateFlow<String?>(null)
    private val pendingExport = MutableStateFlow<PendingExportPayload?>(null)
    private val userMessages = MutableStateFlow<List<UserMessage>>(emptyList())

    private val runsWithStats = analysisRepository.observeAllRuns()
        .map { runs -> runs to (if (runs.isEmpty()) null else aggregateUseCase(runs)) }

    val uiState: StateFlow<AnalysisHistoryUiState> = combine(
        runsWithStats,
        expandedRunId,
        pendingExport,
        userMessages
    ) { (runs, stats), expanded, exportPayload, messages ->
        AnalysisHistoryUiState(
            isLoading = false,
            stats = stats,
            runs = runs,
            expandedRunId = expanded,
            pendingExport = exportPayload,
            userMessages = messages
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalysisHistoryUiState(isLoading = true)
    )

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

    fun onSaveExportToUri(uriString: String, content: String) {
        viewModelScope.launch {
            analysisRepository.writeExportToUri(uriString, content)
                .onSuccess { userMessages.update { it.appendMessage("Exported .analysis.json successfully.") } }
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
