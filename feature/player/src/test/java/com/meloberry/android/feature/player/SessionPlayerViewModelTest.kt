package com.meloberry.android.feature.player

import androidx.lifecycle.SavedStateHandle
import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.ArticleBlockModel
import com.meloberry.android.core.model.ComponentKind
import com.meloberry.android.core.model.ComponentModel
import com.meloberry.android.core.model.FlashcardQuestionModel
import com.meloberry.android.core.model.FlashcardSideModel
import com.meloberry.android.core.model.McqOptionModel
import com.meloberry.android.core.model.McqQuestionModel
import com.meloberry.android.core.model.MediaPosition
import com.meloberry.android.core.model.SavedAnalysisRun
import com.meloberry.android.core.model.SessionAnalysisReport
import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.WorkspaceKind
import com.meloberry.android.core.model.WorkspaceSummary
import com.meloberry.android.data.analysis.AnalysisRepository
import com.meloberry.android.data.workspace.WorkspaceRepository
import com.meloberry.android.domain.BuildAnalysisReportUseCase
import com.meloberry.android.domain.BuildSlideDeckUseCase
import com.meloberry.android.domain.EvaluateMcqAnswerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun playerLifecycle_tracksDwellTime_scoring_flashcardRating_andStateDrivenExports() = runTest(testDispatcher) {
        var currentTimeMs = 1_000L
        val fakeWorkspaceRepo = FakeWorkspaceRepository(sampleWorkspace())
        val fakeAnalysisRepo = FakeAnalysisRepository()
        val savedStateHandle = SavedStateHandle(mapOf("workspaceId" to "Session_Test_1"))

        val vm = SessionPlayerViewModel(
            savedStateHandle = savedStateHandle,
            workspaceRepository = fakeWorkspaceRepo,
            analysisRepository = fakeAnalysisRepo,
            buildSlideDeckUseCase = BuildSlideDeckUseCase(testDispatcher),
            evaluateMcqAnswerUseCase = EvaluateMcqAnswerUseCase(),
            buildAnalysisReportUseCase = BuildAnalysisReportUseCase(testDispatcher),
            nowMs = { currentTimeMs }
        )

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.slides.size)
        assertEquals(1, vm.uiState.value.totalMcqCount)
        assertEquals(0, vm.uiState.value.currentSlideIndex)
        assertFalse(vm.uiState.value.isFinished)

        // Advance time on Article slide (slide 0), move to bundled Quiz slide (slide 1)
        currentTimeMs = 4_500L
        vm.onSlideChanged(1)
        advanceUntilIdle()
        assertEquals(1, savedStateHandle.get<Int>("currentSlideIndex"))

        // Wrong first try on MCQ (Q0), then toggle clears checked UI before second try
        vm.onToggleMcqOption("Q_Mcq1", 1)
        vm.onCheckMcqAnswer("Q_Mcq1")
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.currentMcqScore)
        assertEquals(true, vm.uiState.value.mcqStates["Q_Mcq1"]?.isChecked)
        assertEquals(false, vm.uiState.value.mcqStates["Q_Mcq1"]?.firstTryCorrect)

        vm.onToggleMcqOption("Q_Mcq1", 2)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.mcqStates["Q_Mcq1"]?.isChecked)
        assertEquals(1, vm.uiState.value.mcqStates["Q_Mcq1"]?.checkAttempts)

        vm.onCheckMcqAnswer("Q_Mcq1")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.currentMcqScore)
        assertEquals(true, vm.uiState.value.mcqStates["Q_Mcq1"]?.isChecked)
        assertEquals(true, vm.uiState.value.mcqStates["Q_Mcq1"]?.isCorrect)
        assertEquals(false, vm.uiState.value.mcqStates["Q_Mcq1"]?.firstTryCorrect)

        // Switch inside bundled Quiz slide to Q1 (Flashcard), flip and mark needsReview
        currentTimeMs = 8_000L
        vm.onSelectQuizQuestion("Comp_2", 1)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeQuizQuestionIndex["Comp_2"])
        vm.onFlipFlashcard("Q_Card1")
        vm.onRateFlashcard("Q_Card1", "needsReview")
        advanceUntilIdle()

        // Jump straight back to Article in 1 step from Q1 inside the quiz
        currentTimeMs = 10_000L
        vm.onSlideChanged(0)
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.currentSlideIndex)

        // Finish & Analyze -> transitions to standalone Finish screen and saves report
        currentTimeMs = 11_000L
        vm.onFinishAndAnalyze()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isFinished)
        val report = vm.uiState.value.completedReport
        assertNotNull(report)
        assertEquals(1, fakeAnalysisRepo.savedRuns.size)
        assertEquals(listOf("Q_Card1"), report!!.weakItemIds)
        assertEquals(4_500L, report.slides[0].timeSpentMs)
        assertEquals(3_500L, report.slides[1].timeSpentMs)
        assertEquals(2_000L, report.slides[2].timeSpentMs)
        assertEquals(100.0, report.summary.mcqAccuracyPercent, 0.001)
        assertEquals(0, report.summary.mcqCorrectFirstTry)
        assertEquals(1, report.summary.mcqCorrectFinal)
        assertTrue(vm.uiState.value.userMessages.isNotEmpty())

        vm.onReturnToSlides()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isFinished)

        val msgId = vm.uiState.value.userMessages.first().id
        vm.onMessageShown(msgId)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.userMessages.isEmpty())

        vm.onRequestExportJson()
        advanceUntilIdle()
        assertEquals("Session_Test_1.analysis.json", vm.uiState.value.pendingExport?.suggestedFileName)
        vm.onExportConsumed()
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingExport)

        collectJob.cancel()
    }

    private fun sampleWorkspace() = StudyWorkspace(
        id = "Session_Test_1",
        title = "Test Session",
        kind = WorkspaceKind.SESSION,
        subType = "session",
        activeComponentId = "Comp_1",
        components = listOf(
            ComponentModel(
                id = "Comp_1",
                index = 1,
                kind = ComponentKind.ARTICLE,
                quizType = null,
                questions = emptyList(),
                articleBlocks = listOf(
                    ArticleBlockModel("Art_1", 1, ArticleBlockKind.TEXT, "# Hello", null)
                ),
                layoutOverrides = emptyMap()
            ),
            ComponentModel(
                id = "Comp_2",
                index = 2,
                kind = ComponentKind.QUIZ,
                quizType = "mixed",
                questions = listOf(
                    McqQuestionModel(
                        id = "Q_Mcq1",
                        index = 1,
                        promptText = "2+2?",
                        maxOptions = 2,
                        isMultiCorrect = false,
                        options = listOf(
                            McqOptionModel(1, "3", null, false),
                            McqOptionModel(2, "4", null, true)
                        ),
                        layoutOverrides = emptyMap()
                    ),
                    FlashcardQuestionModel(
                        id = "Q_Card1",
                        index = 2,
                        front = FlashcardSideModel("Front", null, MediaPosition.BELOW_IMAGE),
                        back = FlashcardSideModel("Back", null, MediaPosition.BELOW_IMAGE),
                        layoutOverrides = emptyMap()
                    )
                ),
                articleBlocks = emptyList(),
                layoutOverrides = emptyMap()
            )
        ),
        standaloneQuestions = emptyList(),
        globalLayouts = emptyMap(),
        storageDirPath = "/tmp/Session_Test_1"
    )

    private class FakeWorkspaceRepository(
        private val workspace: StudyWorkspace
    ) : WorkspaceRepository {
        override val workspacesStream: Flow<List<WorkspaceSummary>> = flowOf(emptyList())
        override fun observeWorkspace(id: String): Flow<StudyWorkspace?> =
            flowOf(if (id == workspace.id) workspace else null)
        override suspend fun importPackageFromUri(uriString: String) = Result.failure<WorkspaceSummary>(UnsupportedOperationException())
        override suspend fun ensureSampleWorkspaceSeeded() = Unit
        override suspend fun deleteWorkspace(id: String) = Unit
    }

    private class FakeAnalysisRepository : AnalysisRepository {
        val savedRuns = mutableListOf<SavedAnalysisRun>()
        private val stream = MutableStateFlow<List<SavedAnalysisRun>>(emptyList())

        override fun observeAllRuns(): Flow<List<SavedAnalysisRun>> = stream
        override suspend fun saveCompletedRun(report: SessionAnalysisReport, markdown: String): SavedAnalysisRun {
            val saved = SavedAnalysisRun("run_${savedRuns.size + 1}", report, markdown, 1000L)
            savedRuns += saved
            stream.value = savedRuns.toList()
            return saved
        }
        override suspend fun serializeReportJson(report: SessionAnalysisReport): String = "{\"workspaceId\":\"${report.workspaceId}\"}"
        override suspend fun writeExportToUri(uriString: String, content: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteRun(runId: String) = Unit
    }
}
