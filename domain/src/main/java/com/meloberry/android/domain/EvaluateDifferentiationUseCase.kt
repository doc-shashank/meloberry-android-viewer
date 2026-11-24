package com.meloberry.android.domain

import com.meloberry.android.core.model.DifferentiationPayload
import javax.inject.Inject

enum class DiffCellVerdict {
    CORRECT,
    FUZZY_CORRECT,
    WRONG,
    MISSED_ERROR
}

data class DifferentiationEvaluationResult(
    val isCorrect: Boolean,
    val anyFuzzy: Boolean = false,
    val isOverridden: Boolean = false,
    val cellVerdicts: Map<Pair<Int, Int>, DiffCellVerdict> = emptyMap()
)

class EvaluateDifferentiationUseCase @Inject constructor() {

    fun evaluateFill(
        payload: DifferentiationPayload,
        inputsByCell: Map<Pair<Int, Int>, String>,
        overrideCorrect: Boolean = false
    ): DifferentiationEvaluationResult {
        val blankCells = payload.specialCells.filter { it.isBlank }
        var allOk = blankCells.isNotEmpty()
        var anyFuzzy = false
        val verdicts = LinkedHashMap<Pair<Int, Int>, DiffCellVerdict>()

        for (spec in blankCells) {
            val coord = spec.rowIndex to spec.colIndex
            val userInput = inputsByCell[coord].orEmpty()
            val match = RecallAnswerMatcher.evaluateRecallMatch(
                userInput = userInput,
                acceptedAnswers = spec.blankAnswers.orEmpty(),
                fuzzy = payload.fuzzy,
                caseSensitive = payload.caseSensitive
            )
            when {
                overrideCorrect -> verdicts[coord] = DiffCellVerdict.CORRECT
                match.kind == RecallMatchKind.EXACT -> verdicts[coord] = DiffCellVerdict.CORRECT
                match.kind == RecallMatchKind.FUZZY -> {
                    anyFuzzy = true
                    verdicts[coord] = DiffCellVerdict.FUZZY_CORRECT
                }
                else -> {
                    allOk = false
                    verdicts[coord] = DiffCellVerdict.WRONG
                }
            }
        }

        return DifferentiationEvaluationResult(
            isCorrect = if (overrideCorrect) true else allOk,
            anyFuzzy = if (overrideCorrect) false else anyFuzzy,
            isOverridden = overrideCorrect,
            cellVerdicts = verdicts
        )
    }

    fun evaluateSpotWrong(
        payload: DifferentiationPayload,
        selectedCells: Set<Pair<Int, Int>>,
        pickedNoError: Boolean = false
    ): DifferentiationEvaluationResult {
        val errorCells = payload.specialCells.filter { it.isError }
        val errorCoords = errorCells.map { it.rowIndex to it.colIndex }.toSet()
        val verdicts = LinkedHashMap<Pair<Int, Int>, DiffCellVerdict>()

        if (errorCells.isEmpty()) {
            val allOk = pickedNoError && selectedCells.isEmpty()
            selectedCells.forEach { coord -> verdicts[coord] = DiffCellVerdict.WRONG }
            return DifferentiationEvaluationResult(
                isCorrect = allOk,
                cellVerdicts = verdicts
            )
        }

        var allOk = !pickedNoError
        for (r in payload.rows.indices) {
            for (c in payload.headers.indices) {
                val coord = r to c
                val picked = coord in selectedCells
                val isError = coord in errorCoords
                when {
                    isError && picked -> verdicts[coord] = DiffCellVerdict.CORRECT
                    isError && !picked -> {
                        verdicts[coord] = DiffCellVerdict.MISSED_ERROR
                        allOk = false
                    }
                    !isError && picked -> {
                        verdicts[coord] = DiffCellVerdict.WRONG
                        allOk = false
                    }
                }
            }
        }

        return DifferentiationEvaluationResult(
            isCorrect = allOk,
            cellVerdicts = verdicts
        )
    }

    fun evaluateRearrange(
        payload: DifferentiationPayload,
        placedRows: List<List<String>>
    ): DifferentiationEvaluationResult {
        var allOk = payload.rows.isNotEmpty()
        val verdicts = LinkedHashMap<Pair<Int, Int>, DiffCellVerdict>()

        for (r in payload.rows.indices) {
            val expectedRow = payload.rows[r]
            val placedRow = placedRows.getOrNull(r)
            for (c in 1 until expectedRow.size) {
                val coord = r to c
                val actual = placedRow?.getOrNull(c)
                val cellOk = actual != null && actual == expectedRow[c]
                verdicts[coord] = if (cellOk) DiffCellVerdict.CORRECT else DiffCellVerdict.WRONG
                if (!cellOk) {
                    allOk = false
                }
            }
        }

        return DifferentiationEvaluationResult(
            isCorrect = allOk,
            cellVerdicts = verdicts
        )
    }
}
