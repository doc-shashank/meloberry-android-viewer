package com.meloberry.android.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meloberry.android.core.model.PendingExportPayload
import com.meloberry.android.core.model.SessionAnalysisReport
import com.meloberry.android.core.model.SlideAnalysisEntry
import com.meloberry.android.core.model.SlideDeck
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.model.UserMessage
import com.meloberry.android.core.model.appendMessage
import com.meloberry.android.core.model.dismissMessage
import com.meloberry.android.data.analysis.AnalysisRepository
import com.meloberry.android.data.workspace.WorkspaceLoadResult
import com.meloberry.android.data.workspace.WorkspaceRepository
import com.meloberry.android.domain.BuildAnalysisReportUseCase
import com.meloberry.android.domain.BuildSlideDeckUseCase
import com.meloberry.android.domain.DeterministicShuffler
import com.meloberry.android.domain.EvaluateAssertionReasonUseCase
import com.meloberry.android.domain.EvaluateDifferentiationUseCase
import com.meloberry.android.domain.EvaluateFillBlankUseCase
import com.meloberry.android.domain.EvaluateMcqAnswerUseCase
import com.meloberry.android.domain.EvaluateMechanismUseCase
import com.meloberry.android.domain.EvaluateRelabelImageUseCase
import com.meloberry.android.domain.EvaluateSpotErrorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class SerializableMcqState(
    val selectedIndices: List<Int> = emptyList(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null
)

@Serializable
private data class SerializableFlashcardState(
    val isFlipped: Boolean = false,
    val flipCount: Int = 0,
    val rating: String? = null
)

@Serializable
private data class SerializableFillBlankState(
    val inputsBySlot: Map<Int, String> = emptyMap(),
    val overriddenSlotIndices: List<Int> = emptyList(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null,
    val overriddenCorrect: Boolean = false,
    val anyFuzzy: Boolean = false
)

@Serializable
private data class SerializableAssertionReasonState(
    val selectedCode: String? = null,
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null
)

@Serializable
private data class SerializableSpotErrorState(
    val selectedSpans: List<Int> = emptyList(),
    val pickedNoError: Boolean = false,
    val fixesBySpan: Map<Int, String> = emptyMap(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null
)

@Serializable
private data class SerializableDifferentiationState(
    val fillInputsByCell: Map<String, String> = emptyMap(),
    val selectedErrorCells: List<String> = emptyList(),
    val pickedNoError: Boolean = false,
    val rearrangeRows: List<List<String>> = emptyList(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null,
    val overriddenCorrect: Boolean = false,
    val anyFuzzy: Boolean = false
)

@Serializable
private data class SerializableMechanismState(
    val bankStepIds: List<Int> = emptyList(),
    val placedStepIds: List<Int> = emptyList(),
    val isChecked: Boolean = false,
    val checkAttempts: Int = 0,
    val firstTryCorrect: Boolean? = null,
    val isCorrect: Boolean? = null
)

@Serializable
private data class SerializableRelabelImageState(
    val revealedBoxIndices: List<Int> = emptyList(),
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

@Serializable
private data class PlayerSessionSnapshot(
    val hasStartedSession: Boolean = false,
    val sessionStartedEpochMs: Long? = null,
    val currentSlideIndex: Int = 0,
    val activeQuizQuestionIndex: Map<String, Int> = emptyMap(),
    val isFinished: Boolean = false,
    val timeSpentBySlideId: Map<String, Long> = emptyMap(),
    val visitedBySlideId: Map<String, Int> = emptyMap(),
    val mcqStates: Map<String, SerializableMcqState> = emptyMap(),
    val flashcardStates: Map<String, SerializableFlashcardState> = emptyMap(),
    val fillBlankStates: Map<String, SerializableFillBlankState> = emptyMap(),
    val assertionReasonStates: Map<String, SerializableAssertionReasonState> = emptyMap(),
    val spotErrorStates: Map<String, SerializableSpotErrorState> = emptyMap(),
    val differentiationStates: Map<String, SerializableDifferentiationState> = emptyMap(),
    val mechanismStates: Map<String, SerializableMechanismState> = emptyMap(),
    val relabelImageStates: Map<String, SerializableRelabelImageState> = emptyMap(),
    val savedRunId: String? = null
)

private data class PlayerTelemetryState(
    val hasStartedSession: Boolean = false,
    val currentSlideIndex: Int = 0,
    val activeQuizQuestionIndex: Map<String, Int> = emptyMap(),
    val isFinished: Boolean = false,
    val timeSpentBySlideId: Map<String, Long> = emptyMap(),
    val visitedBySlideId: Map<String, Int> = emptyMap(),
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
    val userMessages: List<UserMessage> = emptyList(),
    val savedRunId: String? = null
)

private sealed interface DeckLoadResult {
    data object Loading : DeckLoadResult
    data object NotFound : DeckLoadResult
    data class Error(val message: String) : DeckLoadResult
    data class Loaded(val deck: SlideDeck) : DeckLoadResult
}

@HiltViewModel
class SessionPlayerViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    workspaceRepository: WorkspaceRepository,
    private val analysisRepository: AnalysisRepository,
    private val buildSlideDeckUseCase: BuildSlideDeckUseCase,
    private val evaluateMcqAnswerUseCase: EvaluateMcqAnswerUseCase,
    private val buildAnalysisReportUseCase: BuildAnalysisReportUseCase,
    private val evaluateFillBlankUseCase: EvaluateFillBlankUseCase = EvaluateFillBlankUseCase(),
    private val evaluateAssertionReasonUseCase: EvaluateAssertionReasonUseCase = EvaluateAssertionReasonUseCase(),
    private val evaluateSpotErrorUseCase: EvaluateSpotErrorUseCase = EvaluateSpotErrorUseCase(),
    private val evaluateDifferentiationUseCase: EvaluateDifferentiationUseCase = EvaluateDifferentiationUseCase(),
    private val evaluateMechanismUseCase: EvaluateMechanismUseCase = EvaluateMechanismUseCase(),
    private val evaluateRelabelImageUseCase: EvaluateRelabelImageUseCase = EvaluateRelabelImageUseCase(),
    private val nowMs: () -> Long = System::currentTimeMillis
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        workspaceRepository: WorkspaceRepository,
        analysisRepository: AnalysisRepository,
        buildSlideDeckUseCase: BuildSlideDeckUseCase,
        evaluateMcqAnswerUseCase: EvaluateMcqAnswerUseCase,
        buildAnalysisReportUseCase: BuildAnalysisReportUseCase,
        evaluateFillBlankUseCase: EvaluateFillBlankUseCase,
        evaluateAssertionReasonUseCase: EvaluateAssertionReasonUseCase,
        evaluateSpotErrorUseCase: EvaluateSpotErrorUseCase,
        evaluateDifferentiationUseCase: EvaluateDifferentiationUseCase,
        evaluateMechanismUseCase: EvaluateMechanismUseCase,
        evaluateRelabelImageUseCase: EvaluateRelabelImageUseCase
    ) : this(
        savedStateHandle = savedStateHandle,
        workspaceRepository = workspaceRepository,
        analysisRepository = analysisRepository,
        buildSlideDeckUseCase = buildSlideDeckUseCase,
        evaluateMcqAnswerUseCase = evaluateMcqAnswerUseCase,
        buildAnalysisReportUseCase = buildAnalysisReportUseCase,
        evaluateFillBlankUseCase = evaluateFillBlankUseCase,
        evaluateAssertionReasonUseCase = evaluateAssertionReasonUseCase,
        evaluateSpotErrorUseCase = evaluateSpotErrorUseCase,
        evaluateDifferentiationUseCase = evaluateDifferentiationUseCase,
        evaluateMechanismUseCase = evaluateMechanismUseCase,
        evaluateRelabelImageUseCase = evaluateRelabelImageUseCase,
        nowMs = System::currentTimeMillis
    )

    private val snapshotJson = Json { ignoreUnknownKeys = true }
    private val persistMutex = Mutex()
    private val workspaceId: String = savedStateHandle.get<String>("workspaceId").orEmpty()

    private val restoredSnapshot: PlayerSessionSnapshot? =
        savedStateHandle.get<String>(KEY_SESSION_SNAPSHOT)?.let { raw ->
            runCatching { snapshotJson.decodeFromString<PlayerSessionSnapshot>(raw) }.getOrNull()
        }

    private var sessionStartedEpochMs: Long = restoredSnapshot?.sessionStartedEpochMs ?: nowMs()
    private var activeSlideEnterEpochMs: Long? = null
    private var isTelemetryPaused: Boolean = false
    private var latestDeck: SlideDeck? = null

    private val telemetry = MutableStateFlow(
        if (restoredSnapshot != null) {
            PlayerTelemetryState(
                hasStartedSession = restoredSnapshot.hasStartedSession,
                currentSlideIndex = restoredSnapshot.currentSlideIndex,
                activeQuizQuestionIndex = restoredSnapshot.activeQuizQuestionIndex,
                isFinished = restoredSnapshot.isFinished,
                timeSpentBySlideId = restoredSnapshot.timeSpentBySlideId,
                visitedBySlideId = restoredSnapshot.visitedBySlideId,
                mcqStates = restoredSnapshot.mcqStates.mapValues { (_, s) ->
                    McqSlideRuntimeState(
                        selectedIndices = s.selectedIndices.toSet(),
                        isChecked = s.isChecked,
                        checkAttempts = s.checkAttempts,
                        firstTryCorrect = s.firstTryCorrect,
                        isCorrect = s.isCorrect
                    )
                },
                flashcardStates = restoredSnapshot.flashcardStates.mapValues { (_, s) ->
                    FlashcardSlideRuntimeState(
                        isFlipped = s.isFlipped,
                        flipCount = s.flipCount,
                        rating = s.rating
                    )
                },
                fillBlankStates = restoredSnapshot.fillBlankStates.mapValues { (_, s) ->
                    FillBlankSlideRuntimeState(
                        inputsBySlot = s.inputsBySlot,
                        overriddenSlotIndices = s.overriddenSlotIndices.toSet(),
                        isChecked = s.isChecked,
                        checkAttempts = s.checkAttempts,
                        firstTryCorrect = s.firstTryCorrect,
                        isCorrect = s.isCorrect,
                        overriddenCorrect = s.overriddenCorrect,
                        anyFuzzy = s.anyFuzzy
                    )
                },
                assertionReasonStates = restoredSnapshot.assertionReasonStates.mapValues { (_, s) ->
                    AssertionReasonSlideRuntimeState(
                        selectedCode = s.selectedCode,
                        isChecked = s.isChecked,
                        checkAttempts = s.checkAttempts,
                        firstTryCorrect = s.firstTryCorrect,
                        isCorrect = s.isCorrect
                    )
                },
                spotErrorStates = restoredSnapshot.spotErrorStates.mapValues { (_, s) ->
                    SpotErrorSlideRuntimeState(
                        selectedSpans = s.selectedSpans.toSet(),
                        pickedNoError = s.pickedNoError,
                        fixesBySpan = s.fixesBySpan,
                        isChecked = s.isChecked,
                        checkAttempts = s.checkAttempts,
                        firstTryCorrect = s.firstTryCorrect,
                        isCorrect = s.isCorrect
                    )
                },
                differentiationStates = restoredSnapshot.differentiationStates.mapValues { (_, s) ->
                    DifferentiationSlideRuntimeState(
                        fillInputsByCell = s.fillInputsByCell,
                        selectedErrorCells = s.selectedErrorCells.toSet(),
                        pickedNoError = s.pickedNoError,
                        rearrangeRows = s.rearrangeRows,
                        isChecked = s.isChecked,
                        checkAttempts = s.checkAttempts,
                        firstTryCorrect = s.firstTryCorrect,
                        isCorrect = s.isCorrect,
                        overriddenCorrect = s.overriddenCorrect,
                        anyFuzzy = s.anyFuzzy
                    )
                },
                mechanismStates = restoredSnapshot.mechanismStates.mapValues { (_, s) ->
                    MechanismSlideRuntimeState(
                        bankStepIds = s.bankStepIds,
                        placedStepIds = s.placedStepIds,
                        isChecked = s.isChecked,
                        checkAttempts = s.checkAttempts,
                        firstTryCorrect = s.firstTryCorrect,
                        isCorrect = s.isCorrect
                    )
                },
                relabelImageStates = restoredSnapshot.relabelImageStates.mapValues { (_, s) ->
                    RelabelImageSlideRuntimeState(
                        revealedBoxIndices = s.revealedBoxIndices.toSet(),
                        assignedLabelsByBox = s.assignedLabelsByBox,
                        recallInputsByBox = s.recallInputsByBox,
                        rating = s.rating,
                        isChecked = s.isChecked,
                        checkAttempts = s.checkAttempts,
                        firstTryCorrect = s.firstTryCorrect,
                        isCorrect = s.isCorrect,
                        overriddenCorrect = s.overriddenCorrect,
                        anyFuzzy = s.anyFuzzy
                    )
                },
                savedRunId = restoredSnapshot.savedRunId
            )
        } else {
            PlayerTelemetryState(
                hasStartedSession = savedStateHandle.get<Boolean>(KEY_HAS_STARTED_SESSION) ?: false,
                currentSlideIndex = savedStateHandle.get<Int>(KEY_CURRENT_SLIDE_INDEX) ?: 0
            )
        }
    )

    private val deckFlow = workspaceRepository.observeWorkspace(workspaceId)
        .map { loadResult ->
            when (loadResult) {
                WorkspaceLoadResult.NotFound -> DeckLoadResult.NotFound
                is WorkspaceLoadResult.Corrupt -> DeckLoadResult.Error(loadResult.message)
                is WorkspaceLoadResult.Loaded -> {
                    val built = buildSlideDeckUseCase(loadResult.workspace)
                    latestDeck = built
                    initializeVisitsIfNeeded(built)
                    if (telemetry.value.isFinished && telemetry.value.completedReport == null) {
                        generateReport(persistToHistory = false)
                    }
                    DeckLoadResult.Loaded(built)
                }
            }
        }

    val uiState: StateFlow<SessionPlayerUiState> = combine(deckFlow, telemetry) { load, state ->
        when (load) {
            DeckLoadResult.Loading -> SessionPlayerUiState(isLoading = true)
            DeckLoadResult.NotFound -> SessionPlayerUiState(
                isLoading = false,
                notFound = true,
                workspaceId = workspaceId
            )
            is DeckLoadResult.Error -> SessionPlayerUiState(
                isLoading = false,
                loadError = load.message,
                workspaceId = workspaceId
            )
            is DeckLoadResult.Loaded -> {
                val deck = load.deck
                val gradedSlides = deck.allQuestionSlides().filter { it !is SlideUiModel.FlashcardSlide }
                val gradedCorrect = gradedSlides.count { slide ->
                    when (slide) {
                        is SlideUiModel.McqSlide -> state.mcqStates[slide.id]?.isCorrect == true
                        is SlideUiModel.FillBlankSlide -> state.fillBlankStates[slide.id]?.isCorrect == true
                        is SlideUiModel.AssertionReasonSlide -> state.assertionReasonStates[slide.id]?.isCorrect == true
                        is SlideUiModel.SpotErrorSlide -> state.spotErrorStates[slide.id]?.isCorrect == true
                        is SlideUiModel.DifferentiationSlide -> state.differentiationStates[slide.id]?.isCorrect == true
                        is SlideUiModel.MechanismSlide -> state.mechanismStates[slide.id]?.isCorrect == true
                        is SlideUiModel.RelabelImageSlide -> state.relabelImageStates[slide.id]?.isCorrect == true
                        is SlideUiModel.FlashcardSlide -> false
                    }
                }
                val maxSlideIndex = (deck.slides.size - 1).coerceAtLeast(0)
                SessionPlayerUiState(
                    isLoading = false,
                    notFound = false,
                    loadError = null,
                    workspaceId = deck.workspaceId,
                    title = deck.title,
                    description = deck.description,
                    themeMode = deck.themeMode,
                    slideTransitionMs = deck.slideTransitionMs,
                    slides = deck.slides,
                    leafSlideCount = deck.leafSlides().size,
                    hasStartedSession = state.hasStartedSession,
                    currentSlideIndex = state.currentSlideIndex.coerceIn(0, maxSlideIndex),
                    activeQuizQuestionIndex = state.activeQuizQuestionIndex,
                    isFinished = state.isFinished,
                    totalMcqCount = gradedSlides.size,
                    currentMcqScore = gradedCorrect,
                    mcqStates = state.mcqStates,
                    flashcardStates = state.flashcardStates,
                    fillBlankStates = state.fillBlankStates,
                    assertionReasonStates = state.assertionReasonStates,
                    spotErrorStates = state.spotErrorStates,
                    differentiationStates = state.differentiationStates,
                    mechanismStates = state.mechanismStates,
                    relabelImageStates = state.relabelImageStates,
                    completedReport = state.completedReport,
                    completedMarkdown = state.completedMarkdown,
                    pendingExport = state.pendingExport,
                    pendingShareText = state.pendingShareText,
                    userMessages = state.userMessages
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionPlayerUiState(isLoading = true)
    )

    private fun initializeVisitsIfNeeded(deck: SlideDeck) {
        if (deck.slides.isEmpty() || !telemetry.value.hasStartedSession || telemetry.value.isFinished) return
        if (activeSlideEnterEpochMs == null && !isTelemetryPaused) {
            activeSlideEnterEpochMs = nowMs()
        }
        val initialIdx = telemetry.value.currentSlideIndex.coerceIn(0, deck.slides.lastIndex)
        val firstLeafId = activeLeafSlideId(deck, initialIdx, telemetry.value.activeQuizQuestionIndex) ?: return
        if ((telemetry.value.visitedBySlideId[firstLeafId] ?: 0) == 0) {
            updateTelemetryAndSave { cur ->
                cur.copy(visitedBySlideId = cur.visitedBySlideId + (firstLeafId to 1))
            }
        }
    }

    fun onStartSession() {
        val deck = latestDeck
        val startMs = nowMs()
        sessionStartedEpochMs = startMs
        isTelemetryPaused = false
        activeSlideEnterEpochMs = startMs
        updateTelemetryAndSave { cur ->
            val updatedVisits = if (deck != null && deck.slides.isNotEmpty()) {
                val initialIdx = cur.currentSlideIndex.coerceIn(0, deck.slides.lastIndex)
                val firstLeafId = activeLeafSlideId(deck, initialIdx, cur.activeQuizQuestionIndex)
                if (firstLeafId != null && (cur.visitedBySlideId[firstLeafId] ?: 0) == 0) {
                    cur.visitedBySlideId + (firstLeafId to 1)
                } else {
                    cur.visitedBySlideId
                }
            } else {
                cur.visitedBySlideId
            }
            cur.copy(hasStartedSession = true, visitedBySlideId = updatedVisits)
        }
    }

    fun onPauseTelemetry() {
        isTelemetryPaused = true
        val deck = latestDeck
        val cur = telemetry.value
        if (deck != null && cur.hasStartedSession && !cur.isFinished) {
            val leafId = activeLeafSlideId(deck, cur.currentSlideIndex, cur.activeQuizQuestionIndex)
            flushLeafDwellTime(leafId, restartClock = false)
        } else {
            activeSlideEnterEpochMs = null
        }
        saveSnapshotToHandle(telemetry.value)
    }

    fun onResumeTelemetry() {
        isTelemetryPaused = false
        val cur = telemetry.value
        if (cur.hasStartedSession && !cur.isFinished && activeSlideEnterEpochMs == null) {
            activeSlideEnterEpochMs = nowMs()
        }
    }

    fun onSlideChanged(newIndex: Int) {
        val deck = latestDeck ?: return
        if (deck.slides.isEmpty()) return
        val clamped = newIndex.coerceIn(0, deck.slides.lastIndex)
        if (clamped == telemetry.value.currentSlideIndex) return

        val prevLeafId = activeLeafSlideId(deck, telemetry.value.currentSlideIndex, telemetry.value.activeQuizQuestionIndex)
        flushLeafDwellTime(prevLeafId, restartClock = !isTelemetryPaused)

        updateTelemetryAndSave { cur ->
            val nextLeafId = activeLeafSlideId(deck, clamped, cur.activeQuizQuestionIndex)
            val updatedVisits = if (nextLeafId != null) {
                cur.visitedBySlideId + (nextLeafId to ((cur.visitedBySlideId[nextLeafId] ?: 0) + 1))
            } else {
                cur.visitedBySlideId
            }
            cur.copy(currentSlideIndex = clamped, visitedBySlideId = updatedVisits)
        }
    }

    fun onSelectQuizQuestion(quizId: String, questionIndex: Int) {
        val deck = latestDeck ?: return
        val targetSlideIndex = deck.slides.indexOfFirst { it is SlideUiModel.QuizSlide && it.id == quizId }
        if (targetSlideIndex < 0) return
        val quizSlide = deck.slides[targetSlideIndex] as SlideUiModel.QuizSlide
        if (quizSlide.questions.isEmpty()) return

        val clampedQIdx = questionIndex.coerceIn(0, quizSlide.questions.lastIndex)
        val curState = telemetry.value
        val prevQIdx = (curState.activeQuizQuestionIndex[quizId] ?: 0).coerceIn(0, quizSlide.questions.lastIndex)
        val sameOuterSlide = curState.currentSlideIndex == targetSlideIndex
        if (sameOuterSlide && clampedQIdx == prevQIdx) return

        val prevLeafId = if (sameOuterSlide) {
            quizSlide.questions[prevQIdx].id
        } else {
            activeLeafSlideId(deck, curState.currentSlideIndex, curState.activeQuizQuestionIndex)
        }
        flushLeafDwellTime(prevLeafId, restartClock = !isTelemetryPaused)

        val nextQuestionId = quizSlide.questions[clampedQIdx].id
        updateTelemetryAndSave { cur ->
            cur.copy(
                currentSlideIndex = targetSlideIndex,
                activeQuizQuestionIndex = cur.activeQuizQuestionIndex + (quizId to clampedQIdx),
                visitedBySlideId = cur.visitedBySlideId + (nextQuestionId to ((cur.visitedBySlideId[nextQuestionId] ?: 0) + 1))
            )
        }
    }

    fun onFinishAndAnalyze() {
        val deck = latestDeck ?: return
        if (!telemetry.value.isFinished) {
            val leafId = activeLeafSlideId(deck, telemetry.value.currentSlideIndex, telemetry.value.activeQuizQuestionIndex)
            flushLeafDwellTime(leafId, restartClock = false)
        }
        updateTelemetryAndSave { it.copy(isFinished = true) }
        generateReport(persistToHistory = true)
    }

    fun onReturnToSlides() {
        if (!isTelemetryPaused) {
            activeSlideEnterEpochMs = nowMs()
        }
        updateTelemetryAndSave { it.copy(isFinished = false) }
    }

    fun onToggleMcqOption(slideId: String, optionIndex: Int) {
        val deck = latestDeck ?: return
        val slide = deck.allQuestionSlides().filterIsInstance<SlideUiModel.McqSlide>()
            .firstOrNull { it.id == slideId } ?: return

        updateTelemetryAndSave { cur ->
            val current = cur.mcqStates[slideId] ?: McqSlideRuntimeState()
            val nextSelected = if (slide.question.isMultiCorrect) {
                if (optionIndex in current.selectedIndices) current.selectedIndices - optionIndex
                else current.selectedIndices + optionIndex
            } else {
                if (optionIndex in current.selectedIndices) emptySet() else setOf(optionIndex)
            }
            val nextState = if (nextSelected != current.selectedIndices) {
                current.copy(selectedIndices = nextSelected)
            } else {
                current
            }
            cur.copy(mcqStates = cur.mcqStates + (slideId to nextState))
        }
    }

    fun onCheckMcqAnswer(slideId: String) {
        val deck = latestDeck ?: return
        val slide = deck.allQuestionSlides().filterIsInstance<SlideUiModel.McqSlide>()
            .firstOrNull { it.id == slideId } ?: return

        updateTelemetryAndSave { cur ->
            val current = cur.mcqStates[slideId] ?: McqSlideRuntimeState()
            val eval = evaluateMcqAnswerUseCase(slide.question, current.selectedIndices)
            val updated = current.copy(
                isChecked = true,
                checkAttempts = current.checkAttempts + 1,
                firstTryCorrect = current.firstTryCorrect ?: eval.isCorrect,
                isCorrect = eval.isCorrect
            )
            cur.copy(mcqStates = cur.mcqStates + (slideId to updated))
        }
    }

    fun onFlipFlashcard(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.flashcardStates[slideId] ?: FlashcardSlideRuntimeState()
            val updated = current.copy(isFlipped = !current.isFlipped, flipCount = current.flipCount + 1)
            cur.copy(flashcardStates = cur.flashcardStates + (slideId to updated))
        }
    }

    fun onRateFlashcard(slideId: String, rating: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.flashcardStates[slideId] ?: FlashcardSlideRuntimeState()
            cur.copy(flashcardStates = cur.flashcardStates + (slideId to current.copy(rating = rating)))
        }
    }

    // --- Fill in the Blank ---

    fun onUpdateFillBlankInput(slideId: String, slotIndex: Int, value: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.fillBlankStates[slideId] ?: FillBlankSlideRuntimeState()
            cur.copy(
                fillBlankStates = cur.fillBlankStates + (
                    slideId to current.copy(inputsBySlot = current.inputsBySlot + (slotIndex to value))
                )
            )
        }
    }

    fun onCheckFillBlank(slideId: String) {
        val slide = latestDeck?.allQuestionSlides()?.filterIsInstance<SlideUiModel.FillBlankSlide>()
            ?.firstOrNull { it.id == slideId } ?: return
        updateTelemetryAndSave { cur ->
            val current = cur.fillBlankStates[slideId] ?: FillBlankSlideRuntimeState()
            val eval = evaluateFillBlankUseCase(
                payload = slide.question.payload,
                userInputsBySlotIndex = current.inputsBySlot,
                overriddenSlotIndices = current.overriddenSlotIndices
            )
            val updated = current.copy(
                isChecked = true,
                checkAttempts = current.checkAttempts + 1,
                firstTryCorrect = current.firstTryCorrect ?: eval.isCorrect,
                isCorrect = eval.isCorrect,
                overriddenCorrect = eval.isOverridden,
                anyFuzzy = eval.anyFuzzy
            )
            cur.copy(fillBlankStates = cur.fillBlankStates + (slideId to updated))
        }
    }

    fun onOverrideFillBlankSlot(slideId: String, slotIndex: Int) {
        val slide = latestDeck?.allQuestionSlides()?.filterIsInstance<SlideUiModel.FillBlankSlide>()
            ?.firstOrNull { it.id == slideId } ?: return
        updateTelemetryAndSave { cur ->
            val current = cur.fillBlankStates[slideId] ?: FillBlankSlideRuntimeState()
            val nextOverriddenSlots = current.overriddenSlotIndices + slotIndex
            val eval = evaluateFillBlankUseCase(
                payload = slide.question.payload,
                userInputsBySlotIndex = current.inputsBySlot,
                overriddenSlotIndices = nextOverriddenSlots
            )
            cur.copy(
                fillBlankStates = cur.fillBlankStates + (
                    slideId to current.copy(
                        overriddenSlotIndices = nextOverriddenSlots,
                        isChecked = true,
                        isCorrect = eval.isCorrect,
                        overriddenCorrect = eval.isOverridden,
                        anyFuzzy = eval.anyFuzzy
                    )
                )
            )
        }
    }

    fun onOverrideFillBlankCorrect(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.fillBlankStates[slideId] ?: FillBlankSlideRuntimeState()
            cur.copy(
                fillBlankStates = cur.fillBlankStates + (
                    slideId to current.copy(
                        isChecked = true,
                        isCorrect = true,
                        overriddenCorrect = true,
                        anyFuzzy = false
                    )
                )
            )
        }
    }

    fun onRetryFillBlank(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.fillBlankStates[slideId] ?: FillBlankSlideRuntimeState()
            cur.copy(
                fillBlankStates = cur.fillBlankStates + (
                    slideId to current.copy(
                        inputsBySlot = emptyMap(),
                        overriddenSlotIndices = emptySet(),
                        isChecked = false,
                        isCorrect = null,
                        overriddenCorrect = false,
                        anyFuzzy = false
                    )
                )
            )
        }
    }

    // --- Assertion-Reason ---

    fun onSelectAssertionReason(slideId: String, code: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.assertionReasonStates[slideId] ?: AssertionReasonSlideRuntimeState()
            cur.copy(assertionReasonStates = cur.assertionReasonStates + (slideId to current.copy(selectedCode = code)))
        }
    }

    fun onCheckAssertionReason(slideId: String) {
        val slide = latestDeck?.allQuestionSlides()?.filterIsInstance<SlideUiModel.AssertionReasonSlide>()
            ?.firstOrNull { it.id == slideId } ?: return
        updateTelemetryAndSave { cur ->
            val current = cur.assertionReasonStates[slideId] ?: AssertionReasonSlideRuntimeState()
            val eval = evaluateAssertionReasonUseCase(slide.question.payload, current.selectedCode)
            val updated = current.copy(
                isChecked = true,
                checkAttempts = current.checkAttempts + 1,
                firstTryCorrect = current.firstTryCorrect ?: eval.isCorrect,
                isCorrect = eval.isCorrect
            )
            cur.copy(assertionReasonStates = cur.assertionReasonStates + (slideId to updated))
        }
    }

    fun onRetryAssertionReason(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.assertionReasonStates[slideId] ?: AssertionReasonSlideRuntimeState()
            cur.copy(
                assertionReasonStates = cur.assertionReasonStates + (
                    slideId to current.copy(selectedCode = null, isChecked = false, isCorrect = null)
                )
            )
        }
    }

    // --- Spot the Error ---

    fun onToggleSpotErrorSpan(slideId: String, spanIndex: Int) {
        updateTelemetryAndSave { cur ->
            val current = cur.spotErrorStates[slideId] ?: SpotErrorSlideRuntimeState()
            val nextSpans = if (spanIndex in current.selectedSpans) {
                current.selectedSpans - spanIndex
            } else {
                current.selectedSpans + spanIndex
            }
            cur.copy(
                spotErrorStates = cur.spotErrorStates + (
                    slideId to current.copy(selectedSpans = nextSpans, pickedNoError = false)
                )
            )
        }
    }

    fun onToggleSpotErrorNoError(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.spotErrorStates[slideId] ?: SpotErrorSlideRuntimeState()
            val nextNoError = !current.pickedNoError
            cur.copy(
                spotErrorStates = cur.spotErrorStates + (
                    slideId to current.copy(
                        pickedNoError = nextNoError,
                        selectedSpans = if (nextNoError) emptySet() else current.selectedSpans
                    )
                )
            )
        }
    }

    fun onUpdateSpotErrorFix(slideId: String, spanIndex: Int, fixText: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.spotErrorStates[slideId] ?: SpotErrorSlideRuntimeState()
            cur.copy(
                spotErrorStates = cur.spotErrorStates + (
                    slideId to current.copy(fixesBySpan = current.fixesBySpan + (spanIndex to fixText))
                )
            )
        }
    }

    fun onCheckSpotError(slideId: String) {
        val slide = latestDeck?.allQuestionSlides()?.filterIsInstance<SlideUiModel.SpotErrorSlide>()
            ?.firstOrNull { it.id == slideId } ?: return
        updateTelemetryAndSave { cur ->
            val current = cur.spotErrorStates[slideId] ?: SpotErrorSlideRuntimeState()
            val eval = evaluateSpotErrorUseCase(
                payload = slide.question.payload,
                selectedSpanIndices = current.selectedSpans,
                pickedNoError = current.pickedNoError,
                fixesBySpanIndex = current.fixesBySpan
            )
            val updated = current.copy(
                isChecked = true,
                checkAttempts = current.checkAttempts + 1,
                firstTryCorrect = current.firstTryCorrect ?: eval.isCorrect,
                isCorrect = eval.isCorrect
            )
            cur.copy(spotErrorStates = cur.spotErrorStates + (slideId to updated))
        }
    }

    fun onRetrySpotError(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.spotErrorStates[slideId] ?: SpotErrorSlideRuntimeState()
            cur.copy(
                spotErrorStates = cur.spotErrorStates + (
                    slideId to current.copy(
                        selectedSpans = emptySet(),
                        pickedNoError = false,
                        fixesBySpan = emptyMap(),
                        isChecked = false,
                        isCorrect = null
                    )
                )
            )
        }
    }

    // --- Differentiation ---

    fun onUpdateDiffFillInput(slideId: String, rowIndex: Int, colIndex: Int, value: String) {
        val cellKey = "$rowIndex:$colIndex"
        updateTelemetryAndSave { cur ->
            val current = cur.differentiationStates[slideId] ?: DifferentiationSlideRuntimeState()
            cur.copy(
                differentiationStates = cur.differentiationStates + (
                    slideId to current.copy(fillInputsByCell = current.fillInputsByCell + (cellKey to value))
                )
            )
        }
    }

    fun onToggleDiffErrorCell(slideId: String, rowIndex: Int, colIndex: Int) {
        val cellKey = "$rowIndex:$colIndex"
        updateTelemetryAndSave { cur ->
            val current = cur.differentiationStates[slideId] ?: DifferentiationSlideRuntimeState()
            val nextCells = if (cellKey in current.selectedErrorCells) {
                current.selectedErrorCells - cellKey
            } else {
                current.selectedErrorCells + cellKey
            }
            cur.copy(
                differentiationStates = cur.differentiationStates + (
                    slideId to current.copy(selectedErrorCells = nextCells, pickedNoError = false)
                )
            )
        }
    }

    fun onToggleDiffNoError(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.differentiationStates[slideId] ?: DifferentiationSlideRuntimeState()
            val nextNoError = !current.pickedNoError
            cur.copy(
                differentiationStates = cur.differentiationStates + (
                    slideId to current.copy(
                        pickedNoError = nextNoError,
                        selectedErrorCells = if (nextNoError) emptySet() else current.selectedErrorCells
                    )
                )
            )
        }
    }

    fun onUpdateDiffRearrangeRows(slideId: String, rows: List<List<String>>) {
        updateTelemetryAndSave { cur ->
            val current = cur.differentiationStates[slideId] ?: DifferentiationSlideRuntimeState()
            cur.copy(
                differentiationStates = cur.differentiationStates + (
                    slideId to current.copy(rearrangeRows = rows)
                )
            )
        }
    }

    fun onCheckDifferentiation(slideId: String) {
        val slide = latestDeck?.allQuestionSlides()?.filterIsInstance<SlideUiModel.DifferentiationSlide>()
            ?.firstOrNull { it.id == slideId } ?: return
        val payload = slide.question.payload
        updateTelemetryAndSave { cur ->
            val current = cur.differentiationStates[slideId] ?: DifferentiationSlideRuntimeState()
            val eval = when (payload.mode) {
                "fill" -> {
                    val inputs = current.fillInputsByCell.mapNotNull { (k, v) ->
                        parseCellCoord(k)?.let { it to v }
                    }.toMap()
                    evaluateDifferentiationUseCase.evaluateFill(payload, inputs)
                }
                "spot-wrong" -> {
                    val selected = current.selectedErrorCells.mapNotNull(::parseCellCoord).toSet()
                    evaluateDifferentiationUseCase.evaluateSpotWrong(payload, selected, current.pickedNoError)
                }
                "rearrange" -> {
                    val rowsToGrade = current.rearrangeRows.ifEmpty {
                        payload.rows.mapIndexed { rIdx, row ->
                            val entityCols = row.drop(1)
                            listOf(row.first()) + DeterministicShuffler.deterministicShuffle(
                                entityCols,
                                "$slideId:r$rIdx"
                            ) { it }
                        }
                    }
                    evaluateDifferentiationUseCase.evaluateRearrange(payload, rowsToGrade)
                }
                else -> return@updateTelemetryAndSave cur
            }
            val updated = current.copy(
                isChecked = true,
                checkAttempts = current.checkAttempts + 1,
                firstTryCorrect = current.firstTryCorrect ?: eval.isCorrect,
                isCorrect = eval.isCorrect,
                overriddenCorrect = false,
                anyFuzzy = eval.anyFuzzy
            )
            cur.copy(differentiationStates = cur.differentiationStates + (slideId to updated))
        }
    }

    fun onOverrideDifferentiationCorrect(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.differentiationStates[slideId] ?: DifferentiationSlideRuntimeState()
            cur.copy(
                differentiationStates = cur.differentiationStates + (
                    slideId to current.copy(
                        isChecked = true,
                        isCorrect = true,
                        overriddenCorrect = true,
                        anyFuzzy = false
                    )
                )
            )
        }
    }

    fun onRetryDifferentiation(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.differentiationStates[slideId] ?: DifferentiationSlideRuntimeState()
            cur.copy(
                differentiationStates = cur.differentiationStates + (
                    slideId to current.copy(
                        fillInputsByCell = emptyMap(),
                        selectedErrorCells = emptySet(),
                        pickedNoError = false,
                        rearrangeRows = emptyList(),
                        isChecked = false,
                        isCorrect = null,
                        overriddenCorrect = false,
                        anyFuzzy = false
                    )
                )
            )
        }
    }

    // --- Mechanism ---

    fun onUpdateMechanismSequence(slideId: String, bankStepIds: List<Int>, placedStepIds: List<Int>) {
        updateTelemetryAndSave { cur ->
            val current = cur.mechanismStates[slideId] ?: MechanismSlideRuntimeState()
            cur.copy(
                mechanismStates = cur.mechanismStates + (
                    slideId to current.copy(bankStepIds = bankStepIds, placedStepIds = placedStepIds)
                )
            )
        }
    }

    fun onCheckMechanism(slideId: String) {
        val slide = latestDeck?.allQuestionSlides()?.filterIsInstance<SlideUiModel.MechanismSlide>()
            ?.firstOrNull { it.id == slideId } ?: return
        updateTelemetryAndSave { cur ->
            val current = cur.mechanismStates[slideId] ?: MechanismSlideRuntimeState()
            val eval = evaluateMechanismUseCase(slide.question.payload, current.placedStepIds)
            val updated = current.copy(
                isChecked = true,
                checkAttempts = current.checkAttempts + 1,
                firstTryCorrect = current.firstTryCorrect ?: eval.isCorrect,
                isCorrect = eval.isCorrect
            )
            cur.copy(mechanismStates = cur.mechanismStates + (slideId to updated))
        }
    }

    fun onRetryMechanism(slideId: String) {
        val slide = latestDeck?.allQuestionSlides()?.filterIsInstance<SlideUiModel.MechanismSlide>()
            ?.firstOrNull { it.id == slideId }
        val reshuffledBank = slide?.let {
            DeterministicShuffler.deterministicShuffle(it.question.payload.steps, "$slideId:mech") { s ->
                s.stepId.toString()
            }.map { s -> s.stepId }
        }.orEmpty()
        updateTelemetryAndSave { cur ->
            val current = cur.mechanismStates[slideId] ?: MechanismSlideRuntimeState()
            cur.copy(
                mechanismStates = cur.mechanismStates + (
                    slideId to current.copy(
                        bankStepIds = reshuffledBank,
                        placedStepIds = emptyList(),
                        isChecked = false,
                        isCorrect = null
                    )
                )
            )
        }
    }

    // --- Relabel Image ---

    fun onToggleRelabelRevealBox(slideId: String, boxIndex: Int) {
        updateTelemetryAndSave { cur ->
            val current = cur.relabelImageStates[slideId] ?: RelabelImageSlideRuntimeState()
            val nextRevealed = if (boxIndex in current.revealedBoxIndices) {
                current.revealedBoxIndices - boxIndex
            } else {
                current.revealedBoxIndices + boxIndex
            }
            cur.copy(
                relabelImageStates = cur.relabelImageStates + (
                    slideId to current.copy(revealedBoxIndices = nextRevealed)
                )
            )
        }
    }

    fun onRateRelabelReveal(slideId: String, rating: String) {
        val slide = latestDeck?.allQuestionSlides()?.filterIsInstance<SlideUiModel.RelabelImageSlide>()
            ?.firstOrNull { it.id == slideId } ?: return
        updateTelemetryAndSave { cur ->
            val current = cur.relabelImageStates[slideId] ?: RelabelImageSlideRuntimeState()
            val eval = evaluateRelabelImageUseCase.evaluateReveal(
                payload = slide.question.payload,
                revealedBoxIndices = current.revealedBoxIndices,
                rating = rating
            )
            val updated = current.copy(
                rating = rating,
                isChecked = true,
                checkAttempts = current.checkAttempts + 1,
                firstTryCorrect = current.firstTryCorrect ?: eval.isCorrect,
                isCorrect = eval.isCorrect
            )
            cur.copy(relabelImageStates = cur.relabelImageStates + (slideId to updated))
        }
    }

    fun onAssignRelabelBankLabel(slideId: String, boxIndex: Int, label: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.relabelImageStates[slideId] ?: RelabelImageSlideRuntimeState()
            cur.copy(
                relabelImageStates = cur.relabelImageStates + (
                    slideId to current.copy(assignedLabelsByBox = current.assignedLabelsByBox + (boxIndex to label))
                )
            )
        }
    }

    fun onUpdateRelabelRecallInput(slideId: String, boxIndex: Int, value: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.relabelImageStates[slideId] ?: RelabelImageSlideRuntimeState()
            cur.copy(
                relabelImageStates = cur.relabelImageStates + (
                    slideId to current.copy(recallInputsByBox = current.recallInputsByBox + (boxIndex to value))
                )
            )
        }
    }

    fun onCheckRelabelImage(slideId: String) {
        val slide = latestDeck?.allQuestionSlides()?.filterIsInstance<SlideUiModel.RelabelImageSlide>()
            ?.firstOrNull { it.id == slideId } ?: return
        val payload = slide.question.payload
        updateTelemetryAndSave { cur ->
            val current = cur.relabelImageStates[slideId] ?: RelabelImageSlideRuntimeState()
            val eval = when (payload.mode) {
                "bank" -> evaluateRelabelImageUseCase.evaluateBank(payload, current.assignedLabelsByBox)
                "recall" -> evaluateRelabelImageUseCase.evaluateRecall(payload, current.recallInputsByBox)
                else -> evaluateRelabelImageUseCase.evaluateReveal(payload, current.revealedBoxIndices, current.rating)
            }
            val updated = current.copy(
                isChecked = true,
                checkAttempts = current.checkAttempts + 1,
                firstTryCorrect = current.firstTryCorrect ?: eval.isCorrect,
                isCorrect = eval.isCorrect,
                overriddenCorrect = false,
                anyFuzzy = eval.anyFuzzy
            )
            cur.copy(relabelImageStates = cur.relabelImageStates + (slideId to updated))
        }
    }

    fun onOverrideRelabelCorrect(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.relabelImageStates[slideId] ?: RelabelImageSlideRuntimeState()
            cur.copy(
                relabelImageStates = cur.relabelImageStates + (
                    slideId to current.copy(
                        isChecked = true,
                        isCorrect = true,
                        overriddenCorrect = true,
                        anyFuzzy = false
                    )
                )
            )
        }
    }

    fun onRetryRelabelImage(slideId: String) {
        updateTelemetryAndSave { cur ->
            val current = cur.relabelImageStates[slideId] ?: RelabelImageSlideRuntimeState()
            cur.copy(
                relabelImageStates = cur.relabelImageStates + (
                    slideId to current.copy(
                        revealedBoxIndices = emptySet(),
                        assignedLabelsByBox = emptyMap(),
                        recallInputsByBox = emptyMap(),
                        rating = null,
                        isChecked = false,
                        isCorrect = null,
                        overriddenCorrect = false,
                        anyFuzzy = false
                    )
                )
            )
        }
    }

    fun onRequestExportJson() {
        val report = telemetry.value.completedReport ?: return
        viewModelScope.launch {
            val encoded = analysisRepository.serializeReportJson(report)
            telemetry.update {
                it.copy(
                    pendingExport = PendingExportPayload(
                        suggestedFileName = "${report.workspaceId}.analysis.json",
                        mimeType = "application/json",
                        content = encoded
                    )
                )
            }
        }
    }

    fun onRequestExportMarkdown() {
        val report = telemetry.value.completedReport ?: return
        val markdown = telemetry.value.completedMarkdown ?: return
        telemetry.update {
            it.copy(
                pendingExport = PendingExportPayload(
                    suggestedFileName = "${report.workspaceId}.analysis.md",
                    mimeType = "text/plain",
                    content = markdown
                )
            )
        }
    }

    fun onSaveExportToUri(uriString: String, content: String) {
        viewModelScope.launch {
            analysisRepository.writeExportToUri(uriString, content)
                .onSuccess { pushMessage("Report exported successfully.") }
                .onFailure { err -> pushMessage(err.message ?: "Failed to export report.") }
        }
    }

    fun onExportConsumed() {
        telemetry.update { it.copy(pendingExport = null) }
    }

    fun onRequestShareMarkdown() {
        val markdown = telemetry.value.completedMarkdown ?: return
        telemetry.update { it.copy(pendingShareText = markdown) }
    }

    fun onShareConsumed() {
        telemetry.update { it.copy(pendingShareText = null) }
    }

    fun onMessageShown(messageId: Long) {
        telemetry.update { cur -> cur.copy(userMessages = cur.userMessages.dismissMessage(messageId)) }
    }

    private fun flushLeafDwellTime(leafId: String?, restartClock: Boolean) {
        val enteredAt = activeSlideEnterEpochMs
        val now = nowMs()
        activeSlideEnterEpochMs = if (restartClock) now else null
        if (enteredAt == null || leafId == null) return

        val delta = (now - enteredAt).coerceAtLeast(0L)
        if (delta == 0L) return
        updateTelemetryAndSave { cur ->
            cur.copy(timeSpentBySlideId = cur.timeSpentBySlideId + (leafId to ((cur.timeSpentBySlideId[leafId] ?: 0L) + delta)))
        }
    }

    private fun generateReport(persistToHistory: Boolean) {
        val deck = latestDeck ?: return
        val snapshot = telemetry.value
        val entries = deck.leafSlides().mapIndexed { idx, slide ->
            val sequentialIndex = idx + 1
            val timeMs = snapshot.timeSpentBySlideId[slide.id] ?: 0L
            val visits = snapshot.visitedBySlideId[slide.id] ?: 0
            when (slide) {
                is SlideUiModel.ArticleSlide -> SlideAnalysisEntry(
                    slideIndex = sequentialIndex,
                    id = slide.id,
                    kind = "article",
                    timeSpentMs = timeMs,
                    visitedCount = visits
                )
                is SlideUiModel.McqSlide -> {
                    val m = snapshot.mcqStates[slide.id] ?: McqSlideRuntimeState()
                    SlideAnalysisEntry(
                        slideIndex = sequentialIndex,
                        id = slide.id,
                        kind = "mcq",
                        timeSpentMs = timeMs,
                        visitedCount = visits,
                        checkAttempts = m.checkAttempts,
                        selectedOptionIndices = m.selectedIndices.sorted(),
                        correctOptionIndices = slide.question.options.filter { it.isCorrect }.map { it.index }.sorted(),
                        firstTryCorrect = m.firstTryCorrect,
                        isCorrect = m.isCorrect
                    )
                }
                is SlideUiModel.FlashcardSlide -> {
                    val f = snapshot.flashcardStates[slide.id] ?: FlashcardSlideRuntimeState()
                    SlideAnalysisEntry(
                        slideIndex = sequentialIndex,
                        id = slide.id,
                        kind = "flashcard",
                        timeSpentMs = timeMs,
                        visitedCount = visits,
                        flipCount = f.flipCount,
                        flashcardRating = f.rating
                    )
                }
                is SlideUiModel.FillBlankSlide -> {
                    val st = snapshot.fillBlankStates[slide.id] ?: FillBlankSlideRuntimeState()
                    SlideAnalysisEntry(
                        slideIndex = sequentialIndex,
                        id = slide.id,
                        kind = "fill-blank",
                        timeSpentMs = timeMs,
                        visitedCount = visits,
                        checkAttempts = st.checkAttempts,
                        firstTryCorrect = st.firstTryCorrect,
                        isCorrect = st.isCorrect
                    )
                }
                is SlideUiModel.AssertionReasonSlide -> {
                    val st = snapshot.assertionReasonStates[slide.id] ?: AssertionReasonSlideRuntimeState()
                    SlideAnalysisEntry(
                        slideIndex = sequentialIndex,
                        id = slide.id,
                        kind = "assertion-reason",
                        timeSpentMs = timeMs,
                        visitedCount = visits,
                        checkAttempts = st.checkAttempts,
                        firstTryCorrect = st.firstTryCorrect,
                        isCorrect = st.isCorrect
                    )
                }
                is SlideUiModel.SpotErrorSlide -> {
                    val st = snapshot.spotErrorStates[slide.id] ?: SpotErrorSlideRuntimeState()
                    SlideAnalysisEntry(
                        slideIndex = sequentialIndex,
                        id = slide.id,
                        kind = "spot-error",
                        timeSpentMs = timeMs,
                        visitedCount = visits,
                        checkAttempts = st.checkAttempts,
                        firstTryCorrect = st.firstTryCorrect,
                        isCorrect = st.isCorrect
                    )
                }
                is SlideUiModel.DifferentiationSlide -> {
                    val st = snapshot.differentiationStates[slide.id] ?: DifferentiationSlideRuntimeState()
                    SlideAnalysisEntry(
                        slideIndex = sequentialIndex,
                        id = slide.id,
                        kind = "diff",
                        timeSpentMs = timeMs,
                        visitedCount = visits,
                        checkAttempts = st.checkAttempts,
                        firstTryCorrect = st.firstTryCorrect,
                        isCorrect = st.isCorrect
                    )
                }
                is SlideUiModel.MechanismSlide -> {
                    val st = snapshot.mechanismStates[slide.id] ?: MechanismSlideRuntimeState()
                    SlideAnalysisEntry(
                        slideIndex = sequentialIndex,
                        id = slide.id,
                        kind = "mechanism",
                        timeSpentMs = timeMs,
                        visitedCount = visits,
                        checkAttempts = st.checkAttempts,
                        firstTryCorrect = st.firstTryCorrect,
                        isCorrect = st.isCorrect
                    )
                }
                is SlideUiModel.RelabelImageSlide -> {
                    val st = snapshot.relabelImageStates[slide.id] ?: RelabelImageSlideRuntimeState()
                    SlideAnalysisEntry(
                        slideIndex = sequentialIndex,
                        id = slide.id,
                        kind = "relabel",
                        timeSpentMs = timeMs,
                        visitedCount = visits,
                        checkAttempts = st.checkAttempts,
                        firstTryCorrect = st.firstTryCorrect,
                        isCorrect = st.isCorrect,
                        flashcardRating = st.rating
                    )
                }
                is SlideUiModel.QuizSlide -> error("QuizSlide should have been flattened into leaf slides.")
            }
        }

        viewModelScope.launch {
            val built = buildAnalysisReportUseCase(
                workspaceId = deck.workspaceId,
                title = deck.title,
                startedAtEpochMs = sessionStartedEpochMs,
                completedAtEpochMs = nowMs(),
                slideEntries = entries
            )

            telemetry.update { cur ->
                cur.copy(
                    completedReport = built.report,
                    completedMarkdown = built.markdown
                )
            }

            if (persistToHistory) {
                persistMutex.withLock {
                    val existingRunId = telemetry.value.savedRunId
                    runCatching {
                        analysisRepository.saveCompletedRun(
                            report = built.report,
                            markdown = built.markdown,
                            existingRunId = existingRunId
                        )
                    }.onSuccess { saved ->
                        updateTelemetryAndSave { cur -> cur.copy(savedRunId = saved.runId) }
                        val msg = if (existingRunId == null) {
                            "Session analysis saved to History."
                        } else {
                            "Session analysis updated in History."
                        }
                        pushMessage(msg)
                    }.onFailure { err ->
                        pushMessage(err.message ?: "Failed to save analysis run.")
                    }
                }
            }
        }
    }

    private fun updateTelemetryAndSave(transform: (PlayerTelemetryState) -> PlayerTelemetryState) {
        var updated: PlayerTelemetryState? = null
        telemetry.update { cur ->
            transform(cur).also { updated = it }
        }
        updated?.let(::saveSnapshotToHandle)
    }

    private fun saveSnapshotToHandle(state: PlayerTelemetryState) {
        savedStateHandle[KEY_HAS_STARTED_SESSION] = state.hasStartedSession
        savedStateHandle[KEY_CURRENT_SLIDE_INDEX] = state.currentSlideIndex
        val snapshot = PlayerSessionSnapshot(
            hasStartedSession = state.hasStartedSession,
            sessionStartedEpochMs = sessionStartedEpochMs,
            currentSlideIndex = state.currentSlideIndex,
            activeQuizQuestionIndex = state.activeQuizQuestionIndex,
            isFinished = state.isFinished,
            timeSpentBySlideId = state.timeSpentBySlideId,
            visitedBySlideId = state.visitedBySlideId,
            mcqStates = state.mcqStates.mapValues { (_, m) ->
                SerializableMcqState(
                    selectedIndices = m.selectedIndices.sorted(),
                    isChecked = m.isChecked,
                    checkAttempts = m.checkAttempts,
                    firstTryCorrect = m.firstTryCorrect,
                    isCorrect = m.isCorrect
                )
            },
            flashcardStates = state.flashcardStates.mapValues { (_, f) ->
                SerializableFlashcardState(
                    isFlipped = f.isFlipped,
                    flipCount = f.flipCount,
                    rating = f.rating
                )
            },
            fillBlankStates = state.fillBlankStates.mapValues { (_, s) ->
                SerializableFillBlankState(
                    inputsBySlot = s.inputsBySlot,
                    overriddenSlotIndices = s.overriddenSlotIndices.sorted(),
                    isChecked = s.isChecked,
                    checkAttempts = s.checkAttempts,
                    firstTryCorrect = s.firstTryCorrect,
                    isCorrect = s.isCorrect,
                    overriddenCorrect = s.overriddenCorrect,
                    anyFuzzy = s.anyFuzzy
                )
            },
            assertionReasonStates = state.assertionReasonStates.mapValues { (_, s) ->
                SerializableAssertionReasonState(
                    selectedCode = s.selectedCode,
                    isChecked = s.isChecked,
                    checkAttempts = s.checkAttempts,
                    firstTryCorrect = s.firstTryCorrect,
                    isCorrect = s.isCorrect
                )
            },
            spotErrorStates = state.spotErrorStates.mapValues { (_, s) ->
                SerializableSpotErrorState(
                    selectedSpans = s.selectedSpans.sorted(),
                    pickedNoError = s.pickedNoError,
                    fixesBySpan = s.fixesBySpan,
                    isChecked = s.isChecked,
                    checkAttempts = s.checkAttempts,
                    firstTryCorrect = s.firstTryCorrect,
                    isCorrect = s.isCorrect
                )
            },
            differentiationStates = state.differentiationStates.mapValues { (_, s) ->
                SerializableDifferentiationState(
                    fillInputsByCell = s.fillInputsByCell,
                    selectedErrorCells = s.selectedErrorCells.sorted(),
                    pickedNoError = s.pickedNoError,
                    rearrangeRows = s.rearrangeRows,
                    isChecked = s.isChecked,
                    checkAttempts = s.checkAttempts,
                    firstTryCorrect = s.firstTryCorrect,
                    isCorrect = s.isCorrect,
                    overriddenCorrect = s.overriddenCorrect,
                    anyFuzzy = s.anyFuzzy
                )
            },
            mechanismStates = state.mechanismStates.mapValues { (_, s) ->
                SerializableMechanismState(
                    bankStepIds = s.bankStepIds,
                    placedStepIds = s.placedStepIds,
                    isChecked = s.isChecked,
                    checkAttempts = s.checkAttempts,
                    firstTryCorrect = s.firstTryCorrect,
                    isCorrect = s.isCorrect
                )
            },
            relabelImageStates = state.relabelImageStates.mapValues { (_, s) ->
                SerializableRelabelImageState(
                    revealedBoxIndices = s.revealedBoxIndices.sorted(),
                    assignedLabelsByBox = s.assignedLabelsByBox,
                    recallInputsByBox = s.recallInputsByBox,
                    rating = s.rating,
                    isChecked = s.isChecked,
                    checkAttempts = s.checkAttempts,
                    firstTryCorrect = s.firstTryCorrect,
                    isCorrect = s.isCorrect,
                    overriddenCorrect = s.overriddenCorrect,
                    anyFuzzy = s.anyFuzzy
                )
            },
            savedRunId = state.savedRunId
        )
        runCatching {
            savedStateHandle[KEY_SESSION_SNAPSHOT] = snapshotJson.encodeToString(snapshot)
        }
    }

    private fun pushMessage(text: String) {
        telemetry.update { cur -> cur.copy(userMessages = cur.userMessages.appendMessage(text)) }
    }

    private fun parseCellCoord(raw: String): Pair<Int, Int>? {
        val parts = raw.split(':')
        if (parts.size != 2) return null
        val r = parts[0].toIntOrNull() ?: return null
        val c = parts[1].toIntOrNull() ?: return null
        return r to c
    }

    private companion object {
        const val KEY_CURRENT_SLIDE_INDEX = "currentSlideIndex"
        const val KEY_HAS_STARTED_SESSION = "hasStartedSession"
        const val KEY_SESSION_SNAPSHOT = "playerSessionSnapshot"
    }
}

private fun activeLeafSlideId(
    deck: SlideDeck,
    slideIdx: Int,
    quizIndices: Map<String, Int>
): String? {
    val slide = deck.slides.getOrNull(slideIdx) ?: return null
    return when (slide) {
        is SlideUiModel.QuizSlide -> {
            if (slide.questions.isEmpty()) null
            else slide.questions[(quizIndices[slide.id] ?: 0).coerceIn(0, slide.questions.lastIndex)].id
        }
        else -> slide.id
    }
}
