package com.meloberry.android.domain

import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.ArticleBlockModel
import com.meloberry.android.core.model.ComponentKind
import com.meloberry.android.core.model.ComponentModel
import com.meloberry.android.core.model.ContentAlign
import com.meloberry.android.core.model.FlashcardQuestionModel
import com.meloberry.android.core.model.FlashcardSideModel
import com.meloberry.android.core.model.LayoutTargetType
import com.meloberry.android.core.model.McqOptionModel
import com.meloberry.android.core.model.McqQuestionModel
import com.meloberry.android.core.model.MediaPosition
import com.meloberry.android.core.model.MeloberryVersionInfo
import com.meloberry.android.core.model.SavedAnalysisRun
import com.meloberry.android.core.model.SlideAnalysisEntry
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.ThemeMode
import com.meloberry.android.core.model.WorkspaceKind
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUseCasesTest {

    @Test
    fun layoutBoundsCatalog_mergesThreeTiers_andClampsOutOfBoundsValues() {
        val global = mapOf(
            LayoutTargetType.MCQ to mapOf(
                "cardMaxWidthPx" to "5000", // clamped to 1400
                "optionColumns" to "1",
                "themeMode" to "light",
                "contentAlign" to "center"
            )
        )
        val componentOverrides = mapOf(
            "optionColumns" to "2",
            "cardPaddingPx" to "40"
        )
        val questionOverrides = mapOf(
            "cardPaddingPx" to "24",
            "optionGapPx" to "2" // clamped to min 6
        )

        val resolved = LayoutBoundsCatalog.merge(
            globalLayouts = global,
            targetType = LayoutTargetType.MCQ,
            componentOverrides = componentOverrides,
            questionOverrides = questionOverrides
        )

        assertEquals(1400, resolved.cardMaxWidthDp)
        assertEquals(24, resolved.cardPaddingDp)
        assertEquals(2, resolved.optionColumns)
        assertEquals(6, resolved.optionGapDp)
        assertEquals(ThemeMode.LIGHT, resolved.themeMode)
        assertEquals(ContentAlign.CENTER, resolved.contentAlign)
    }

    @Test
    fun buildSlideDeckUseCase_flattensSessionComponents_andAppliesOverrides() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = BuildSlideDeckUseCase(dispatcher)

        val workspace = StudyWorkspace(
            id = "Session_1",
            title = "Meloberry Session",
            description = "Physics overview",
            kind = WorkspaceKind.SESSION,
            subType = "session",
            activeComponentId = "Component_1",
            components = listOf(
                ComponentModel(
                    id = "Component_1",
                    index = 1,
                    kind = ComponentKind.ARTICLE,
                    quizType = null,
                    questions = emptyList(),
                    articleBlocks = listOf(
                        ArticleBlockModel("ArtComp_1", 1, ArticleBlockKind.TEXT, "# Intro", null)
                    ),
                    layoutOverrides = mapOf("blockSpacingPx" to "28"),
                    title = "Intro Section"
                ),
                ComponentModel(
                    id = "Component_2",
                    index = 2,
                    kind = ComponentKind.QUIZ,
                    quizType = "mixed",
                    questions = listOf(
                        McqQuestionModel(
                            id = "Q_1",
                            index = 1,
                            promptText = "2 + 2 = ?",
                            maxOptions = 2,
                            isMultiCorrect = false,
                            options = listOf(
                                McqOptionModel(1, "3", null, false),
                                McqOptionModel(2, "4", null, true)
                            ),
                            layoutOverrides = mapOf("optionColumns" to "2"),
                            explanation = "Basic arithmetic"
                        ),
                        FlashcardQuestionModel(
                            id = "Q_2",
                            index = 2,
                            front = FlashcardSideModel("Front", null, MediaPosition.ABOVE_IMAGE),
                            back = FlashcardSideModel("Back", null, MediaPosition.BELOW_IMAGE),
                            layoutOverrides = mapOf("flipDurationMs" to "600")
                        )
                    ),
                    articleBlocks = emptyList(),
                    layoutOverrides = mapOf("cardMaxWidthPx" to "960"),
                    title = "Quick Check"
                )
            ),
            standaloneQuestions = emptyList(),
            globalLayouts = mapOf(
                LayoutTargetType.MCQ to mapOf("themeMode" to "dark", "slideTransitionMs" to "500")
            ),
            storageDirPath = "/tmp/Session_1"
        )

        val deck = useCase(workspace)

        assertEquals("Physics overview", deck.description)
        assertEquals(ThemeMode.DARK, deck.themeMode)
        assertEquals(500, deck.slideTransitionMs)
        assertEquals(2, deck.slides.size)

        val articleSlide = deck.slides[0] as SlideUiModel.ArticleSlide
        assertEquals("Intro Section", articleSlide.title)
        assertEquals(28, articleSlide.layout.blockSpacingDp)

        val quizSlide = deck.slides[1] as SlideUiModel.QuizSlide
        assertEquals("Quick Check", quizSlide.title)
        assertEquals(2, quizSlide.questions.size)

        val mcqSlide = quizSlide.questions[0] as SlideUiModel.McqSlide
        assertEquals(960, mcqSlide.layout.cardMaxWidthDp)
        assertEquals(2, mcqSlide.layout.optionColumns)

        val flashcardSlide = quizSlide.questions[1] as SlideUiModel.FlashcardSlide
        assertEquals(960, flashcardSlide.layout.cardMaxWidthDp)
        assertEquals(600, flashcardSlide.layout.flipDurationMs)
    }

    @Test
    fun buildSlideDeckUseCase_resolvesThemeAndTransitionFromArticleOrFlashcardWhenMcqAbsent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = BuildSlideDeckUseCase(dispatcher)

        val workspace = StudyWorkspace(
            id = "Session_article_theme",
            title = "Article Only",
            kind = WorkspaceKind.SESSION,
            subType = "session",
            activeComponentId = "Component_1",
            components = listOf(
                ComponentModel(
                    id = "Component_1",
                    index = 1,
                    kind = ComponentKind.ARTICLE,
                    quizType = null,
                    questions = emptyList(),
                    articleBlocks = listOf(
                        ArticleBlockModel("ArtComp_1", 1, ArticleBlockKind.TEXT, "# Hello", null)
                    ),
                    layoutOverrides = emptyMap()
                )
            ),
            standaloneQuestions = emptyList(),
            globalLayouts = mapOf(
                LayoutTargetType.ARTICLE to mapOf("themeMode" to "light", "slideTransitionMs" to "640")
            ),
            storageDirPath = "/tmp/Session_article_theme"
        )

        val deck = useCase(workspace)
        assertEquals(ThemeMode.LIGHT, deck.themeMode)
        assertEquals(640, deck.slideTransitionMs)
    }

    @Test
    fun buildSlideDeckUseCase_supportsPaperTheme_fullTransitionBounds_andFlashcardQuizComponentLayout() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = BuildSlideDeckUseCase(dispatcher)

        val workspace = StudyWorkspace(
            id = "Session_paper",
            title = "Paper Flashcards",
            kind = WorkspaceKind.SESSION,
            subType = "session",
            activeComponentId = "Comp_1",
            components = listOf(
                ComponentModel(
                    id = "Comp_1",
                    index = 1,
                    kind = ComponentKind.QUIZ,
                    quizType = "flashcard",
                    questions = listOf(
                        FlashcardQuestionModel(
                            id = "Q_1",
                            index = 1,
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
            globalLayouts = mapOf(
                LayoutTargetType.FLASHCARD to mapOf(
                    "themeMode" to "paper",
                    "slideTransitionMs" to "1100",
                    "flipDurationMs" to "750"
                )
            ),
            storageDirPath = "/tmp/Session_paper"
        )

        val deck = useCase(workspace)
        assertEquals(ThemeMode.PAPER, deck.themeMode)
        assertEquals(1100, deck.slideTransitionMs)
        val quizSlide = deck.slides.single() as SlideUiModel.QuizSlide
        assertEquals(750, quizSlide.layout.flipDurationMs)
    }

    @Test
    fun evaluateMcqAnswerUseCase_handlesSingleAndMultiCorrect() {
        val evaluator = EvaluateMcqAnswerUseCase()
        val multi = McqQuestionModel(
            id = "Q_Multi",
            index = 1,
            promptText = "Pick primes",
            maxOptions = 3,
            isMultiCorrect = true,
            options = listOf(
                McqOptionModel(1, "2", null, true),
                McqOptionModel(2, "3", null, true),
                McqOptionModel(3, "4", null, false)
            ),
            layoutOverrides = emptyMap()
        )

        assertFalse(evaluator(multi, setOf(1)).isCorrect)
        assertFalse(evaluator(multi, setOf(1, 2, 3)).isCorrect)
        assertTrue(evaluator(multi, setOf(1, 2)).isCorrect)
    }

    @Test
    fun buildAnalysisReportUseCase_reportsSkippedArticles_andAggregateHistoryAveragesGradedRunsOnly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reportUseCase = BuildAnalysisReportUseCase(dispatcher)
        val aggUseCase = AggregateAnalysisHistoryUseCase(dispatcher)

        val ungradedResult = reportUseCase(
            workspaceId = "Set_Cards",
            title = "Flashcards & Articles",
            startedAtEpochMs = 1_000L,
            completedAtEpochMs = 6_000L,
            slideEntries = listOf(
                SlideAnalysisEntry(
                    slideIndex = 1,
                    id = "Art_Visited",
                    kind = "article",
                    timeSpentMs = 2_000L,
                    visitedCount = 1
                ),
                SlideAnalysisEntry(
                    slideIndex = 2,
                    id = "Art_Skipped",
                    kind = "article",
                    timeSpentMs = 0L,
                    visitedCount = 0
                ),
                SlideAnalysisEntry(
                    slideIndex = 3,
                    id = "Q_Card1",
                    kind = "flashcard",
                    timeSpentMs = 4_000L,
                    visitedCount = 1,
                    flipCount = 2,
                    flashcardRating = "needsReview"
                )
            )
        )

        assertEquals("1.0", ungradedResult.report.schemaVersion)
        assertEquals(100.0, ungradedResult.report.summary.mcqAccuracyPercent, 0.001)
        assertEquals(1, ungradedResult.report.summary.articleSlidesViewed)
        assertEquals(listOf("Q_Card1"), ungradedResult.report.weakItemIds)
        assertTrue(ungradedResult.markdown.contains("# Meloberry Session Analysis — Set_Cards"))
        assertTrue(ungradedResult.markdown.contains("## Weak / Review Items"))
        assertTrue(ungradedResult.markdown.contains("| 1 | `Art_Visited` | article | 2.0 | 1 | Viewed |"))
        assertTrue(ungradedResult.markdown.contains("| 2 | `Art_Skipped` | article | 0.0 | 0 | Skipped |"))

        // Only ungraded runs -> averageAccuracyPercent must be 0.0 (not inflated to 100.0)
        val aggUngradedOnly = aggUseCase(
            listOf(
                SavedAnalysisRun("run1", ungradedResult.report, ungradedResult.markdown, 6_000L),
                SavedAnalysisRun("run2", ungradedResult.report, ungradedResult.markdown, 12_000L)
            )
        )
        assertEquals(2, aggUngradedOnly.totalRuns)
        assertEquals(0, aggUngradedOnly.gradedRunCount)
        assertEquals(0.0, aggUngradedOnly.averageAccuracyPercent, 0.001)
        assertEquals(1, aggUngradedOnly.recurringWeakItems.size)
        assertEquals(2, aggUngradedOnly.recurringWeakItems[0].flaggedCount)

        // Mixed graded + ungraded runs -> average only the graded run (50.0%, not 75.0%)
        val gradedResult = reportUseCase(
            workspaceId = "Set_Quiz",
            title = "Quiz",
            startedAtEpochMs = 1_000L,
            completedAtEpochMs = 11_000L,
            slideEntries = listOf(
                SlideAnalysisEntry(1, "Q_1", "mcq", 3_000L, 1, 1, listOf(1), listOf(1), true, true),
                SlideAnalysisEntry(2, "Q_2", "mcq", 4_000L, 1, 1, listOf(2), listOf(1), false, false)
            )
        )
        val aggMixed = aggUseCase(
            listOf(
                SavedAnalysisRun("run_ungraded", ungradedResult.report, ungradedResult.markdown, 6_000L),
                SavedAnalysisRun("run_graded", gradedResult.report, gradedResult.markdown, 12_000L)
            )
        )
        assertEquals(2, aggMixed.totalRuns)
        assertEquals(1, aggMixed.gradedRunCount)
        assertEquals(50.0, aggMixed.averageAccuracyPercent, 0.001)
    }

    @Test
    fun meloberryVersionInfo_matchesSingleSourceOfTruthVersioning() {
        assertEquals("2.0.1", MeloberryVersionInfo.VERSION_NAME)
        assertEquals(20001, MeloberryVersionInfo.VERSION_CODE)
        assertEquals("2.0", MeloberryVersionInfo.TARGET_PACKAGE_VERSION)
        assertEquals("1.0", MeloberryVersionInfo.LEGACY_PACKAGE_VERSION)
        assertEquals("1.0", MeloberryVersionInfo.ANALYSIS_SCHEMA_VERSION)
        assertTrue(MeloberryVersionInfo.LEGACY_PACKAGE_VERSION in MeloberryVersionInfo.SUPPORTED_PACKAGE_VERSIONS)
        assertTrue(MeloberryVersionInfo.TARGET_PACKAGE_VERSION in MeloberryVersionInfo.SUPPORTED_PACKAGE_VERSIONS)
    }
}
