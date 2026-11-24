package com.meloberry.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TableBlockPayload(
    val title: String? = null,
    val caption: String? = null,
    val stickyFirstCol: Boolean = false,
    val hideableCols: List<Int> = emptyList(),
    val headers: List<String> = emptyList(),
    val alignments: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val footnote: String? = null
)

@Serializable
data class FillBlankSlot(
    val index: Int,
    val answers: List<String> = emptyList()
)

@Serializable
data class FillBlankPayload(
    val template: String = "",
    val blanks: List<FillBlankSlot> = emptyList(),
    val fuzzy: Boolean = true,
    val caseSensitive: Boolean = false,
    val explanation: String? = null,
    val placeholder: String? = null
)

@Serializable
data class AssertionReasonPayload(
    val assertion: String = "",
    val reason: String = "",
    val answer: String = "",
    val scheme: String = "",
    val explanation: String? = null
)

@Serializable
data class SpotErrorSegment(
    val spanIndex: Int? = null,
    val text: String = "",
    val isCandidate: Boolean = false,
    val isError: Boolean = false,
    val corrections: List<String>? = null
)

@Serializable
data class SpotErrorPayload(
    val template: String = "",
    val segments: List<SpotErrorSegment> = emptyList(),
    val requireFix: Boolean = false,
    val allowNoError: Boolean = false,
    val noError: Boolean = false,
    val explanation: String? = null,
    val imagePath: String? = null,
    val imageFilePath: String? = null
)

@Serializable
data class DiffCellSpec(
    val rowIndex: Int,
    val colIndex: Int,
    val rawText: String = "",
    val isBlank: Boolean = false,
    val blankAnswers: List<String>? = null,
    val isError: Boolean = false,
    val wrongText: String? = null,
    val corrections: List<String>? = null
)

@Serializable
data class DifferentiationPayload(
    val mode: String = "",
    val title: String? = null,
    val bank: Boolean = false,
    val fuzzy: Boolean = true,
    val caseSensitive: Boolean = false,
    val allowNoError: Boolean = false,
    val headers: List<String> = emptyList(),
    val alignments: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val specialCells: List<DiffCellSpec> = emptyList(),
    val explanation: String? = null
)

@Serializable
data class MechanismStep(
    val stepId: Int,
    val orderGroup: Int,
    val text: String = "",
    val isDistractor: Boolean = false
)

@Serializable
data class MechanismPayload(
    val title: String? = null,
    val steps: List<MechanismStep> = emptyList(),
    val explanation: String? = null
)

@Serializable
data class RelabelBox(
    val index: Int,
    val label: String = "",
    val aliases: List<String> = emptyList(),
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0
)

@Serializable
data class RelabelImagePayload(
    val imagePath: String = "",
    val imageFilePath: String = "",
    val prompt: String? = null,
    val mode: String = "bank",
    val style: String = "solid",
    val boxes: List<RelabelBox> = emptyList(),
    val distractors: List<String> = emptyList(),
    val fuzzy: Boolean = true,
    val caseSensitive: Boolean = false,
    val explanation: String? = null,
    val sourceMasksPath: String? = null,
    val includeLabels: List<String>? = null
)
