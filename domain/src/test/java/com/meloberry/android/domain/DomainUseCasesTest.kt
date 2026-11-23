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
    fun buildAnalysisReportUseCase_defaultsAccuracyTo100WhenZeroMcqs_andFlagsWeakItems() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reportUseCase = BuildAnalysisReportUseCase(dispatcher)
        val aggUseCase = AggregateAnalysisHistoryUseCase(dispatcher)

        val noMcqResult = reportUseCase(
            workspaceId = "Set_Cards",
            title = "Flashcards",
            startedAtEpochMs = 1_000L,
            completedAtEpochMs = 6_000L,
            slideEntries = listOf(
                SlideAnalysisEntry(
                    slideIndex = 1,
                    id = "Q_Card1",
                    kind = "flashcard",
                    timeSpentMs = 4_000L,
                    visitedCount = 1,
                    flipCount = 2,
                    flashcardRating = "needsReview"
                )
            )
        )

        assertEquals("1.0", noMcqResult.report.schemaVersion)
        assertEquals(100.0, noMcqResult.report.summary.mcqAccuracyPercent, 0.001)
        assertEquals(listOf("Q_Card1"), noMcqResult.report.weakItemIds)
        assertTrue(noMcqResult.markdown.contains("# Meloberry Session Analysis — Set_Cards"))
        assertTrue(noMcqResult.markdown.contains("## Weak / Review Items"))

        val agg = aggUseCase(
            listOf(
                SavedAnalysisRun("run1", noMcqResult.report, noMcqResult.markdown, 6_000L),
                SavedAnalysisRun("run2", noMcqResult.report, noMcqResult.markdown, 12_000L)
            )
        )
        assertEquals(2, agg.totalRuns)
        assertEquals(100.0, agg.averageAccuracyPercent, 0.001)
        assertEquals(1, agg.recurringWeakItems.size)
        assertEquals(2, agg.recurringWeakItems[0].flaggedCount)
    }
}
