package com.meloberry.android.feature.player

import androidx.lifecycle.SavedStateHandle
import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.ArticleBlockModel
import com.meloberry.android.core.model.AssertionReasonPayload
import com.meloberry.android.core.model.AssertionReasonQuestionModel
import com.meloberry.android.core.model.ComponentKind
import com.meloberry.android.core.model.ComponentModel
import com.meloberry.android.core.model.DiffCellSpec
import com.meloberry.android.core.model.DifferentiationPayload
import com.meloberry.android.core.model.DifferentiationQuestionModel
import com.meloberry.android.core.model.FillBlankPayload
import com.meloberry.android.core.model.FillBlankQuestionModel
import com.meloberry.android.core.model.FillBlankSlot
import com.meloberry.android.core.model.FlashcardQuestionModel
import com.meloberry.android.core.model.FlashcardSideModel
import com.meloberry.android.core.model.McqOptionModel
import com.meloberry.android.core.model.McqQuestionModel
import com.meloberry.android.core.model.MechanismPayload
import com.meloberry.android.core.model.MechanismQuestionModel
import com.meloberry.android.core.model.MechanismStep
import com.meloberry.android.core.model.MediaPosition
import com.meloberry.android.core.model.RelabelBox
import com.meloberry.android.core.model.RelabelImagePayload
import com.meloberry.android.core.model.RelabelImageQuestionModel
import com.meloberry.android.core.model.SavedAnalysisRun
import com.meloberry.android.core.model.SessionAnalysisReport
import com.meloberry.android.core.model.SpotErrorPayload
import com.meloberry.android.core.model.SpotErrorQuestionModel
import com.meloberry.android.core.model.SpotErrorSegment
import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.WorkspaceKind
import com.meloberry.android.core.model.WorkspaceSummary
import com.meloberry.android.data.analysis.AnalysisRepository
import com.meloberry.android.data.workspace.WorkspaceLoadResult
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
        assertFalse(vm.uiState.value.hasStartedSession)
        assertFalse(vm.uiState.value.isFinished)

        // Start session from Greeting Card -> starts session clock & Slide 0 dwell timer
        vm.onStartSession()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasStartedSession)

        // Advance time on Article slide (slide 0), move to bundled Quiz slide (slide 1)
        currentTimeMs = 4_500L
        vm.onSlideChanged(1)
        advanceUntilIdle()
        assertEquals(1, savedStateHandle.get<Int>("currentSlideIndex"))

        // Wrong first try on MCQ (Q0), then toggle keeps isChecked true (matching CLI strictly) until re-checked
        vm.onToggleMcqOption("Q_Mcq1", 1)
        vm.onCheckMcqAnswer("Q_Mcq1")
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.currentMcqScore)
        assertEquals(true, vm.uiState.value.mcqStates["Q_Mcq1"]?.isChecked)
        assertEquals(false, vm.uiState.value.mcqStates["Q_Mcq1"]?.firstTryCorrect)

        vm.onToggleMcqOption("Q_Mcq1", 2)
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.mcqStates["Q_Mcq1"]?.isChecked)
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

    @Test
    fun reFinish_updatesExistingRunInPlace_insteadOfDiscardingOrDuplicating() = runTest(testDispatcher) {
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

        vm.onStartSession()
        vm.onSlideChanged(1)
        vm.onToggleMcqOption("Q_Mcq1", 1) // wrong answer
        vm.onCheckMcqAnswer("Q_Mcq1")
        currentTimeMs = 5_000L
        vm.onFinishAndAnalyze()
        advanceUntilIdle()

        assertEquals(1, fakeAnalysisRepo.savedRuns.size)
        val firstRunId = fakeAnalysisRepo.savedRuns.single().runId
        assertEquals(0, fakeAnalysisRepo.savedRuns.single().report.summary.mcqCorrectFinal)

        // Return to slides, fix MCQ answer, and finish again -> must update same runId
        vm.onReturnToSlides()
        vm.onToggleMcqOption("Q_Mcq1", 2)
        vm.onCheckMcqAnswer("Q_Mcq1")
        currentTimeMs = 9_000L
        vm.onFinishAndAnalyze()
        advanceUntilIdle()

        assertEquals(1, fakeAnalysisRepo.savedRuns.size)
        assertEquals(firstRunId, fakeAnalysisRepo.savedRuns.single().runId)
        assertEquals(1, fakeAnalysisRepo.savedRuns.single().report.summary.mcqCorrectFinal)
        assertEquals(100.0, fakeAnalysisRepo.savedRuns.single().report.summary.mcqAccuracyPercent, 0.001)

        collectJob.cancel()
    }

    @Test
    fun lifecyclePauseResume_excludesBackgroundDwellTime_andRestoresAcrossProcessDeath() = runTest(testDispatcher) {
        var currentTimeMs = 1_000L
        val fakeWorkspaceRepo = FakeWorkspaceRepository(sampleWorkspace())
        val fakeAnalysisRepo = FakeAnalysisRepository()
        val savedStateHandle = SavedStateHandle(mapOf("workspaceId" to "Session_Test_1"))

        val vm1 = SessionPlayerViewModel(
            savedStateHandle = savedStateHandle,
            workspaceRepository = fakeWorkspaceRepo,
            analysisRepository = fakeAnalysisRepo,
            buildSlideDeckUseCase = BuildSlideDeckUseCase(testDispatcher),
            evaluateMcqAnswerUseCase = EvaluateMcqAnswerUseCase(),
            buildAnalysisReportUseCase = BuildAnalysisReportUseCase(testDispatcher),
            nowMs = { currentTimeMs }
        )
        val job1 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm1.uiState.collect {}
        }
        advanceUntilIdle()

        vm1.onStartSession()
        currentTimeMs = 3_000L // 2_000ms active on slide 0
        vm1.onPauseTelemetry()

        // App stays backgrounded for 60 seconds
        currentTimeMs = 63_000L
        vm1.onResumeTelemetry()

        currentTimeMs = 64_500L // +1_500ms active on slide 0 = 3_500ms total
        // Simulate stale pager index: call onSelectQuizQuestion directly while currentSlideIndex is still 0
        vm1.onSelectQuizQuestion("Comp_2", 1)
        vm1.onFlipFlashcard("Q_Card1")
        vm1.onRateFlashcard("Q_Card1", "known")
        advanceUntilIdle()
        assertEquals(1, vm1.uiState.value.currentSlideIndex)
        assertEquals(1, vm1.uiState.value.activeQuizQuestionIndex["Comp_2"])

        // Simulate process death by constructing a fresh ViewModel with the same SavedStateHandle
        currentTimeMs = 66_000L
        vm1.onPauseTelemetry()
        job1.cancel()

        val vm2 = SessionPlayerViewModel(
            savedStateHandle = savedStateHandle,
            workspaceRepository = fakeWorkspaceRepo,
            analysisRepository = fakeAnalysisRepo,
            buildSlideDeckUseCase = BuildSlideDeckUseCase(testDispatcher),
            evaluateMcqAnswerUseCase = EvaluateMcqAnswerUseCase(),
            buildAnalysisReportUseCase = BuildAnalysisReportUseCase(testDispatcher),
            nowMs = { currentTimeMs }
        )
        val job2 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm2.uiState.collect {}
        }
        advanceUntilIdle()

        assertTrue(vm2.uiState.value.hasStartedSession)
        assertEquals(1, vm2.uiState.value.currentSlideIndex)
        assertEquals(1, vm2.uiState.value.activeQuizQuestionIndex["Comp_2"])
        assertEquals("known", vm2.uiState.value.flashcardStates["Q_Card1"]?.rating)

        currentTimeMs = 67_000L
        vm2.onFinishAndAnalyze()
        advanceUntilIdle()

        val report = vm2.uiState.value.completedReport!!
        assertEquals(3_500L, report.slides[0].timeSpentMs) // 60s background time excluded
        assertEquals(2_500L, report.slides[2].timeSpentMs) // 1_500ms before death + 1_000ms after restore

        job2.cancel()
    }

    @Test
    fun corruptWorkspace_surfacesRecoverableErrorWithoutCrashing() = runTest(testDispatcher) {
        val corruptRepo = object : WorkspaceRepository {
            override val workspacesStream: Flow<List<WorkspaceSummary>> = flowOf(emptyList())
            override fun observeWorkspace(id: String): Flow<WorkspaceLoadResult> =
                flowOf(WorkspaceLoadResult.Corrupt(id, "Malformed layouts.json"))
            override suspend fun importPackageFromUri(uriString: String) = Result.failure<WorkspaceSummary>(UnsupportedOperationException())
            override suspend fun ensureSampleWorkspaceSeeded() = Unit
            override suspend fun deleteWorkspace(id: String) = Unit
        }
        val vm = SessionPlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("workspaceId" to "Session_Bad")),
            workspaceRepository = corruptRepo,
            analysisRepository = FakeAnalysisRepository(),
            buildSlideDeckUseCase = BuildSlideDeckUseCase(testDispatcher),
            evaluateMcqAnswerUseCase = EvaluateMcqAnswerUseCase(),
            buildAnalysisReportUseCase = BuildAnalysisReportUseCase(testDispatcher),
            nowMs = { 1_000L }
        )
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.notFound)
        assertEquals("Malformed layouts.json", vm.uiState.value.loadError)
        collectJob.cancel()
    }

    @Test
    fun v2InteractiveQuestions_trackRuntimeState_overrides_andAnalysisSummary() = runTest(testDispatcher) {
        var currentTimeMs = 1_000L
        val v2Workspace = StudyWorkspace(
            id = "Set_V2_Player",
            title = "V2 Interactive Quiz",
            kind = WorkspaceKind.QUIZ,
            subType = "mixed",
            activeComponentId = null,
            components = emptyList(),
            standaloneQuestions = listOf(
                FillBlankQuestionModel(
                    id = "Q_Fb1",
                    index = 1,
                    payload = FillBlankPayload("Enzyme: {{PFK-1}}", listOf(FillBlankSlot(1, listOf("PFK-1")))),
                    layoutOverrides = emptyMap()
                ),
                AssertionReasonQuestionModel(
                    id = "Q_Ar1",
                    index = 2,
                    payload = AssertionReasonPayload("A", "R", "both-explain", "4opt"),
                    layoutOverrides = emptyMap()
                ),
                MechanismQuestionModel(
                    id = "Q_Mech1",
                    index = 3,
                    payload = MechanismPayload(
                        title = "Cascade",
                        steps = listOf(
                            MechanismStep(1, 1, "First", false),
                            MechanismStep(2, 2, "Second A", false),
                            MechanismStep(3, 2, "Second B", false),
                            MechanismStep(4, 0, "Decoy", true)
                        )
                    ),
                    layoutOverrides = emptyMap()
                )
            ),
            globalLayouts = emptyMap(),
            storageDirPath = "/tmp/Set_V2_Player"
        )
        val fakeWorkspaceRepo = FakeWorkspaceRepository(v2Workspace)
        val fakeAnalysisRepo = FakeAnalysisRepository()
        val savedStateHandle = SavedStateHandle(mapOf("workspaceId" to "Set_V2_Player"))

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

        assertEquals(3, vm.uiState.value.totalMcqCount)
        vm.onStartSession()

        // FillBlank wrong then manual override
        vm.onUpdateFillBlankInput("Q_Fb1", 1, "wrong")
        vm.onCheckFillBlank("Q_Fb1")
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.fillBlankStates["Q_Fb1"]?.isCorrect)
        assertEquals(false, vm.uiState.value.fillBlankStates["Q_Fb1"]?.firstTryCorrect)
        vm.onOverrideFillBlankCorrect("Q_Fb1")
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.fillBlankStates["Q_Fb1"]?.isCorrect)
        assertEquals(true, vm.uiState.value.fillBlankStates["Q_Fb1"]?.overriddenCorrect)

        // AssertionReason correct first try
        vm.onSelectAssertionReason("Q_Ar1", "both-explain")
        vm.onCheckAssertionReason("Q_Ar1")
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.assertionReasonStates["Q_Ar1"]?.firstTryCorrect)

        // Mechanism parallel permutation correct first try
        vm.onUpdateMechanismSequence("Q_Mech1", bankStepIds = listOf(4), placedStepIds = listOf(1, 3, 2))
        vm.onCheckMechanism("Q_Mech1")
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.mechanismStates["Q_Mech1"]?.isCorrect)

        assertEquals(3, vm.uiState.value.currentMcqScore)

        currentTimeMs = 5_000L
        vm.onFinishAndAnalyze()
        advanceUntilIdle()

        val report = vm.uiState.value.completedReport!!
        assertEquals(3, report.summary.mcqTotal)
        assertEquals(3, report.summary.mcqCorrectFinal)
        assertEquals(2, report.summary.mcqCorrectFirstTry)
        assertTrue(report.weakItemIds.isEmpty())
        assertTrue(vm.uiState.value.completedMarkdown!!.contains("- **Graded Questions:** 3 / 3 correct"))
        assertTrue(vm.uiState.value.completedMarkdown!!.contains("| 3 | `Q_Mech1` | mechanism |"))

        collectJob.cancel()
    }

    @Test
    fun v2AllSixQuestionTypes_fullModesPerSlotOverrideAndProcessDeathRestoration() = runTest(testDispatcher) {
        var currentTimeMs = 1_000L
        val v2AllWorkspace = StudyWorkspace(
            id = "Set_V2_All6",
            title = "All Six V2 Types",
            kind = WorkspaceKind.QUIZ,
            subType = "mixed",
            activeComponentId = null,
            components = emptyList(),
            standaloneQuestions = listOf(
                FillBlankQuestionModel(
                    id = "Q_Fb2",
                    index = 1,
                    payload = FillBlankPayload(
                        template = "{{alpha}} and {{nephron}}",
                        blanks = listOf(
                            FillBlankSlot(1, listOf("alpha")),
                            FillBlankSlot(2, listOf("nephron"))
                        ),
                        fuzzy = true
                    ),
                    layoutOverrides = emptyMap()
                ),
                SpotErrorQuestionModel(
                    id = "Q_SeFix",
                    index = 2,
                    payload = SpotErrorPayload(
                        template = "In [PTH excess], phosphate is [~~high~~ -> low].",
                        segments = listOf(
                            SpotErrorSegment(1, "PTH excess", isCandidate = true, isError = false),
                            SpotErrorSegment(2, "high", isCandidate = true, isError = true, corrections = listOf("low"))
                        ),
                        requireFix = true,
                        allowNoError = true,
                        noError = false
                    ),
                    layoutOverrides = emptyMap()
                ),
                SpotErrorQuestionModel(
                    id = "Q_SeClean",
                    index = 3,
                    payload = SpotErrorPayload(
                        template = "[Normal statement]",
                        segments = listOf(SpotErrorSegment(1, "Normal statement", isCandidate = true, isError = false)),
                        requireFix = false,
                        allowNoError = true,
                        noError = true
                    ),
                    layoutOverrides = emptyMap()
                ),
                DifferentiationQuestionModel(
                    id = "Q_DiffFill",
                    index = 4,
                    payload = DifferentiationPayload(
                        mode = "fill",
                        headers = listOf("Feature", "Type A"),
                        alignments = listOf("left", "left"),
                        rows = listOf(listOf("Transport", "{{Active}}")),
                        specialCells = listOf(DiffCellSpec(0, 1, "{{Active}}", isBlank = true, blankAnswers = listOf("Active")))
                    ),
                    layoutOverrides = emptyMap()
                ),
                DifferentiationQuestionModel(
                    id = "Q_DiffSpot",
                    index = 5,
                    payload = DifferentiationPayload(
                        mode = "spot-wrong",
                        allowNoError = true,
                        headers = listOf("Feature", "A", "B"),
                        alignments = listOf("left", "left", "left"),
                        rows = listOf(listOf("Row", "Wrong", "Ok")),
                        specialCells = listOf(DiffCellSpec(0, 1, "~~Wrong~~ -> Right", isError = true, wrongText = "Wrong", corrections = listOf("Right")))
                    ),
                    layoutOverrides = emptyMap()
                ),
                DifferentiationQuestionModel(
                    id = "Q_DiffRearr",
                    index = 6,
                    payload = DifferentiationPayload(
                        mode = "rearrange",
                        headers = listOf("Feature", "A", "B"),
                        alignments = listOf("left", "left", "left"),
                        rows = listOf(listOf("Marker", "Pos", "Neg"))
                    ),
                    layoutOverrides = emptyMap()
                ),
                RelabelImageQuestionModel(
                    id = "Q_RelReveal",
                    index = 7,
                    payload = RelabelImagePayload(
                        imagePath = "/tmp/img.png",
                        imageFilePath = "/tmp/img.png",
                        mode = "reveal",
                        style = "solid",
                        boxes = listOf(RelabelBox(1, "Aorta", emptyList(), 0.1, 0.1, 0.2, 0.1))
                    ),
                    layoutOverrides = emptyMap()
                ),
                RelabelImageQuestionModel(
                    id = "Q_RelBank",
                    index = 8,
                    payload = RelabelImagePayload(
                        imagePath = "/tmp/img.png",
                        imageFilePath = "/tmp/img.png",
                        mode = "bank",
                        style = "blur",
                        boxes = listOf(RelabelBox(1, "Glomerulus", listOf("Tuft"), 0.1, 0.1, 0.2, 0.1)),
                        distractors = listOf("Tubule")
                    ),
                    layoutOverrides = emptyMap()
                ),
                RelabelImageQuestionModel(
                    id = "Q_RelRecall",
                    index = 9,
                    payload = RelabelImagePayload(
                        imagePath = "/tmp/img.png",
                        imageFilePath = "/tmp/img.png",
                        mode = "recall",
                        style = "whiteout",
                        boxes = listOf(RelabelBox(1, "Mitochondria", emptyList(), 0.1, 0.1, 0.2, 0.1)),
                        fuzzy = true
                    ),
                    layoutOverrides = emptyMap()
                )
            ),
            globalLayouts = emptyMap(),
            storageDirPath = "/tmp/Set_V2_All6"
        )

        val fakeWorkspaceRepo = FakeWorkspaceRepository(v2AllWorkspace)
        val fakeAnalysisRepo = FakeAnalysisRepository()
        val savedStateHandle = SavedStateHandle(mapOf("workspaceId" to "Set_V2_All6"))

        val vm1 = SessionPlayerViewModel(
            savedStateHandle = savedStateHandle,
            workspaceRepository = fakeWorkspaceRepo,
            analysisRepository = fakeAnalysisRepo,
            buildSlideDeckUseCase = BuildSlideDeckUseCase(testDispatcher),
            evaluateMcqAnswerUseCase = EvaluateMcqAnswerUseCase(),
            buildAnalysisReportUseCase = BuildAnalysisReportUseCase(testDispatcher),
            nowMs = { currentTimeMs }
        )
        val job1 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm1.uiState.collect {}
        }
        advanceUntilIdle()

        assertEquals(9, vm1.uiState.value.totalMcqCount)
        vm1.onStartSession()

        // 1. FillBlank: slot 1 exact, slot 2 wrong -> per-slot override makes it correct
        vm1.onUpdateFillBlankInput("Q_Fb2", 1, "alpha")
        vm1.onUpdateFillBlankInput("Q_Fb2", 2, "wrong")
        vm1.onCheckFillBlank("Q_Fb2")
        advanceUntilIdle()
        assertEquals(false, vm1.uiState.value.fillBlankStates["Q_Fb2"]?.isCorrect)
        vm1.onOverrideFillBlankSlot("Q_Fb2", 2)
        advanceUntilIdle()
        assertEquals(true, vm1.uiState.value.fillBlankStates["Q_Fb2"]?.isCorrect)
        assertEquals(true, vm1.uiState.value.fillBlankStates["Q_Fb2"]?.overriddenCorrect)
        assertEquals(setOf(2), vm1.uiState.value.fillBlankStates["Q_Fb2"]?.overriddenSlotIndices)

        // 2. SpotError with requireFix
        vm1.onToggleSpotErrorSpan("Q_SeFix", 2)
        vm1.onUpdateSpotErrorFix("Q_SeFix", 2, "low")
        vm1.onCheckSpotError("Q_SeFix")
        advanceUntilIdle()
        assertEquals(true, vm1.uiState.value.spotErrorStates["Q_SeFix"]?.isCorrect)

        // 3. SpotError noError mode
        vm1.onToggleSpotErrorNoError("Q_SeClean")
        vm1.onCheckSpotError("Q_SeClean")
        advanceUntilIdle()
        assertEquals(true, vm1.uiState.value.spotErrorStates["Q_SeClean"]?.isCorrect)

        // 4. Differentiation fill (wrong then override clears anyFuzzy & marks correct)
        vm1.onUpdateDiffFillInput("Q_DiffFill", 0, 1, "wrong")
        vm1.onCheckDifferentiation("Q_DiffFill")
        advanceUntilIdle()
        assertEquals(false, vm1.uiState.value.differentiationStates["Q_DiffFill"]?.isCorrect)
        vm1.onOverrideDifferentiationCorrect("Q_DiffFill")
        advanceUntilIdle()
        assertEquals(true, vm1.uiState.value.differentiationStates["Q_DiffFill"]?.isCorrect)
        assertEquals(true, vm1.uiState.value.differentiationStates["Q_DiffFill"]?.overriddenCorrect)
        assertEquals(false, vm1.uiState.value.differentiationStates["Q_DiffFill"]?.anyFuzzy)

        // 5. Differentiation spot-wrong
        vm1.onToggleDiffErrorCell("Q_DiffSpot", 0, 1)
        vm1.onCheckDifferentiation("Q_DiffSpot")
        advanceUntilIdle()
        assertEquals(true, vm1.uiState.value.differentiationStates["Q_DiffSpot"]?.isCorrect)

        // 6. Differentiation rearrange
        vm1.onUpdateDiffRearrangeRows("Q_DiffRearr", listOf(listOf("Marker", "Pos", "Neg")))
        vm1.onCheckDifferentiation("Q_DiffRearr")
        advanceUntilIdle()
        assertEquals(true, vm1.uiState.value.differentiationStates["Q_DiffRearr"]?.isCorrect)

        // 7. RelabelImage reveal
        vm1.onToggleRelabelRevealBox("Q_RelReveal", 1)
        vm1.onRateRelabelReveal("Q_RelReveal", "known")
        advanceUntilIdle()
        assertEquals(true, vm1.uiState.value.relabelImageStates["Q_RelReveal"]?.isCorrect)

        // 8. RelabelImage bank (alias accepted)
        vm1.onAssignRelabelBankLabel("Q_RelBank", 1, "Tuft")
        vm1.onCheckRelabelImage("Q_RelBank")
        advanceUntilIdle()
        assertEquals(true, vm1.uiState.value.relabelImageStates["Q_RelBank"]?.isCorrect)

        // 9. RelabelImage recall (fuzzy then override)
        vm1.onUpdateRelabelRecallInput("Q_RelRecall", 1, "Mitochondra")
        vm1.onCheckRelabelImage("Q_RelRecall")
        advanceUntilIdle()
        assertEquals(true, vm1.uiState.value.relabelImageStates["Q_RelRecall"]?.isCorrect)
        assertEquals(true, vm1.uiState.value.relabelImageStates["Q_RelRecall"]?.anyFuzzy)
        assertEquals(9, vm1.uiState.value.currentMcqScore)

        // Simulate process death & verify full SavedStateHandle restoration for all 6 new question states
        vm1.onPauseTelemetry()
        job1.cancel()

        val vm2 = SessionPlayerViewModel(
            savedStateHandle = savedStateHandle,
            workspaceRepository = fakeWorkspaceRepo,
            analysisRepository = fakeAnalysisRepo,
            buildSlideDeckUseCase = BuildSlideDeckUseCase(testDispatcher),
            evaluateMcqAnswerUseCase = EvaluateMcqAnswerUseCase(),
            buildAnalysisReportUseCase = BuildAnalysisReportUseCase(testDispatcher),
            nowMs = { currentTimeMs }
        )
        val job2 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm2.uiState.collect {}
        }
        advanceUntilIdle()

        assertEquals(9, vm2.uiState.value.currentMcqScore)
        assertEquals(setOf(2), vm2.uiState.value.fillBlankStates["Q_Fb2"]?.overriddenSlotIndices)
        assertEquals(mapOf(2 to "low"), vm2.uiState.value.spotErrorStates["Q_SeFix"]?.fixesBySpan)
        assertEquals(true, vm2.uiState.value.spotErrorStates["Q_SeClean"]?.pickedNoError)
        assertEquals(true, vm2.uiState.value.differentiationStates["Q_DiffFill"]?.overriddenCorrect)
        assertEquals(setOf("0:1"), vm2.uiState.value.differentiationStates["Q_DiffSpot"]?.selectedErrorCells)
        assertEquals(listOf(listOf("Marker", "Pos", "Neg")), vm2.uiState.value.differentiationStates["Q_DiffRearr"]?.rearrangeRows)
        assertEquals("known", vm2.uiState.value.relabelImageStates["Q_RelReveal"]?.rating)
        assertEquals(mapOf(1 to "Tuft"), vm2.uiState.value.relabelImageStates["Q_RelBank"]?.assignedLabelsByBox)
        assertEquals(true, vm2.uiState.value.relabelImageStates["Q_RelRecall"]?.anyFuzzy)

        currentTimeMs = 6_000L
        vm2.onFinishAndAnalyze()
        advanceUntilIdle()

        val report = vm2.uiState.value.completedReport!!
        assertEquals(9, report.summary.mcqTotal)
        assertEquals(9, report.summary.mcqCorrectFinal)
        assertEquals(7, report.summary.mcqCorrectFirstTry)
        assertTrue(report.weakItemIds.isEmpty())
        assertTrue(vm2.uiState.value.completedMarkdown!!.contains("| 2 | `Q_SeFix` | spot-error |"))
        assertTrue(vm2.uiState.value.completedMarkdown!!.contains("| 4 | `Q_DiffFill` | diff |"))
        assertTrue(vm2.uiState.value.completedMarkdown!!.contains("| 9 | `Q_RelRecall` | relabel |"))

        job2.cancel()
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
        override fun observeWorkspace(id: String): Flow<WorkspaceLoadResult> =
            flowOf(if (id == workspace.id) WorkspaceLoadResult.Loaded(workspace) else WorkspaceLoadResult.NotFound)
        override suspend fun importPackageFromUri(uriString: String) = Result.failure<WorkspaceSummary>(UnsupportedOperationException())
        override suspend fun ensureSampleWorkspaceSeeded() = Unit
        override suspend fun deleteWorkspace(id: String) = Unit
    }

    private class FakeAnalysisRepository : AnalysisRepository {
        val savedRuns = mutableListOf<SavedAnalysisRun>()
        private val stream = MutableStateFlow<List<SavedAnalysisRun>>(emptyList())

        override fun observeAllRuns(): Flow<List<SavedAnalysisRun>> = stream
        override suspend fun saveCompletedRun(
            report: SessionAnalysisReport,
            markdown: String,
            existingRunId: String?
        ): SavedAnalysisRun {
            val runId = existingRunId ?: "run_${savedRuns.size + 1}"
            val saved = SavedAnalysisRun(runId, report, markdown, 1000L)
            val existingIdx = savedRuns.indexOfFirst { it.runId == runId }
            if (existingIdx >= 0) {
                savedRuns[existingIdx] = saved
            } else {
                savedRuns += saved
            }
            stream.value = savedRuns.toList()
            return saved
        }
        override suspend fun serializeReportJson(report: SessionAnalysisReport): String = "{\"workspaceId\":\"${report.workspaceId}\"}"
        override suspend fun writeExportToUri(uriString: String, content: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteRun(runId: String) = Unit
    }
}
