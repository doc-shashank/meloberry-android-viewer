package com.meloberry.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meloberry.android.core.model.UserMessage
import com.meloberry.android.core.model.appendMessage
import com.meloberry.android.core.model.dismissMessage
import com.meloberry.android.data.workspace.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val pendingOpenWorkspaceId = MutableStateFlow<String?>(null)
    private val userMessages = MutableStateFlow<List<UserMessage>>(emptyList())

    val uiState: StateFlow<LibraryUiState> = combine(
        workspaceRepository.workspacesStream,
        isImporting,
        pendingOpenWorkspaceId,
        userMessages
    ) { workspaces, importing, openId, messages ->
        LibraryUiState(
            isLoading = false,
            isImporting = importing,
            workspaces = workspaces,
            pendingOpenWorkspaceId = openId,
            userMessages = messages
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            runCatching { workspaceRepository.ensureSampleWorkspaceSeeded() }
                .onFailure { err ->
                    userMessages.update { it.appendMessage(err.message ?: "Failed to initialize sample session.") }
                }
        }
    }

    fun importPackage(uriString: String) {
        if (!isImporting.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                workspaceRepository.importPackageFromUri(uriString)
                    .onSuccess { summary ->
                        userMessages.update { it.appendMessage("Imported ${summary.id}") }
                        pendingOpenWorkspaceId.value = summary.id
                    }
                    .onFailure { err ->
                        userMessages.update { it.appendMessage(err.message ?: "Failed to import .mbypkg package.") }
                    }
            } finally {
                isImporting.value = false
            }
        }
    }

    fun deleteWorkspace(id: String) {
        viewModelScope.launch {
            runCatching { workspaceRepository.deleteWorkspace(id) }
                .onSuccess { userMessages.update { it.appendMessage("Removed workspace $id") } }
                .onFailure { err -> userMessages.update { it.appendMessage(err.message ?: "Delete failed.") } }
        }
    }

    fun onNavigationHandled() {
        pendingOpenWorkspaceId.value = null
    }

    fun onMessageShown(messageId: Long) {
        userMessages.update { it.dismissMessage(messageId) }
    }
}
