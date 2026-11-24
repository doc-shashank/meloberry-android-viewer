package com.meloberry.android.domain

import com.meloberry.android.core.model.SpotErrorPayload
import javax.inject.Inject

enum class SpotErrorSpanVerdict {
    CORRECT_ERROR,
    WRONG_FIX,
    MISSED_ERROR,
    FALSE_POSITIVE,
    CORRECT_UNSELECTED
}

data class SpotErrorEvaluationResult(
    val isCorrect: Boolean,
    val pickedNoError: Boolean,
    val spanVerdicts: Map<Int, SpotErrorSpanVerdict>,
    val fixMatchResults: Map<Int, RecallMatchResult>
)

class EvaluateSpotErrorUseCase @Inject constructor() {

    operator fun invoke(
        payload: SpotErrorPayload,
        selectedSpanIndices: Set<Int>,
        pickedNoError: Boolean = false,
        fixesBySpanIndex: Map<Int, String> = emptyMap()
    ): SpotErrorEvaluationResult {
        val candidates = payload.segments.filter { it.isCandidate && it.spanIndex != null }
        val spanVerdicts = LinkedHashMap<Int, SpotErrorSpanVerdict>()
        val fixResults = LinkedHashMap<Int, RecallMatchResult>()

        if (payload.noError) {
            val allOk = pickedNoError && selectedSpanIndices.isEmpty()
            for (seg in candidates) {
                val idx = seg.spanIndex!!
                spanVerdicts[idx] = if (idx in selectedSpanIndices) {
                    SpotErrorSpanVerdict.FALSE_POSITIVE
                } else {
                    SpotErrorSpanVerdict.CORRECT_UNSELECTED
                }
            }
            return SpotErrorEvaluationResult(
                isCorrect = allOk,
                pickedNoError = pickedNoError,
                spanVerdicts = spanVerdicts,
                fixMatchResults = fixResults
            )
        }

        var allOk = !pickedNoError
        for (seg in candidates) {
            val idx = seg.spanIndex!!
            val picked = idx in selectedSpanIndices
            if (seg.isError) {
                if (picked) {
                    if (payload.requireFix) {
                        val match = RecallAnswerMatcher.evaluateRecallMatch(
                            userInput = fixesBySpanIndex[idx].orEmpty(),
                            acceptedAnswers = seg.corrections.orEmpty(),
                            fuzzy = true,
                            caseSensitive = false
                        )
                        fixResults[idx] = match
                        if (match.isMatch) {
                            spanVerdicts[idx] = SpotErrorSpanVerdict.CORRECT_ERROR
                        } else {
                            spanVerdicts[idx] = SpotErrorSpanVerdict.WRONG_FIX
                            allOk = false
                        }
                    } else {
                        spanVerdicts[idx] = SpotErrorSpanVerdict.CORRECT_ERROR
                    }
                } else {
                    spanVerdicts[idx] = SpotErrorSpanVerdict.MISSED_ERROR
                    allOk = false
                }
            } else {
                if (picked) {
                    spanVerdicts[idx] = SpotErrorSpanVerdict.FALSE_POSITIVE
                    allOk = false
                } else {
                    spanVerdicts[idx] = SpotErrorSpanVerdict.CORRECT_UNSELECTED
                }
            }
        }

        return SpotErrorEvaluationResult(
            isCorrect = allOk,
            pickedNoError = pickedNoError,
            spanVerdicts = spanVerdicts,
            fixMatchResults = fixResults
        )
    }
}
