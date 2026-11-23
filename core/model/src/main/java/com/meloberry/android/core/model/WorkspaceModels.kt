package com.meloberry.android.core.model

enum class WorkspaceKind {
    SESSION,
    QUIZ
}

enum class ComponentKind {
    ARTICLE,
    QUIZ
}

enum class ArticleBlockKind {
    TEXT,
    IMAGE
}

enum class QuestionType {
    MCQ,
    FLASHCARD
}

enum class MediaPosition {
    BELOW_IMAGE,
    ABOVE_IMAGE;

    companion object {
        fun fromRaw(raw: String?): MediaPosition = when (raw?.trim()?.lowercase()) {
            "aboveimage", "textaboveimage" -> ABOVE_IMAGE
            else -> BELOW_IMAGE
        }
    }
}

enum class ThemeMode {
    BERRY,
    LIGHT,
    DARK,
    PAPER;

    companion object {
        fun fromRaw(raw: String?): ThemeMode = when (raw?.trim()?.lowercase()) {
            "light" -> LIGHT
            "dark" -> DARK
            "paper" -> PAPER
            else -> BERRY
        }
    }
}

enum class ContentAlign {
    LEFT,
    CENTER,
    RIGHT;

    companion object {
        fun fromRaw(raw: String?): ContentAlign = when (raw?.trim()?.lowercase()) {
            "center" -> CENTER
            "right" -> RIGHT
            else -> LEFT
        }
    }
}

enum class LayoutTargetType(val key: String) {
    MCQ("mcq"),
    FLASHCARD("flashcard"),
    ARTICLE("article");

    companion object {
        fun fromRaw(raw: String?): LayoutTargetType? =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class WorkspaceSummary(
    val id: String,
    val title: String,
    val description: String = "",
    val kind: WorkspaceKind,
    val subType: String,
    val itemCount: Int,
    val importedAtEpochMs: Long
)

data class StudyWorkspace(
    val id: String,
    val title: String,
    val description: String = "",
    val kind: WorkspaceKind,
    val subType: String,
    val activeComponentId: String?,
    val components: List<ComponentModel>,
    val standaloneQuestions: List<QuestionModel>,
    val globalLayouts: Map<LayoutTargetType, Map<String, String>>,
    val storageDirPath: String
)

data class ComponentModel(
    val id: String,
    val index: Int,
    val kind: ComponentKind,
    val quizType: String?,
    val questions: List<QuestionModel>,
    val articleBlocks: List<ArticleBlockModel>,
    val layoutOverrides: Map<String, String>,
    val title: String? = null
)

data class ArticleBlockModel(
    val id: String,
    val index: Int,
    val kind: ArticleBlockKind,
    val textContent: String?,
    val imageFilePath: String?
)

sealed interface QuestionModel {
    val id: String
    val index: Int
    val type: QuestionType
    val layoutOverrides: Map<String, String>
}

data class McqOptionModel(
    val index: Int,
    val text: String?,
    val imageFilePath: String?,
    val isCorrect: Boolean
)

data class McqQuestionModel(
    override val id: String,
    override val index: Int,
    val promptText: String,
    val maxOptions: Int,
    val isMultiCorrect: Boolean,
    val options: List<McqOptionModel>,
    override val layoutOverrides: Map<String, String>,
    val promptImageFilePath: String? = null,
    val explanation: String? = null
) : QuestionModel {
    override val type: QuestionType = QuestionType.MCQ
}

data class FlashcardSideModel(
    val text: String?,
    val imageFilePath: String?,
    val position: MediaPosition
)

data class FlashcardQuestionModel(
    override val id: String,
    override val index: Int,
    val front: FlashcardSideModel,
    val back: FlashcardSideModel,
    override val layoutOverrides: Map<String, String>
) : QuestionModel {
    override val type: QuestionType = QuestionType.FLASHCARD
}

data class ResolvedSlideLayout(
    val cardMaxWidthDp: Int = 840,
    val cardPaddingDp: Int = 32,
    val promptFontSizeSp: Int = 22,
    val bodyFontSizeSp: Int = 16,
    val imageMaxHeightDp: Int = 360,
    val slideTransitionMs: Int = 380,
    val contentAlign: ContentAlign = ContentAlign.LEFT,
    val themeMode: ThemeMode = ThemeMode.BERRY,
    val optionColumns: Int = 1,
    val optionGapDp: Int = 12,
    val defaultTextPosition: MediaPosition = MediaPosition.BELOW_IMAGE,
    val flipDurationMs: Int = 450,
    val blockSpacingDp: Int = 20
)

sealed interface QuestionSlideItem : SlideUiModel

sealed interface SlideUiModel {
    val id: String
    val slideIndex: Int
    val layout: ResolvedSlideLayout

    data class ArticleSlide(
        override val id: String,
        override val slideIndex: Int,
        override val layout: ResolvedSlideLayout,
        val blocks: List<ArticleBlockModel>,
        val title: String? = null
    ) : SlideUiModel

    data class McqSlide(
        override val id: String,
        override val slideIndex: Int,
        override val layout: ResolvedSlideLayout,
        val question: McqQuestionModel
    ) : QuestionSlideItem

    data class FlashcardSlide(
        override val id: String,
        override val slideIndex: Int,
        override val layout: ResolvedSlideLayout,
        val question: FlashcardQuestionModel
    ) : QuestionSlideItem

    data class QuizSlide(
        override val id: String,
        override val slideIndex: Int,
        override val layout: ResolvedSlideLayout,
        val quizType: String?,
        val questions: List<QuestionSlideItem>,
        val title: String? = null
    ) : SlideUiModel
}

data class SlideDeck(
    val workspaceId: String,
    val title: String,
    val description: String = "",
    val themeMode: ThemeMode,
    val slideTransitionMs: Int,
    val slides: List<SlideUiModel>
) {
    fun leafSlides(): List<SlideUiModel> = slides.flatMap { slide ->
        when (slide) {
            is SlideUiModel.QuizSlide -> slide.questions
            else -> listOf(slide)
        }
    }

    fun allQuestionSlides(): List<QuestionSlideItem> =
        leafSlides().filterIsInstance<QuestionSlideItem>()
}
