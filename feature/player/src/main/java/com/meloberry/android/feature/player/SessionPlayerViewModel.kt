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
import com.meloberry.android.data.workspace.WorkspaceRepository
import com.meloberry.android.domain.BuildAnalysisReportUseCase
import com.meloberry.android.domain.BuildSlideDeckUseCase
import com.meloberry.android.domain.EvaluateMcqAnswerUseCase
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

private data class PlayerTelemetryState(
    val hasStartedSession: Boolean = false,
    val currentSlideIndex: Int = 0,
    val activeQuizQuestionIndex: Map<String, Int> = emptyMap(),
    val isFinished: Boolean = false,
    val timeSpentBySlideId: Map<String, Long> = emptyMap(),
    val visitedBySlideId: Map<String, Int> = emptyMap(),
    val mcqStates: Map<String, McqSlideRuntimeState> = emptyMap(),
    val flashcardStates: Map<String, FlashcardSlideRuntimeState> = emptyMap(),
    val completedReport: SessionAnalysisReport? = null,
    val completedMarkdown: String? = null,
    val pendingExport: PendingExportPayload? = null,
    val pendingShareText: String? = null,
    val userMessages: List<UserMessage> = emptyList(),
    val hasAutoSavedRun: Boolean = false
)

private sealed interface DeckLoadResult {
    data object Loading : DeckLoadResult
    data object NotFound : DeckLoadResult
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
    private val nowMs: () -> Long
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        workspaceRepository: WorkspaceRepository,
        analysisRepository: AnalysisRepository,
        buildSlideDeckUseCase: BuildSlideDeckUseCase,
        evaluateMcqAnswerUseCase: EvaluateMcqAnswerUseCase,
        buildAnalysisReportUseCase: BuildAnalysisReportUseCase
    ) : this(
        savedStateHandle = savedStateHandle,
        workspaceRepository = workspaceRepository,
        analysisRepository = analysisRepository,
        buildSlideDeckUseCase = buildSlideDeckUseCase,
        evaluateMcqAnswerUseCase = evaluateMcqAnswerUseCase,
        buildAnalysisReportUseCase = buildAnalysisReportUseCase,
        nowMs = System::currentTimeMillis
    )

    private val workspaceId: String = savedStateHandle.get<String>("workspaceId").orEmpty()
    private var sessionStartedEpochMs: Long = nowMs()
    private var activeSlideEnterEpochMs: Long? = null
    private var latestDeck: SlideDeck? = null

    private val telemetry = MutableStateFlow(
        PlayerTelemetryState(
            hasStartedSession = savedStateHandle.get<Boolean>(KEY_HAS_STARTED_SESSION) ?: false,
            currentSlideIndex = savedStateHandle.get<Int>(KEY_CURRENT_SLIDE_INDEX) ?: 0
        )
    )

    private val deckFlow = workspaceRepository.observeWorkspace(workspaceId)
        .map { workspace ->
            if (workspace == null) {
                DeckLoadResult.NotFound
            } else {
                val built = buildSlideDeckUseCase(workspace)
                latestDeck = built
                initializeVisitsIfNeeded(built)
                DeckLoadResult.Loaded(built)
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
            is DeckLoadResult.Loaded -> {
                val deck = load.deck
                val mcqs = deck.allQuestionSlides().filterIsInstance<SlideUiModel.McqSlide>()
                val maxSlideIndex = (deck.slides.size - 1).coerceAtLeast(0)
                SessionPlayerUiState(
                    isLoading = false,
                    notFound = false,
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
                    totalMcqCount = mcqs.size,
                    currentMcqScore = mcqs.count { state.mcqStates[it.id]?.isCorrect == true },
                    mcqStates = state.mcqStates,
                    flashcardStates = state.flashcardStates,
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
        if (deck.slides.isEmpty() || !telemetry.value.hasStartedSession) return
        if (activeSlideEnterEpochMs == null) {
            activeSlideEnterEpochMs = nowMs()
        }
        val initialIdx = telemetry.value.currentSlideIndex.coerceIn(0, deck.slides.lastIndex)
        val firstLeafId = activeLeafSlideId(deck, initialIdx, telemetry.value.activeQuizQuestionIndex) ?: return
        if ((telemetry.value.visitedBySlideId[firstLeafId] ?: 0) == 0) {
            telemetry.update { cur ->
                cur.copy(visitedBySlideId = cur.visitedBySlideId + (firstLeafId to 1))
            }
        }
    }

    fun onStartSession() {
        val deck = latestDeck
        val startMs = nowMs()
        sessionStartedEpochMs = startMs
        activeSlideEnterEpochMs = startMs
        savedStateHandle[KEY_HAS_STARTED_SESSION] = true
        telemetry.update { cur ->
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

    fun onSlideChanged(newIndex: Int) {
        val deck = latestDeck ?: return
        if (deck.slides.isEmpty()) return
        val clamped = newIndex.coerceIn(0, deck.slides.lastIndex)
        if (clamped == telemetry.value.currentSlideIndex) return

        flushActiveLeafDwellTime(deck)
        savedStateHandle[KEY_CURRENT_SLIDE_INDEX] = clamped

        telemetry.update { cur ->
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
        val quizSlide = deck.slides.filterIsInstance<SlideUiModel.QuizSlide>().firstOrNull { it.id == quizId } ?: return
        if (quizSlide.questions.isEmpty()) return

        val clampedQIdx = questionIndex.coerceIn(0, quizSlide.questions.lastIndex)
        val prevQIdx = (telemetry.value.activeQuizQuestionIndex[quizId] ?: 0).coerceIn(0, quizSlide.questions.lastIndex)
        if (clampedQIdx == prevQIdx) return

        flushActiveLeafDwellTime(deck)
        val nextQuestionId = quizSlide.questions[clampedQIdx].id
        telemetry.update { cur ->
            cur.copy(
                activeQuizQuestionIndex = cur.activeQuizQuestionIndex + (quizId to clampedQIdx),
                visitedBySlideId = cur.visitedBySlideId + (nextQuestionId to ((cur.visitedBySlideId[nextQuestionId] ?: 0) + 1))
            )
        }
    }

    fun onFinishAndAnalyze() {
        val deck = latestDeck ?: return
        if (!telemetry.value.isFinished) {
            flushActiveLeafDwellTime(deck)
        }
        telemetry.update { it.copy(isFinished = true) }
        generateReport(saveRunIfFirstTime = true)
    }

    fun onReturnToSlides() {
        activeSlideEnterEpochMs = nowMs()
        telemetry.update { it.copy(isFinished = false) }
    }

    fun onToggleMcqOption(slideId: String, optionIndex: Int) {
        val deck = latestDeck ?: return
        val slide = deck.allQuestionSlides().filterIsInstance<SlideUiModel.McqSlide>()
            .firstOrNull { it.id == slideId } ?: return

        telemetry.update { cur ->
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

        telemetry.update { cur ->
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
        telemetry.update { cur ->
            val current = cur.flashcardStates[slideId] ?: FlashcardSlideRuntimeState()
            val updated = current.copy(isFlipped = !current.isFlipped, flipCount = current.flipCount + 1)
            cur.copy(flashcardStates = cur.flashcardStates + (slideId to updated))
        }
    }

    fun onRateFlashcard(slideId: String, rating: String) {
        telemetry.update { cur ->
            val current = cur.flashcardStates[slideId] ?: FlashcardSlideRuntimeState()
            cur.copy(flashcardStates = cur.flashcardStates + (slideId to current.copy(rating = rating)))
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
                    mimeType = "text/markdown",
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

    private fun flushActiveLeafDwellTime(deck: SlideDeck) {
        val enteredAt = activeSlideEnterEpochMs
        val now = nowMs()
        activeSlideEnterEpochMs = now
        if (enteredAt == null) return

        val leafId = activeLeafSlideId(
            deck = deck,
            slideIdx = telemetry.value.currentSlideIndex,
            quizIndices = telemetry.value.activeQuizQuestionIndex
        ) ?: return

        val delta = (now - enteredAt).coerceAtLeast(0L)
        telemetry.update { cur ->
            cur.copy(timeSpentBySlideId = cur.timeSpentBySlideId + (leafId to ((cur.timeSpentBySlideId[leafId] ?: 0L) + delta)))
        }
    }

    private fun generateReport(saveRunIfFirstTime: Boolean) {
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

            val shouldPersist = saveRunIfFirstTime && !telemetry.value.hasAutoSavedRun
            telemetry.update { cur ->
                cur.copy(
                    completedReport = built.report,
                    completedMarkdown = built.markdown,
                    hasAutoSavedRun = cur.hasAutoSavedRun || shouldPersist
                )
            }

            if (shouldPersist) {
                runCatching { analysisRepository.saveCompletedRun(built.report, built.markdown) }
                    .onSuccess { pushMessage("Session analysis saved to History.") }
                    .onFailure { err -> pushMessage(err.message ?: "Failed to save analysis run.") }
            }
        }
    }

    private fun pushMessage(text: String) {
        telemetry.update { cur -> cur.copy(userMessages = cur.userMessages.appendMessage(text)) }
    }

    private companion object {
        const val KEY_CURRENT_SLIDE_INDEX = "currentSlideIndex"
        const val KEY_HAS_STARTED_SESSION = "hasStartedSession"
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
