package com.meloberry.android.domain

import com.meloberry.android.core.model.ComponentKind
import com.meloberry.android.core.model.DefaultDispatcher
import com.meloberry.android.core.model.FlashcardQuestionModel
import com.meloberry.android.core.model.LayoutTargetType
import com.meloberry.android.core.model.McqQuestionModel
import com.meloberry.android.core.model.QuestionModel
import com.meloberry.android.core.model.QuestionSlideItem
import com.meloberry.android.core.model.SlideDeck
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.WorkspaceKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BuildSlideDeckUseCase @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(workspace: StudyWorkspace): SlideDeck = withContext(defaultDispatcher) {
        val slides = mutableListOf<SlideUiModel>()
        var nextSlideIndex = 1

        if (workspace.kind == WorkspaceKind.SESSION) {
            for (comp in workspace.components.sortedBy { it.index }) {
                if (comp.kind == ComponentKind.ARTICLE) {
                    val layout = LayoutBoundsCatalog.merge(
                        globalLayouts = workspace.globalLayouts,
                        targetType = LayoutTargetType.ARTICLE,
                        componentOverrides = comp.layoutOverrides
                    )
                    slides += SlideUiModel.ArticleSlide(
                        id = comp.id,
                        slideIndex = nextSlideIndex++,
                        layout = layout,
                        blocks = comp.articleBlocks.sortedBy { it.index },
                        title = comp.title
                    )
                } else {
                    val questionSlides = comp.questions.sortedBy { it.index }.mapIndexed { idx, q ->
                        buildQuestionSlide(
                            question = q,
                            slideIndex = idx + 1,
                            globalLayouts = workspace.globalLayouts,
                            componentOverrides = comp.layoutOverrides
                        )
                    }
                    if (questionSlides.isNotEmpty()) {
                        val targetType = if (comp.quizType.equals("flashcard", ignoreCase = true)) {
                            LayoutTargetType.FLASHCARD
                        } else {
                            LayoutTargetType.MCQ
                        }
                        val quizLayout = LayoutBoundsCatalog.merge(
                            globalLayouts = workspace.globalLayouts,
                            targetType = targetType,
                            componentOverrides = comp.layoutOverrides
                        )
                        slides += SlideUiModel.QuizSlide(
                            id = comp.id,
                            slideIndex = nextSlideIndex++,
                            layout = quizLayout,
                            quizType = comp.quizType,
                            questions = questionSlides,
                            title = comp.title
                        )
                    }
                }
            }
        } else {
            for (q in workspace.standaloneQuestions.sortedBy { it.index }) {
                slides += buildQuestionSlide(
                    question = q,
                    slideIndex = nextSlideIndex++,
                    globalLayouts = workspace.globalLayouts,
                    componentOverrides = emptyMap()
                )
            }
        }

        SlideDeck(
            workspaceId = workspace.id,
            title = workspace.title,
            description = workspace.description,
            themeMode = resolveGlobalLayoutValue(
                globalLayouts = workspace.globalLayouts,
                key = "themeMode",
                defaultValue = "berry",
                parser = LayoutBoundsCatalog::parseThemeMode
            ),
            slideTransitionMs = resolveGlobalLayoutValue(
                globalLayouts = workspace.globalLayouts,
                key = "slideTransitionMs",
                defaultValue = "380",
                parser = LayoutBoundsCatalog::parseSlideTransitionMs
            ),
            slides = slides
        )
    }

    private fun <T> resolveGlobalLayoutValue(
        globalLayouts: Map<LayoutTargetType, Map<String, String>>,
        key: String,
        defaultValue: String,
        parser: (String?) -> T
    ): T {
        val order = listOf(LayoutTargetType.MCQ, LayoutTargetType.ARTICLE, LayoutTargetType.FLASHCARD)
        var firstPresent: String? = null
        for (target in order) {
            val profile = globalLayouts[target] ?: continue
            val value = profile.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            if (!value.isNullOrBlank()) {
                if (firstPresent == null) firstPresent = value
                if (!value.equals(defaultValue, ignoreCase = true)) {
                    return parser(value)
                }
            }
        }
        return parser(firstPresent ?: defaultValue)
    }

    private fun buildQuestionSlide(
        question: QuestionModel,
        slideIndex: Int,
        globalLayouts: Map<LayoutTargetType, Map<String, String>>,
        componentOverrides: Map<String, String>
    ): QuestionSlideItem {
        val targetType = when (question) {
            is McqQuestionModel -> LayoutTargetType.MCQ
            is FlashcardQuestionModel -> LayoutTargetType.FLASHCARD
        }
        val layout = LayoutBoundsCatalog.merge(
            globalLayouts = globalLayouts,
            targetType = targetType,
            componentOverrides = componentOverrides,
            questionOverrides = question.layoutOverrides
        )
        return when (question) {
            is McqQuestionModel -> SlideUiModel.McqSlide(question.id, slideIndex, layout, question)
            is FlashcardQuestionModel -> SlideUiModel.FlashcardSlide(question.id, slideIndex, layout, question)
        }
    }
}
