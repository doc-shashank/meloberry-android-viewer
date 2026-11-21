package com.meloberry.android.domain

import com.meloberry.android.core.model.McqQuestionModel
import javax.inject.Inject

data class McqEvaluationResult(
    val isCorrect: Boolean,
    val selectedOptionIndices: List<Int>,
    val correctOptionIndices: List<Int>
)

class EvaluateMcqAnswerUseCase @Inject constructor() {

    operator fun invoke(
        question: McqQuestionModel,
        selectedOptionIndices: Set<Int>
    ): McqEvaluationResult {
        val correctSet = question.options
            .filter { it.isCorrect }
            .map { it.index }
            .toSet()

        val sortedSelected = selectedOptionIndices.sorted()
        val sortedCorrect = correctSet.sorted()
        val isCorrect = correctSet.isNotEmpty() && selectedOptionIndices == correctSet

        return McqEvaluationResult(
            isCorrect = isCorrect,
            selectedOptionIndices = sortedSelected,
            correctOptionIndices = sortedCorrect
        )
    }
}
