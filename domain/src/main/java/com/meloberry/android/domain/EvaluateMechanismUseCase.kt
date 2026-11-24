package com.meloberry.android.domain

import com.meloberry.android.core.model.MechanismPayload
import com.meloberry.android.core.model.MechanismStep
import javax.inject.Inject

enum class MechanismSlotVerdict {
    CORRECT,
    WRONG_POSITION,
    DISTRACTOR_PLACED
}

data class MechanismSlotEvaluation(
    val stepId: Int,
    val verdict: MechanismSlotVerdict
)

data class MechanismEvaluationResult(
    val isCorrect: Boolean,
    val slotResults: List<MechanismSlotEvaluation>,
    val canonicalSteps: List<MechanismStep>
)

class EvaluateMechanismUseCase @Inject constructor() {

    operator fun invoke(
        payload: MechanismPayload,
        placedStepIds: List<Int>
    ): MechanismEvaluationResult {
        val stepById = payload.steps.associateBy { it.stepId }
        val canonicalSteps = payload.steps
            .filter { !it.isDistractor }
            .sortedBy { it.orderGroup }
        val expectedGroups = canonicalSteps.map { it.orderGroup }

        val placedSteps = placedStepIds.mapNotNull { stepById[it] }
        val seenIds = mutableSetOf<Int>()
        var allOk = placedSteps.size == expectedGroups.size &&
            placedSteps.size == placedStepIds.size

        val slotResults = placedSteps.mapIndexed { idx, step ->
            val isDuplicate = !seenIds.add(step.stepId)
            val verdict = when {
                step.isDistractor -> {
                    allOk = false
                    MechanismSlotVerdict.DISTRACTOR_PLACED
                }
                !isDuplicate && idx < expectedGroups.size && step.orderGroup == expectedGroups[idx] -> {
                    MechanismSlotVerdict.CORRECT
                }
                else -> {
                    allOk = false
                    MechanismSlotVerdict.WRONG_POSITION
                }
            }
            MechanismSlotEvaluation(stepId = step.stepId, verdict = verdict)
        }

        return MechanismEvaluationResult(
            isCorrect = allOk,
            slotResults = slotResults,
            canonicalSteps = canonicalSteps
        )
    }
}
