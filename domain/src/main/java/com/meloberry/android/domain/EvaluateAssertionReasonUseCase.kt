package com.meloberry.android.domain

import com.meloberry.android.core.model.AssertionReasonPayload
import java.util.Locale
import javax.inject.Inject

data class AssertionReasonOption(
    val code: String,
    val label: String
)

data class AssertionReasonEvaluationResult(
    val isCorrect: Boolean,
    val selectedCode: String?,
    val correctCode: String,
    val options: List<AssertionReasonOption>
)

class EvaluateAssertionReasonUseCase @Inject constructor() {

    companion object {
        private val FOUR_OPT_OPTIONS = listOf(
            AssertionReasonOption(
                code = "both-explain",
                label = "Both Assertion (A) and Reason (R) are true, and R is the correct explanation of A."
            ),
            AssertionReasonOption(
                code = "both-no-explain",
                label = "Both Assertion (A) and Reason (R) are true, but R is NOT the correct explanation of A."
            ),
            AssertionReasonOption(
                code = "a-true-r-false",
                label = "Assertion (A) is true, but Reason (R) is false."
            ),
            AssertionReasonOption(
                code = "a-false-r-true",
                label = "Assertion (A) is false, but Reason (R) is true."
            )
        )

        private val BOTH_FALSE_OPTION = AssertionReasonOption(
            code = "both-false",
            label = "Both Assertion (A) and Reason (R) are false."
        )

        fun optionsForScheme(scheme: String): List<AssertionReasonOption> =
            if (scheme.trim().equals("5opt", ignoreCase = true)) {
                FOUR_OPT_OPTIONS + BOTH_FALSE_OPTION
            } else {
                FOUR_OPT_OPTIONS
            }
    }

    operator fun invoke(
        payload: AssertionReasonPayload,
        selectedAnswerCode: String?
    ): AssertionReasonEvaluationResult {
        val options = optionsForScheme(payload.scheme)
        val normCorrect = payload.answer.trim().lowercase(Locale.ROOT)
        val normSelected = selectedAnswerCode?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val isCorrect = normSelected != null && normSelected == normCorrect

        return AssertionReasonEvaluationResult(
            isCorrect = isCorrect,
            selectedCode = normSelected,
            correctCode = normCorrect,
            options = options
        )
    }
}
