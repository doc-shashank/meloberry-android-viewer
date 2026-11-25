package com.meloberry.android.domain

import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.ArticleBlockModel
import com.meloberry.android.core.model.AssertionReasonPayload
import com.meloberry.android.core.model.AssertionReasonQuestionModel
import com.meloberry.android.core.model.ComponentKind
import com.meloberry.android.core.model.ComponentModel
import com.meloberry.android.core.model.DiffCellSpec
import com.meloberry.android.core.model.DifferentiationPayload
import com.meloberry.android.core.model.DifferentiationQuestionModel
import com.meloberry.android.core.model.FillBlankPayload
import com.meloberry.android.core.model.FillBlankQuestionModel
import com.meloberry.android.core.model.FillBlankSlot
import com.meloberry.android.core.model.LayoutTargetType
import com.meloberry.android.core.model.MechanismPayload
import com.meloberry.android.core.model.MechanismQuestionModel
import com.meloberry.android.core.model.MechanismStep
import com.meloberry.android.core.model.RelabelBox
import com.meloberry.android.core.model.RelabelImagePayload
import com.meloberry.android.core.model.RelabelImageQuestionModel
import com.meloberry.android.core.model.SlideAnalysisEntry
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.model.SpotErrorPayload
import com.meloberry.android.core.model.SpotErrorQuestionModel
import com.meloberry.android.core.model.SpotErrorSegment
import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.TableBlockPayload
import com.meloberry.android.core.model.WorkspaceKind
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradingEngineV2Test {

    @Test
    fun recallAnswerMatcher_normalizesGreekWhitespacePunctuationAndFuzzyBounds() {
        // Greek letter normalization
        assertEquals("alpha-1 receptor", RecallAnswerMatcher.normalizeRecallAnswer("  α–1   receptor  ", caseSensitive = false))
        assertEquals("Beta blocker", RecallAnswerMatcher.normalizeRecallAnswer("Β-blocker", caseSensitive = true).replace("-", " ").let {
            RecallAnswerMatcher.normalizeRecallAnswer("Β blocker", caseSensitive = true)
        })
        assertEquals("mu", RecallAnswerMatcher.normalizeRecallAnswer("\u00b5", caseSensitive = false))
        assertEquals("delta g", RecallAnswerMatcher.normalizeRecallAnswer("ΔG", caseSensitive = false).let {
            RecallAnswerMatcher.normalizeRecallAnswer("Δ G", caseSensitive = false)
        })

        // Exact & compact match
        val exactDash = RecallAnswerMatcher.evaluateRecallMatch("PFK1", listOf("phosphofructokinase-1", "PFK-1"), fuzzy = false, caseSensitive = false)
        assertTrue(exactDash.isMatch)
        assertFalse(exactDash.isFuzzyMatch)
        assertEquals("PFK-1", exactDash.matchedAnswer)

        // Case sensitivity
        val wrongCase = RecallAnswerMatcher.evaluateRecallMatch("pfk-1", listOf("PFK-1"), fuzzy = false, caseSensitive = true)
        assertFalse(wrongCase.isMatch)

        // Levenshtein distance
        assertEquals(0, RecallAnswerMatcher.levenshteinDistance("mitosis", "mitosis"))
        assertEquals(1, RecallAnswerMatcher.levenshteinDistance("mitosis", "mitosi"))
        assertEquals(2, RecallAnswerMatcher.levenshteinDistance("kitten", "sittin"))

        // Fuzzy threshold: length >= 6 and edit distance <= 1 allowed; length < 6 rejected
        val fuzzyLong = RecallAnswerMatcher.evaluateRecallMatch("nephrn", listOf("nephron"), fuzzy = true, caseSensitive = false)
        assertTrue(fuzzyLong.isMatch)
        assertTrue(fuzzyLong.isFuzzyMatch)

        val fuzzyDisabled = RecallAnswerMatcher.evaluateRecallMatch("nephrn", listOf("nephron"), fuzzy = false, caseSensitive = false)
        assertFalse(fuzzyDisabled.isMatch)

        val shortFuzzyRejected = RecallAnswerMatcher.evaluateRecallMatch("atp", listOf("adp"), fuzzy = true, caseSensitive = false)
        assertFalse(shortFuzzyRejected.isMatch)
    }

    @Test
    fun deterministicShuffler_isDeterministic_andAvoidsIdentityOrder() {
        val items = listOf("A", "B", "C", "D")
        val first = DeterministicShuffler.deterministicShuffle(items, "Q_1:mech") { it }
        val second = DeterministicShuffler.deterministicShuffle(items, "Q_1:mech") { it }

        assertEquals(first, second)
        assertEquals(items.toSet(), first.toSet())
        assertNotEquals(items, first)

        // Two-element list always swaps away from identity
        val pair = listOf("1", "2")
        val shuffledPair = DeterministicShuffler.deterministicShuffle(pair, "any-seed") { it }
        assertEquals(listOf("2", "1"), shuffledPair)

        // Duplicate leading keys must still swap with a distinct element when Fisher-Yates hits identity
        val duplicateLeading = listOf("Positive", "Positive", "Negative")
        for (seedIdx in 0 until 64) {
            val res = DeterministicShuffler.deterministicShuffle(duplicateLeading, "dup-seed-$seedIdx") { it }
            assertNotEquals(duplicateLeading, res)
        }
    }

    @Test
    fun evaluateFillBlankUseCase_gradesExactFuzzyAndManualOverride() {
        val useCase = EvaluateFillBlankUseCase()
        val payload = FillBlankPayload(
            template = "{{alpha}} and {{mitochondria}}",
            blanks = listOf(
                FillBlankSlot(1, listOf("alpha", "α")),
                FillBlankSlot(2, listOf("mitochondria"))
            ),
            fuzzy = true,
            caseSensitive = false
        )

        val fuzzyPass = useCase(payload, mapOf(1 to "α", 2 to "mitochondra"))
        assertTrue(fuzzyPass.isCorrect)
        assertTrue(fuzzyPass.anyFuzzy)

        val fail = useCase(payload, mapOf(1 to "beta", 2 to "ribosome"))
        assertFalse(fail.isCorrect)

        val overridden = useCase(payload, mapOf(1 to "beta", 2 to "ribosome"), overrideQuestionCorrect = true)
        assertTrue(overridden.isCorrect)
        assertTrue(overridden.isOverridden)

        // Per-slot override: slot 1 exact, slot 2 wrong but overridden -> passes as overridden
        val perSlotOverride = useCase(payload, mapOf(1 to "alpha", 2 to "ribosome"), overriddenSlotIndices = setOf(2))
        assertTrue(perSlotOverride.isCorrect)
        assertTrue(perSlotOverride.isOverridden)
        assertFalse(perSlotOverride.anyFuzzy)
        assertTrue(perSlotOverride.slotResults[1].isOverridden)
        assertFalse(perSlotOverride.slotResults[0].isOverridden)
    }

    @Test
    fun evaluateAssertionReasonUseCase_validatesOptionsAndScheme() {
        val useCase = EvaluateAssertionReasonUseCase()
        val payload4 = AssertionReasonPayload("A", "R", "both-explain", "4opt")
        val payload5 = AssertionReasonPayload("A", "R", "both-false", "5opt")

        val res4Ok = useCase(payload4, "both-explain")
        assertTrue(res4Ok.isCorrect)
        assertEquals(4, res4Ok.options.size)

        val res5Ok = useCase(payload5, "both-false")
        assertTrue(res5Ok.isCorrect)
        assertEquals(5, res5Ok.options.size)
        assertEquals("Both Assertion (A) and Reason (R) are false.", res5Ok.options.last().label)

        assertFalse(useCase(payload5, "a-true-r-false").isCorrect)
    }

    @Test
    fun evaluateSpotErrorUseCase_handlesRequireFixAndNoErrorMode() {
        val useCase = EvaluateSpotErrorUseCase()
        val payloadWithError = SpotErrorPayload(
            template = "In [PTH excess], phosphate is [~~high~~ -> low | decreased].",
            segments = listOf(
                SpotErrorSegment(1, "PTH excess", isCandidate = true, isError = false),
                SpotErrorSegment(2, "high", isCandidate = true, isError = true, corrections = listOf("low", "decreased"))
            ),
            requireFix = true,
            allowNoError = true,
            noError = false
        )

        val okFix = useCase(payloadWithError, setOf(2), pickedNoError = false, fixesBySpanIndex = mapOf(2 to "decreased"))
        assertTrue(okFix.isCorrect)
        assertEquals(SpotErrorSpanVerdict.CORRECT_ERROR, okFix.spanVerdicts[2])

        val badFix = useCase(payloadWithError, setOf(2), pickedNoError = false, fixesBySpanIndex = mapOf(2 to "normal"))
        assertFalse(badFix.isCorrect)
        assertEquals(SpotErrorSpanVerdict.WRONG_FIX, badFix.spanVerdicts[2])

        val noErrorPayload = SpotErrorPayload(
            template = "[All good]",
            segments = listOf(SpotErrorSegment(1, "All good", isCandidate = true, isError = false)),
            requireFix = false,
            allowNoError = true,
            noError = true
        )
        assertTrue(useCase(noErrorPayload, emptySet(), pickedNoError = true).isCorrect)
        assertFalse(useCase(noErrorPayload, setOf(1), pickedNoError = false).isCorrect)
    }

    @Test
    fun evaluateDifferentiationUseCase_handlesFillSpotWrongAndRearrange() {
        val useCase = EvaluateDifferentiationUseCase()

        // Fill mode
        val fillPayload = DifferentiationPayload(
            mode = "fill",
            headers = listOf("Feature", "A", "B"),
            alignments = listOf("left", "left", "left"),
            rows = listOf(listOf("Energy", "{{Passive}}", "{{ATP-dependent}}")),
            specialCells = listOf(
                DiffCellSpec(0, 1, "{{Passive}}", isBlank = true, blankAnswers = listOf("Passive")),
                DiffCellSpec(0, 2, "{{ATP-dependent}}", isBlank = true, blankAnswers = listOf("ATP-dependent"))
            ),
            fuzzy = true
        )
        val fillOk = useCase.evaluateFill(fillPayload, mapOf((0 to 1) to "passiv", (0 to 2) to "atp dependent"))
        assertTrue(fillOk.isCorrect)
        assertTrue(fillOk.anyFuzzy)

        val fillOverride = useCase.evaluateFill(fillPayload, emptyMap(), overrideCorrect = true)
        assertTrue(fillOverride.isCorrect)
        assertTrue(fillOverride.isOverridden)

        // Spot-wrong with no errors + allowNoError
        val noErrorDiff = DifferentiationPayload(
            mode = "spot-wrong",
            allowNoError = true,
            headers = listOf("Feature", "A", "B"),
            alignments = listOf("left", "left", "left"),
            rows = listOf(listOf("X", "1", "2")),
            specialCells = emptyList()
        )
        assertTrue(useCase.evaluateSpotWrong(noErrorDiff, emptySet(), pickedNoError = true).isCorrect)
        assertFalse(useCase.evaluateSpotWrong(noErrorDiff, setOf(0 to 1), pickedNoError = false).isCorrect)

        // Spot-wrong with actual error cells (including col 0 false-positive check)
        val withErrorDiff = DifferentiationPayload(
            mode = "spot-wrong",
            allowNoError = true,
            headers = listOf("Feature", "A", "B"),
            alignments = listOf("left", "left", "left"),
            rows = listOf(listOf("X", "Wrong", "Right")),
            specialCells = listOf(
                DiffCellSpec(0, 1, "~~Wrong~~ -> Fixed", isError = true, wrongText = "Wrong", corrections = listOf("Fixed"))
            )
        )
        val spotOk = useCase.evaluateSpotWrong(withErrorDiff, setOf(0 to 1), pickedNoError = false)
        assertTrue(spotOk.isCorrect)
        assertEquals(DiffCellVerdict.CORRECT, spotOk.cellVerdicts[0 to 1])

        val spotFalsePosCol0 = useCase.evaluateSpotWrong(withErrorDiff, setOf(0 to 0, 0 to 1), pickedNoError = false)
        assertFalse(spotFalsePosCol0.isCorrect)
        assertEquals(DiffCellVerdict.WRONG, spotFalsePosCol0.cellVerdicts[0 to 0])

        // Rearrange mode (allowing duplicate identical text cells)
        val rearrPayload = DifferentiationPayload(
            mode = "rearrange",
            headers = listOf("Feature", "Type 1", "Type 2", "Type 3"),
            alignments = listOf("left", "left", "left", "left"),
            rows = listOf(listOf("Marker", "Positive", "Positive", "Negative"))
        )
        assertTrue(
            useCase.evaluateRearrange(
                rearrPayload,
                listOf(listOf("Marker", "Positive", "Positive", "Negative"))
            ).isCorrect
        )
        assertFalse(
            useCase.evaluateRearrange(
                rearrPayload,
                listOf(listOf("Marker", "Negative", "Positive", "Positive"))
            ).isCorrect
        )
    }

    @Test
    fun evaluateMechanismUseCase_allowsParallelOrderGroupPermutations_andRejectsDistractors() {
        val useCase = EvaluateMechanismUseCase()
        val payload = MechanismPayload(
            title = "RAAS",
            steps = listOf(
                MechanismStep(stepId = 1, orderGroup = 1, text = "Renin release", isDistractor = false),
                MechanismStep(stepId = 2, orderGroup = 2, text = "Ang II formation", isDistractor = false),
                MechanismStep(stepId = 3, orderGroup = 2, text = "Bradykinin breakdown", isDistractor = false),
                MechanismStep(stepId = 4, orderGroup = 3, text = "Vasoconstriction", isDistractor = false),
                MechanismStep(stepId = 5, orderGroup = 0, text = "ANP decoy", isDistractor = true)
            )
        )

        // Both permutations of parallel group 2 (2 then 3, or 3 then 2) must pass
        assertTrue(useCase(payload, listOf(1, 2, 3, 4)).isCorrect)
        assertTrue(useCase(payload, listOf(1, 3, 2, 4)).isCorrect)

        // Wrong order fails
        val wrongOrder = useCase(payload, listOf(1, 4, 2, 3))
        assertFalse(wrongOrder.isCorrect)

        // Distractor fails and is flagged
        val withDistractor = useCase(payload, listOf(1, 2, 5, 4))
        assertFalse(withDistractor.isCorrect)
        assertEquals(MechanismSlotVerdict.DISTRACTOR_PLACED, withDistractor.slotResults[2].verdict)

        // Duplicate step ID fails even if orderGroup matches
        assertFalse(useCase(payload, listOf(1, 2, 2, 4)).isCorrect)
    }

    @Test
    fun evaluateRelabelImageUseCase_handlesRevealBankAndRecallModes() {
        val useCase = EvaluateRelabelImageUseCase()
        val payload = RelabelImagePayload(
            imagePath = "/tmp/nephron.png",
            imageFilePath = "/tmp/nephron.png",
            mode = "recall",
            style = "solid",
            boxes = listOf(
                RelabelBox(1, "Glomerulus", listOf("Glomerular tuft"), 0.1, 0.1, 0.2, 0.1),
                RelabelBox(2, "Loop of Henle", emptyList(), 0.4, 0.5, 0.2, 0.1)
            ),
            fuzzy = true,
            caseSensitive = false
        )

        // Reveal
        val revealKnown = useCase.evaluateReveal(payload, setOf(1, 2), "known")
        assertTrue(revealKnown.isCorrect)
        assertTrue(revealKnown.allBoxesRevealed)
        assertFalse(useCase.evaluateReveal(payload, setOf(1), "needsReview").isCorrect)

        // Bank (alias accepted)
        val bankOk = useCase.evaluateBank(payload, mapOf(1 to "Glomerular tuft", 2 to "Loop of Henle"))
        assertTrue(bankOk.isCorrect)

        // Recall (fuzzy + override)
        val recallFuzzy = useCase.evaluateRecall(payload, mapOf(1 to "Glomerlus", 2 to "Loop of Henle"))
        assertTrue(recallFuzzy.isCorrect)
        assertTrue(recallFuzzy.anyFuzzy)

        val recallOverride = useCase.evaluateRecall(payload, mapOf(1 to "wrong", 2 to "wrong"), overrideCorrect = true)
        assertTrue(recallOverride.isCorrect)
        assertTrue(recallOverride.isOverridden)
    }

    @Test
    fun buildSlideDeckUseCase_inheritsMcqLayoutForAllSixNewQuestionTypes_andArticleLayoutForTables() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = BuildSlideDeckUseCase(dispatcher)

        val workspace = StudyWorkspace(
            id = "Session_V2_Layout",
            title = "V2 Layouts",
            kind = WorkspaceKind.SESSION,
            subType = "session",
            activeComponentId = "Comp_1",
            components = listOf(
                ComponentModel(
                    id = "Comp_1",
                    index = 1,
                    kind = ComponentKind.ARTICLE,
                    quizType = null,
                    questions = emptyList(),
                    articleBlocks = listOf(
                        ArticleBlockModel(
                            id = "Art_Table",
                            index = 1,
                            kind = ArticleBlockKind.TABLE,
                            textContent = null,
                            imageFilePath = null,
                            tablePayload = TableBlockPayload(headers = listOf("A"), alignments = listOf("left"), rows = listOf(listOf("1")))
                        )
                    ),
                    layoutOverrides = emptyMap()
                ),
                ComponentModel(
                    id = "Comp_2",
                    index = 2,
                    kind = ComponentKind.QUIZ,
                    quizType = "mixed",
                    questions = listOf(
                        FillBlankQuestionModel("Q_FB", 1, FillBlankPayload("{{x}}", listOf(FillBlankSlot(1, listOf("x")))), mapOf("cardPaddingPx" to "20")),
                        AssertionReasonQuestionModel("Q_AR", 2, AssertionReasonPayload("A", "R", "both-explain", "4opt"), emptyMap()),
                        SpotErrorQuestionModel("Q_SE", 3, SpotErrorPayload("[x]", listOf(SpotErrorSegment(1, "x", true, false))), emptyMap()),
                        DifferentiationQuestionModel("Q_DIFF", 4, DifferentiationPayload("fill", headers = listOf("F", "A"), alignments = listOf("left", "left"), rows = listOf(listOf("1", "2"))), emptyMap()),
                        MechanismQuestionModel("Q_MECH", 5, MechanismPayload(steps = listOf(MechanismStep(1, 1, "A"), MechanismStep(2, 2, "B"))), emptyMap()),
                        RelabelImageQuestionModel("Q_REL", 6, RelabelImagePayload("/tmp/a.png", "/tmp/a.png", boxes = listOf(RelabelBox(1, "A", emptyList(), 0.1, 0.1, 0.2, 0.1))), emptyMap())
                    ),
                    articleBlocks = emptyList(),
                    layoutOverrides = mapOf("cardPaddingPx" to "44")
                )
            ),
            standaloneQuestions = emptyList(),
            globalLayouts = mapOf(
                LayoutTargetType.ARTICLE to mapOf("blockSpacingPx" to "36"),
                LayoutTargetType.MCQ to mapOf("cardMaxWidthPx" to "1020")
            ),
            storageDirPath = "/tmp/Session_V2_Layout"
        )

        val deck = useCase(workspace)
        val articleSlide = deck.slides[0] as SlideUiModel.ArticleSlide
        assertEquals(36, articleSlide.layout.blockSpacingDp)

        val quizSlide = deck.slides[1] as SlideUiModel.QuizSlide
        assertEquals(6, quizSlide.questions.size)
        val fbSlide = quizSlide.questions[0] as SlideUiModel.FillBlankSlide
        assertEquals(1020, fbSlide.layout.cardMaxWidthDp)
        assertEquals(20, fbSlide.layout.cardPaddingDp) // question override wins over component override (44)

        val relSlide = quizSlide.questions[5] as SlideUiModel.RelabelImageSlide
        assertEquals(1020, relSlide.layout.cardMaxWidthDp)
        assertEquals(44, relSlide.layout.cardPaddingDp) // component override wins over default
    }

    @Test
    fun buildAnalysisReportUseCase_matchesCliMarkdownFormatAcrossAllSevenGradedKinds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reportUseCase = BuildAnalysisReportUseCase(dispatcher)

        val result = reportUseCase(
            workspaceId = "Session_V2_Mixed",
            title = "Mixed V2",
            startedAtEpochMs = 1_000L,
            completedAtEpochMs = 11_000L,
            slideEntries = listOf(
                SlideAnalysisEntry(1, "Art_1", "article", 2_000L, 1),
                SlideAnalysisEntry(2, "Q_Mcq", "mcq", 1_000L, 1, 1, listOf(1), listOf(1), true, true),
                SlideAnalysisEntry(3, "Q_Fb", "fill-blank", 1_000L, 1, 2, firstTryCorrect = false, isCorrect = true),
                SlideAnalysisEntry(4, "Q_Ar", "assertion-reason", 1_000L, 1, 1, firstTryCorrect = true, isCorrect = true),
                SlideAnalysisEntry(5, "Q_Se", "spot-error", 1_000L, 1, 1, firstTryCorrect = false, isCorrect = false),
                SlideAnalysisEntry(6, "Q_Diff", "diff", 1_000L, 1, 1, firstTryCorrect = true, isCorrect = true),
                SlideAnalysisEntry(7, "Q_Mech", "mechanism", 1_000L, 1, 1, firstTryCorrect = true, isCorrect = true),
                SlideAnalysisEntry(8, "Q_Rel", "relabel", 1_000L, 1, 1, firstTryCorrect = false, isCorrect = false),
                SlideAnalysisEntry(9, "Q_Card", "flashcard", 1_000L, 1, flipCount = 1, flashcardRating = "needsReview")
            )
        )

        assertEquals("1.0", result.report.schemaVersion)
        assertEquals(7, result.report.summary.mcqTotal)
        assertEquals(5, result.report.summary.mcqCorrectFinal)
        assertEquals(4, result.report.summary.mcqCorrectFirstTry)
        assertEquals(71.4, result.report.summary.mcqAccuracyPercent, 0.001)
        assertEquals(listOf("Q_Se", "Q_Rel", "Q_Card"), result.report.weakItemIds)

        assertTrue(result.markdown.contains("- **Graded Questions:** 5 / 7 correct (71.4% | First-try: 4)"))
        assertTrue(result.markdown.contains("| 2 | `Q_Mcq` | mcq | 1.0 | 1 | Correct: Yes, Checks: 1, Selected: [1] |"))
        assertTrue(result.markdown.contains("| 3 | `Q_Fb` | fill-blank | 1.0 | 1 | Correct: Yes, Checks: 2 |"))
        assertTrue(result.markdown.contains("| 4 | `Q_Ar` | assertion-reason | 1.0 | 1 | Correct: Yes, Checks: 1 |"))
        assertTrue(result.markdown.contains("| 5 | `Q_Se` | spot-error | 1.0 | 1 | Correct: No, Checks: 1 |"))
        assertTrue(result.markdown.contains("| 6 | `Q_Diff` | diff | 1.0 | 1 | Correct: Yes, Checks: 1 |"))
        assertTrue(result.markdown.contains("| 7 | `Q_Mech` | mechanism | 1.0 | 1 | Correct: Yes, Checks: 1 |"))
        assertTrue(result.markdown.contains("| 8 | `Q_Rel` | relabel | 1.0 | 1 | Correct: No, Checks: 1 |"))
        assertTrue(result.markdown.contains("| 9 | `Q_Card` | flashcard | 1.0 | 1 | Flips: 1, Rating: needsReview |"))
    }
}
