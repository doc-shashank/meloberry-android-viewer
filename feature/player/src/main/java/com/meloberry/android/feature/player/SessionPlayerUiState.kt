package com.meloberry.android.feature.player

import com.meloberry.android.core.model.PendingExportPayload
import com.meloberry.android.core.model.SessionAnalysisReport
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.model.ThemeMode
import com.meloberry.android.core.model.UserMessage

data class McqSlideRuntimeState(
    val selectedIndices: Set<Int> = emptySet(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null
)

data class FlashcardSlideRuntimeState(
    val isFlipped: Boolean = false,
    val flipCount: Int = 0,
    val rating: String? = null
)

data class SessionPlayerUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val workspaceId: String = "",
    val title: String = "",
    val description: String = "",
    val themeMode: ThemeMode = ThemeMode.BERRY,
    val slideTransitionMs: Int = 380,
    val slides: List<SlideUiModel> = emptyList(),
    val leafSlideCount: Int = 0,
    val hasStartedSession: Boolean = false,
    val currentSlideIndex: Int = 0,
    val activeQuizQuestionIndex: Map<String, Int> = emptyMap(),
    val isFinished: Boolean = false,
    val totalMcqCount: Int = 0,
    val currentMcqScore: Int = 0,
    val mcqStates: Map<String, McqSlideRuntimeState> = emptyMap(),
    val flashcardStates: Map<String, FlashcardSlideRuntimeState> = emptyMap(),
    val completedReport: SessionAnalysisReport? = null,
    val completedMarkdown: String? = null,
    val pendingExport: PendingExportPayload? = null,
    val pendingShareText: String? = null,
    val userMessages: List<UserMessage> = emptyList()
)
