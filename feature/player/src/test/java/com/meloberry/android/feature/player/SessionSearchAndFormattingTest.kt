package com.meloberry.android.feature.player

import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.ArticleBlockModel
import com.meloberry.android.core.model.AssertionReasonPayload
import com.meloberry.android.core.model.AssertionReasonQuestionModel
import com.meloberry.android.core.model.CalloutTone
import com.meloberry.android.core.model.DiffCellSpec
import com.meloberry.android.core.model.DifferentiationPayload
import com.meloberry.android.core.model.DifferentiationQuestionModel
import com.meloberry.android.core.model.FillBlankPayload
import com.meloberry.android.core.model.FillBlankQuestionModel
import com.meloberry.android.core.model.FillBlankSlot
import com.meloberry.android.core.model.MechanismPayload
import com.meloberry.android.core.model.MechanismQuestionModel
import com.meloberry.android.core.model.MechanismStep
import com.meloberry.android.core.model.RelabelBox
import com.meloberry.android.core.model.RelabelImagePayload
import com.meloberry.android.core.model.RelabelImageQuestionModel
import com.meloberry.android.core.model.ResolvedSlideLayout
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.model.SpotErrorPayload
import com.meloberry.android.core.model.SpotErrorQuestionModel
import com.meloberry.android.core.model.SpotErrorSegment
import com.meloberry.android.core.model.TableBlockPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSearchAndFormattingTest {

    @Test
    fun sessionSearchIndex_indexesCalloutsTablesAndAllV2QuestionTypesWithNestedQuizTargets_withoutSpoilingAnswers() {
        val layout = ResolvedSlideLayout()
        val slides: List<SlideUiModel> = listOf(
            SlideUiModel.ArticleSlide(
                id = "Comp_Article",
                slideIndex = 1,
                layout = layout,
                title = "Renal Physiology",
                blocks = listOf(
                    ArticleBlockModel("Art_1", 1, ArticleBlockKind.TEXT, "Overview of nephron filtration.", null),
                    ArticleBlockModel(
                        id = "Art_2",
                        index = 2,
                        kind = ArticleBlockKind.CALLOUT,
                        textContent = "Loop diuretics inhibit NKCC2 cotransporter.",
                        imageFilePath = null,
                        calloutTone = CalloutTone.CLINICAL,
                        calloutTitle = "Diuretic Pearl"
                    ),
                    ArticleBlockModel(
                        id = "Art_3",
                        index = 3,
                        kind = ArticleBlockKind.TABLE,
                        textContent = null,
                        imageFilePath = null,
                        tablePayload = TableBlockPayload(
                            title = "Tubule Segments",
                            headers = listOf("Segment", "Transporter"),
                            alignments = listOf("left", "left"),
                            rows = listOf(listOf("DCT", "NCC symporter")),
                            footnote = "Thiazide target."
                        )
                    )
                )
            ),
            SlideUiModel.QuizSlide(
                id = "Comp_Quiz",
                slideIndex = 2,
                layout = layout,
                quizType = "mixed",
                title = "Renal Drill",
                questions = listOf(
                    SlideUiModel.FillBlankSlide(
                        id = "Q_Fb",
                        slideIndex = 1,
                        layout = layout,
                        question = FillBlankQuestionModel(
                            "Q_Fb", 1,
                            FillBlankPayload("Rate-limiting enzyme: {{PFK-1}}", listOf(FillBlankSlot(1, listOf("PFK-1")))),
                            emptyMap()
                        )
                    ),
                    SlideUiModel.AssertionReasonSlide(
                        id = "Q_Ar",
                        slideIndex = 2,
                        layout = layout,
                        question = AssertionReasonQuestionModel(
                            "Q_Ar", 2,
                            AssertionReasonPayload("Aldosterone increases ENaC.", "Acts on principal cells.", "both-explain", "4opt"),
                            emptyMap()
                        )
                    ),
                    SlideUiModel.SpotErrorSlide(
                        id = "Q_Se",
                        slideIndex = 3,
                        layout = layout,
                        question = SpotErrorQuestionModel(
                            "Q_Se", 3,
                            SpotErrorPayload(
                                "[PTH] causes [~~hyperphosphatemia~~ -> hypophosphatemia].",
                                listOf(SpotErrorSegment(1, "hyperphosphatemia", true, true, listOf("hypophosphatemia")))
                            ),
                            emptyMap()
                        )
                    ),
                    SlideUiModel.DifferentiationSlide(
                        id = "Q_Diff",
                        slideIndex = 4,
                        layout = layout,
                        question = DifferentiationQuestionModel(
                            "Q_Diff", 4,
                            DifferentiationPayload(
                                mode = "fill",
                                title = "Nephritic vs Nephrotic",
                                headers = listOf("Feature", "Nephritic"),
                                alignments = listOf("left", "left"),
                                rows = listOf(listOf("Urine", "{{RBC casts}}")),
                                specialCells = listOf(DiffCellSpec(0, 1, "{{RBC casts}}", isBlank = true, blankAnswers = listOf("RBC casts")))
                            ),
                            emptyMap()
                        )
                    ),
                    SlideUiModel.MechanismSlide(
                        id = "Q_Mech",
                        slideIndex = 5,
                        layout = layout,
                        question = MechanismQuestionModel(
                            "Q_Mech", 5,
                            MechanismPayload("RAAS Cascade", listOf(MechanismStep(1, 1, "JGA renin"), MechanismStep(2, 2, "Angiotensin II"))),
                            emptyMap()
                        )
                    ),
                    SlideUiModel.RelabelImageSlide(
                        id = "Q_Rel",
                        slideIndex = 6,
                        layout = layout,
                        question = RelabelImageQuestionModel(
                            "Q_Rel", 6,
                            RelabelImagePayload(
                                "/tmp/a.png", "/tmp/a.png",
                                prompt = "Label glomerulus",
                                boxes = listOf(RelabelBox(1, "Bowman capsule", listOf("Glomerular capsule"), 0.1, 0.1, 0.2, 0.1))
                            ),
                            emptyMap()
                        )
                    )
                )
            )
        )

        val entries = SessionSearchIndex.build(slides)
        assertEquals(7, entries.size)

        // Outline titles and previewText must not leak raw answers or corrections
        val fbEntry = entries.first { it.id == "Q_Fb" }
        assertFalse(fbEntry.title.contains("PFK-1"))
        assertFalse(fbEntry.previewText.contains("PFK-1"))
        assertTrue(fbEntry.previewText.contains("[blank]"))

        val seEntry = entries.first { it.id == "Q_Se" }
        assertFalse(seEntry.title.contains("hypophosphatemia"))
        assertFalse(seEntry.previewText.contains("hypophosphatemia"))
        assertTrue(seEntry.previewText.contains("hyperphosphatemia"))

        // Matches inside callout
        val calloutHits = SessionSearchIndex.search(entries, "NKCC2")
        assertEquals(1, calloutHits.size)
        assertEquals("Comp_Article", calloutHits.single().entry.id)

        // Matches inside table footnote
        val tableHits = SessionSearchIndex.search(entries, "Thiazide")
        assertEquals(1, tableHits.size)
        assertEquals(0, tableHits.single().entry.outerSlideIndex)

        // Matches inside nested QuizSlide v2.0 RelabelImage alias -> preserves quizId & question index without leaking answer in snippet
        val relabelHits = SessionSearchIndex.search(entries, "Glomerular capsule")
        assertEquals(1, relabelHits.size)
        assertEquals("Comp_Quiz", relabelHits.single().entry.quizId)
        assertEquals(5, relabelHits.single().entry.quizQuestionIndex)
        assertFalse(relabelHits.single().snippet.contains("Bowman capsule"))

        // Matches inside Mechanism
        val mechHits = SessionSearchIndex.search(entries, "Angiotensin")
        assertEquals(1, mechHits.size)
        assertEquals(4, mechHits.single().entry.quizQuestionIndex)
    }

    @Test
    fun formatFillBlankTemplateWithBadges_preservesMathAndInlineCodeSpans_andIgnoresCurrency() {
        val template = "Code `{{not_a_blank}}`, math $\\frac{{1}}{{2}}$, real blank {{PFK-1}} and {{ATP}}."
        val formatted = formatFillBlankTemplateWithBadges(template)
        assertTrue(formatted.contains("`{{not_a_blank}}`"))
        assertTrue(formatted.contains("$\\frac{{1}}{{2}}$"))
        assertTrue(formatted.contains("`[ #1 ]`"))
        assertTrue(formatted.contains("`[ #2 ]`"))

        val currencyTemplate = "Costs $5 for {{aspirin}} and $10 total."
        val currencyFormatted = formatFillBlankTemplateWithBadges(currencyTemplate)
        assertEquals("Costs $5 for `[ #1 ]` and $10 total.", currencyFormatted)
    }
}
