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

data class FillBlankSlideRuntimeState(
    val inputsBySlot: Map<Int, String> = emptyMap(),
    val overriddenSlotIndices: Set<Int> = emptySet(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null,
    val overriddenQuestion: Boolean = false,
    val overriddenCorrect: Boolean = false,
    val anyFuzzy: Boolean = false
)

data class AssertionReasonSlideRuntimeState(
    val selectedCode: String? = null,
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null
)

data class SpotErrorSlideRuntimeState(
    val selectedSpans: Set<Int> = emptySet(),
    val pickedNoError: Boolean = false,
    val fixesBySpan: Map<Int, String> = emptyMap(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null
)

data class DifferentiationSlideRuntimeState(
    val fillInputsByCell: Map<String, String> = emptyMap(),
    val selectedErrorCells: Set<String> = emptySet(),
    val pickedNoError: Boolean = false,
    val rearrangeRows: List<List<String>> = emptyList(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null,
    val overriddenCorrect: Boolean = false,
    val anyFuzzy: Boolean = false
)

data class MechanismSlideRuntimeState(
    val bankStepIds: List<Int> = emptyList(),
    val placedStepIds: List<Int> = emptyList(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null
)

data class RelabelImageSlideRuntimeState(
    val revealedBoxIndices: Set<Int> = emptySet(),
    val assignedLabelsByBox: Map<Int, String> = emptyMap(),
    val recallInputsByBox: Map<Int, String> = emptyMap(),
    val rating: String? = null,
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null,
    val overriddenCorrect: Boolean = false,
    val anyFuzzy: Boolean = false
)

data class SessionPlayerUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val loadError: String? = null,
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
    val fillBlankStates: Map<String, FillBlankSlideRuntimeState> = emptyMap(),
    val assertionReasonStates: Map<String, AssertionReasonSlideRuntimeState> = emptyMap(),
    val spotErrorStates: Map<String, SpotErrorSlideRuntimeState> = emptyMap(),
    val differentiationStates: Map<String, DifferentiationSlideRuntimeState> = emptyMap(),
    val mechanismStates: Map<String, MechanismSlideRuntimeState> = emptyMap(),
    val relabelImageStates: Map<String, RelabelImageSlideRuntimeState> = emptyMap(),
    val completedReport: SessionAnalysisReport? = null,
    val completedMarkdown: String? = null,
    val pendingExport: PendingExportPayload? = null,
    val pendingShareText: String? = null,
    val userMessages: List<UserMessage> = emptyList()
)
