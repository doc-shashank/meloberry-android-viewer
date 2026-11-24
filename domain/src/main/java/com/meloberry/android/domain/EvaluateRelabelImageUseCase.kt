package com.meloberry.android.domain

import com.meloberry.android.core.model.RelabelImagePayload
import javax.inject.Inject

enum class RelabelBoxVerdict {
    EXACT,
    FUZZY,
    WRONG,
    OVERRIDDEN
}

data class RelabelImageEvaluationResult(
    val isCorrect: Boolean,
    val anyFuzzy: Boolean = false,
    val isOverridden: Boolean = false,
    val allBoxesRevealed: Boolean = false,
    val boxVerdicts: Map<Int, RelabelBoxVerdict> = emptyMap(),
    val rating: String? = null
)

class EvaluateRelabelImageUseCase @Inject constructor() {

    fun evaluateReveal(
        payload: RelabelImagePayload,
        revealedBoxIndices: Set<Int>,
        rating: String?
    ): RelabelImageEvaluationResult {
        val allRevealed = payload.boxes.all { it.index in revealedBoxIndices }
        val isCorrect = rating == "known"
        return RelabelImageEvaluationResult(
            isCorrect = isCorrect,
            allBoxesRevealed = allRevealed,
            rating = rating
        )
    }

    fun evaluateBank(
        payload: RelabelImagePayload,
        assignedLabelsByBoxIndex: Map<Int, String>
    ): RelabelImageEvaluationResult {
        var allOk = payload.boxes.isNotEmpty()
        val verdicts = LinkedHashMap<Int, RelabelBoxVerdict>()

        for (box in payload.boxes) {
            val assigned = assignedLabelsByBoxIndex[box.index].orEmpty()
            val match = RecallAnswerMatcher.evaluateRecallMatch(
                userInput = assigned,
                acceptedAnswers = listOf(box.label) + box.aliases,
                fuzzy = false,
                caseSensitive = payload.caseSensitive
            )
            if (match.isMatch) {
                verdicts[box.index] = RelabelBoxVerdict.EXACT
            } else {
                verdicts[box.index] = RelabelBoxVerdict.WRONG
                allOk = false
            }
        }

        return RelabelImageEvaluationResult(
            isCorrect = allOk,
            boxVerdicts = verdicts
        )
    }

    fun evaluateRecall(
        payload: RelabelImagePayload,
        inputsByBoxIndex: Map<Int, String>,
        overrideCorrect: Boolean = false
    ): RelabelImageEvaluationResult {
        var allOk = payload.boxes.isNotEmpty()
        var anyFuzzy = false
        val verdicts = LinkedHashMap<Int, RelabelBoxVerdict>()

        for (box in payload.boxes) {
            val rawInput = inputsByBoxIndex[box.index].orEmpty()
            val match = RecallAnswerMatcher.evaluateRecallMatch(
                userInput = rawInput,
                acceptedAnswers = listOf(box.label) + box.aliases,
                fuzzy = payload.fuzzy,
                caseSensitive = payload.caseSensitive
            )
            when {
                overrideCorrect -> verdicts[box.index] = RelabelBoxVerdict.OVERRIDDEN
                match.kind == RecallMatchKind.EXACT -> verdicts[box.index] = RelabelBoxVerdict.EXACT
                match.kind == RecallMatchKind.FUZZY -> {
                    anyFuzzy = true
                    verdicts[box.index] = RelabelBoxVerdict.FUZZY
                }
                else -> {
                    verdicts[box.index] = RelabelBoxVerdict.WRONG
                    allOk = false
                }
            }
        }

        return RelabelImageEvaluationResult(
            isCorrect = if (overrideCorrect) true else allOk,
            anyFuzzy = if (overrideCorrect) false else anyFuzzy,
            isOverridden = overrideCorrect,
            boxVerdicts = verdicts
        )
    }
}
