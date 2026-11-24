package com.meloberry.android.domain

import com.meloberry.android.core.model.FillBlankPayload
import javax.inject.Inject

data class FillBlankSlotEvaluation(
    val slotIndex: Int,
    val userInput: String,
    val matchResult: RecallMatchResult,
    val isOverridden: Boolean,
    val isCorrect: Boolean,
    val primaryAnswer: String
)

data class FillBlankEvaluationResult(
    val isCorrect: Boolean,
    val anyFuzzy: Boolean,
    val isOverridden: Boolean,
    val slotResults: List<FillBlankSlotEvaluation>
)

class EvaluateFillBlankUseCase @Inject constructor() {

    operator fun invoke(
        payload: FillBlankPayload,
        userInputsBySlotIndex: Map<Int, String>,
        overriddenSlotIndices: Set<Int> = emptySet(),
        overrideQuestionCorrect: Boolean = false
    ): FillBlankEvaluationResult {
        var allOk = payload.blanks.isNotEmpty()
        var anyFuzzy = false

        val slotResults = payload.blanks.map { slot ->
            val rawInput = userInputsBySlotIndex[slot.index].orEmpty()
            val match = RecallAnswerMatcher.evaluateRecallMatch(
                userInput = rawInput,
                acceptedAnswers = slot.answers,
                fuzzy = payload.fuzzy,
                caseSensitive = payload.caseSensitive
            )
            val slotOverridden = overrideQuestionCorrect || slot.index in overriddenSlotIndices
            val slotOk = match.isMatch || slotOverridden
            if (match.isFuzzyMatch && !slotOverridden) {
                anyFuzzy = true
            }
            if (!slotOk) {
                allOk = false
            }
            FillBlankSlotEvaluation(
                slotIndex = slot.index,
                userInput = rawInput,
                matchResult = match,
                isOverridden = slotOverridden,
                isCorrect = slotOk,
                primaryAnswer = slot.answers.firstOrNull().orEmpty()
            )
        }

        return FillBlankEvaluationResult(
            isCorrect = if (overrideQuestionCorrect) true else allOk,
            anyFuzzy = anyFuzzy,
            isOverridden = overrideQuestionCorrect || overriddenSlotIndices.isNotEmpty(),
            slotResults = slotResults
        )
    }
}
