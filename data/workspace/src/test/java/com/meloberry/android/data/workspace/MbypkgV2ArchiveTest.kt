package com.meloberry.android.data.workspace

import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.AssertionReasonQuestionModel
import com.meloberry.android.core.model.CalloutTone
import com.meloberry.android.core.model.ComponentKind
import com.meloberry.android.core.model.DifferentiationQuestionModel
import com.meloberry.android.core.model.FillBlankQuestionModel
import com.meloberry.android.core.model.FlashcardQuestionModel
import com.meloberry.android.core.model.McqQuestionModel
import com.meloberry.android.core.model.MechanismQuestionModel
import com.meloberry.android.core.model.MediaPosition
import com.meloberry.android.core.model.MeloberryVersionInfo
import com.meloberry.android.core.model.RelabelImageQuestionModel
import com.meloberry.android.core.model.SpotErrorQuestionModel
import com.meloberry.android.core.model.WorkspaceKind
import com.meloberry.android.data.workspace.source.MbypkgArchiveDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MbypkgV2ArchiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun extractAndParse_parsesRealCliV2MbypkgFixtureWithAllBlockAndQuestionTypes() = runTest {
        assertTrue("2.0" in MeloberryVersionInfo.SUPPORTED_PACKAGE_VERSIONS)
        assertTrue("1.0" in MeloberryVersionInfo.SUPPORTED_PACKAGE_VERSIONS)

        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("v2_full_sample.mbypkg")
        ) { "Missing test fixture v2_full_sample.mbypkg" }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val bundle = stream.use { input ->
            archiveSource.extractAndParse(input) { id -> File(tempFolder.root, id) }
        }

        val ws = bundle.workspace
        assertEquals(WorkspaceKind.SESSION, ws.kind)
        assertEquals("Phase 2 Full V2 Fixture", ws.title)
        assertEquals(2, ws.components.size)

        // Component 1: Article with all 4 block kinds
        val article = ws.components[0]
        assertEquals(ComponentKind.ARTICLE, article.kind)
        assertEquals("intro-article", article.refKey)
        assertEquals(4, article.articleBlocks.size)

        val textBlock = article.articleBlocks[0]
        assertEquals(ArticleBlockKind.TEXT, textBlock.kind)
        assertEquals("blk-text", textBlock.refKey)
        assertTrue(textBlock.textContent!!.contains("Kinematics"))

        val imgBlock = article.articleBlocks[1]
        assertEquals(ArticleBlockKind.IMAGE, imgBlock.kind)
        assertEquals("blk-img", imgBlock.refKey)
        assertEquals("Velocity vs time", imgBlock.caption)
        assertEquals("Velocity graph", imgBlock.alt)
        assertTrue(File(imgBlock.imageFilePath!!).exists())

        val calloutBlock = article.articleBlocks[2]
        assertEquals(ArticleBlockKind.CALLOUT, calloutBlock.kind)
        assertEquals("blk-callout", calloutBlock.refKey)
        assertEquals(CalloutTone.CLINICAL, calloutBlock.calloutTone)
        assertEquals("Clinical Pearl", calloutBlock.calloutTitle)
        assertEquals("Always check units before substituting.", calloutBlock.textContent)

        val tableBlock = article.articleBlocks[3]
        assertEquals(ArticleBlockKind.TABLE, tableBlock.kind)
        assertEquals("blk-table", tableBlock.refKey)
        val table = tableBlock.tablePayload!!
        assertEquals("Comparison Table", table.title)
        assertEquals("Core quantities", table.caption)
        assertTrue(table.stickyFirstCol)
        assertEquals(listOf(2), table.hideableCols)
        assertEquals(listOf("Symbol", "Meaning", "SI Unit"), table.headers)
        assertEquals(listOf("left", "center", "right"), table.alignments)
        assertEquals(2, table.rows.size)
        assertEquals("Standard SI notation.", table.footnote)

        // Component 2: Quiz with all 8 question types
        val quiz = ws.components[1]
        assertEquals(ComponentKind.QUIZ, quiz.kind)
        assertEquals("full-quiz", quiz.refKey)
        assertEquals(8, quiz.questions.size)

        val mcq = quiz.questions[0] as McqQuestionModel
        assertEquals("q-mcq", mcq.refKey)
        assertTrue(File(mcq.promptImageFilePath!!).exists())

        val card = quiz.questions[1] as FlashcardQuestionModel
        assertEquals("q-card", card.refKey)
        assertEquals(MediaPosition.BELOW_IMAGE, card.front.position)
        assertTrue(card.front.isPositionExplicit)
        assertFalse(card.back.isPositionExplicit)

        val fb = quiz.questions[2] as FillBlankQuestionModel
        assertEquals("q-fb", fb.refKey)
        assertEquals(1, fb.payload.blanks.size)
        assertEquals(listOf("phosphofructokinase-1", "PFK-1"), fb.payload.blanks.first().answers)
        assertTrue(fb.payload.fuzzy)

        val ar = quiz.questions[3] as AssertionReasonQuestionModel
        assertEquals("q-ar", ar.refKey)
        assertEquals("both-no-explain", ar.payload.answer)
        assertEquals("4opt", ar.payload.scheme)

        val se = quiz.questions[4] as SpotErrorQuestionModel
        assertEquals("q-se", se.refKey)
        assertTrue(se.payload.requireFix)
        assertTrue(se.payload.allowNoError)
        assertFalse(se.payload.noError)
        assertTrue(File(se.payload.imageFilePath!!).exists())
        assertTrue(se.payload.segments.any { it.isError })

        val diff = quiz.questions[5] as DifferentiationQuestionModel
        assertEquals("q-diff", diff.refKey)
        assertEquals("fill", diff.payload.mode)
        assertTrue(diff.payload.bank)
        assertEquals(2, diff.payload.specialCells.count { it.isBlank })

        val mech = quiz.questions[6] as MechanismQuestionModel
        assertEquals("q-mech", mech.refKey)
        assertEquals(4, mech.payload.steps.size)
        assertEquals(2, mech.payload.steps.count { !it.isDistractor && it.orderGroup == 2 })
        assertEquals(1, mech.payload.steps.count { it.isDistractor })

        val relabel = quiz.questions[7] as RelabelImageQuestionModel
        assertEquals("q-relabel", relabel.refKey)
        assertEquals("bank", relabel.payload.mode)
        assertEquals("blur", relabel.payload.style)
        assertEquals(2, relabel.payload.boxes.size)
        assertEquals(listOf("Collecting duct"), relabel.payload.distractors)
        assertTrue(File(relabel.payload.imageFilePath).exists())
        assertEquals(relabel.payload.imageFilePath, relabel.payload.imagePath)

        // Round-trip Room entity deserialization succeeds with identical structure
        val roundTripped = archiveSource.deserializeEntity(bundle.entity)
        assertEquals(ws.components.size, roundTripped.components.size)
        assertEquals(8, roundTripped.components[1].questions.size)
    }

    @Test
    fun extractAndParse_rejectsInvalidTableAndCalloutPayloads() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        // Table with out-of-range hideableCols
        val badHideableCol = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"session","id":"Session_BadTable","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Session_BadTable","components":[{"id":"Comp_1","index":1,"kind":"Article","articleBlocks":[{"id":"Art_1","index":1,"kind":"Table","payloadJson":"{\"headers\":[\"A\",\"B\"],\"rows\":[[\"1\",\"2\"]],\"hideableCols\":[3]}"}]}]}""".encodeToByteArray()
            )
        )
        val hideableErr = runCatching {
            archiveSource.extractAndParse(ByteArrayInputStream(badHideableCol)) { id -> File(tempFolder.root, id) }
        }
        assertTrue(hideableErr.isFailure)
        assertTrue(hideableErr.exceptionOrNull()?.message?.contains("hideableCols") == true)

        // Table with empty alignments defaults cleanly to "left"
        val defaultAlignTable = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"session","id":"Session_OkTable","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Session_OkTable","components":[{"id":"Comp_1","index":1,"kind":"Article","articleBlocks":[{"id":"Art_1","index":1,"kind":"Table","payloadJson":"{\"headers\":[\"A\",\"B\"],\"rows\":[[\"1\",\"2\"]]}"}]}]}""".encodeToByteArray()
            )
        )
        val okBundle = archiveSource.extractAndParse(ByteArrayInputStream(defaultAlignTable)) { id -> File(tempFolder.root, id) }
        val tablePayload = okBundle.workspace.components.single().articleBlocks.single().tablePayload
        assertNotNull(tablePayload)
        assertEquals(listOf("left", "left"), tablePayload!!.alignments)

        // Callout with invalid tone
        val badCalloutTone = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"session","id":"Session_BadTone","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Session_BadTone","components":[{"id":"Comp_1","index":1,"kind":"Article","articleBlocks":[{"id":"Art_1","index":1,"kind":"Callout","calloutTone":"danger","textContent":"Watch out"}]}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(badCalloutTone)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )
    }

    @Test
    fun extractAndParse_rejectsDuplicateRefKeys_andInvalidV2QuestionPayloads() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        // Duplicate refKey (case-insensitive) across component and question
        val dupRefPkg = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"session","id":"Session_DupRef","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Session_DupRef","components":[{"id":"Comp_1","index":1,"kind":"Quiz","refKey":"shared-slug","quiz":{"id":"Comp_1","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"Mcq","refKey":"SHARED-SLUG","promptText":"Hi","options":[{"index":1,"text":"A","isCorrect":true},{"index":2,"text":"B","isCorrect":false}]}]}}]}""".encodeToByteArray()
            )
        )
        val dupErr = runCatching {
            archiveSource.extractAndParse(ByteArrayInputStream(dupRefPkg)) { id -> File(tempFolder.root, id) }
        }
        assertTrue(dupErr.isFailure)
        assertTrue(dupErr.exceptionOrNull()?.message?.contains("Duplicate refKey") == true)

        // Out-of-bounds RelabelBox (x + w > 1.001)
        val badRelabelBounds = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_BadRelabel","assets":["assets/pic.png"]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadRelabel","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"RelabelImage","payloadJson":"{\"imagePath\":\"assets/pic.png\",\"mode\":\"bank\",\"style\":\"solid\",\"boxes\":[{\"index\":1,\"label\":\"Aorta\",\"x\":0.8,\"y\":0.1,\"w\":0.3,\"h\":0.1}]}"}]}""".encodeToByteArray(),
                "assets/pic.png" to pngHeader
            )
        )
        val boundsErr = runCatching {
            archiveSource.extractAndParse(ByteArrayInputStream(badRelabelBounds)) { id -> File(tempFolder.root, id) }
        }
        assertTrue(boundsErr.isFailure)
        assertTrue(boundsErr.exceptionOrNull()?.message?.contains("out-of-bounds") == true)

        // RelabelImage path traversal outside assets/
        val escapeRelabelPath = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_EscapeRelabel","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_EscapeRelabel","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"RelabelImage","payloadJson":"{\"imagePath\":\"../secret.png\",\"mode\":\"bank\",\"style\":\"solid\",\"boxes\":[{\"index\":1,\"label\":\"Aorta\",\"x\":0.1,\"y\":0.1,\"w\":0.2,\"h\":0.1}]}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(escapeRelabelPath)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // FillBlank missing blanks
        val emptyFillBlank = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_BadFb","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadFb","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"FillBlank","payloadJson":"{\"template\":\"No blanks\",\"blanks\":[]}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(emptyFillBlank)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // AssertionReason invalid answer
        val badAr = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_BadAr","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadAr","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"AssertionReason","payloadJson":"{\"assertion\":\"A\",\"reason\":\"R\",\"answer\":\"unknown\",\"scheme\":\"4opt\"}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(badAr)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // Differentiation rearrange with only 2 columns
        val badDiff = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_BadDiff","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadDiff","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"Differentiation","payloadJson":"{\"mode\":\"rearrange\",\"headers\":[\"F\",\"A\"],\"rows\":[[\"x\",\"y\"]]}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(badDiff)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // Mechanism with < 2 steps
        val badMech = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_BadMech","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadMech","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"Mechanism","payloadJson":"{\"steps\":[{\"stepId\":1,\"orderGroup\":1,\"text\":\"Only one\",\"isDistractor\":false}]}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(badMech)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // Mechanism with < 2 non-distractor ordered steps
        val badMechOnlyOneOrdered = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_BadMechOrd","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadMechOrd","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"Mechanism","payloadJson":"{\"steps\":[{\"stepId\":1,\"orderGroup\":1,\"text\":\"Real\",\"isDistractor\":false},{\"stepId\":2,\"orderGroup\":0,\"text\":\"Decoy\",\"isDistractor\":true}]}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(badMechOnlyOneOrdered)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // Mechanism with duplicate stepId
        val dupMechStepId = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_DupMech","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_DupMech","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"Mechanism","payloadJson":"{\"steps\":[{\"stepId\":1,\"orderGroup\":1,\"text\":\"First\",\"isDistractor\":false},{\"stepId\":1,\"orderGroup\":2,\"text\":\"Second\",\"isDistractor\":false}]}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(dupMechStepId)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // AssertionReason with answer 'both-false' paired with 4opt scheme
        val badArSchemeMismatch = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_BadAr4Opt","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadAr4Opt","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"AssertionReason","payloadJson":"{\"assertion\":\"A\",\"reason\":\"R\",\"answer\":\"both-false\",\"scheme\":\"4opt\"}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(badArSchemeMismatch)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // FillBlank with duplicate blank slot index
        val dupFbIndex = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_DupFb","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_DupFb","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"FillBlank","payloadJson":"{\"template\":\"{{a}} {{b}}\",\"blanks\":[{\"index\":1,\"answers\":[\"a\"]},{\"index\":1,\"answers\":[\"b\"]}]}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(dupFbIndex)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // Differentiation with out-of-bounds specialCells coordinate
        val badDiffSpecialCell = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_BadDiffCell","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadDiffCell","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"Differentiation","payloadJson":"{\"mode\":\"fill\",\"headers\":[\"F\",\"A\"],\"rows\":[[\"x\",\"{{y}}\"]],\"specialCells\":[{\"rowIndex\":3,\"colIndex\":1,\"rawText\":\"{{y}}\",\"isBlank\":true,\"blankAnswers\":[\"y\"]}]}"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(badDiffSpecialCell)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // RelabelImage with duplicate box index
        val dupRelabelBox = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"2.0","kind":"quiz","id":"Set_DupRelabel","assets":["assets/pic.png"]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_DupRelabel","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"RelabelImage","payloadJson":"{\"imagePath\":\"assets/pic.png\",\"mode\":\"bank\",\"style\":\"solid\",\"boxes\":[{\"index\":1,\"label\":\"Aorta\",\"x\":0.1,\"y\":0.1,\"w\":0.2,\"h\":0.1},{\"index\":1,\"label\":\"Vena cava\",\"x\":0.4,\"y\":0.1,\"w\":0.2,\"h\":0.1}]}"}]}""".encodeToByteArray(),
                "assets/pic.png" to pngHeader
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(dupRelabelBox)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )
    }
}
