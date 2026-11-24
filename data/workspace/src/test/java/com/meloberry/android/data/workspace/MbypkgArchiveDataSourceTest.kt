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
import com.meloberry.android.core.model.RelabelImageQuestionModel
import com.meloberry.android.core.model.SpotErrorQuestionModel
import com.meloberry.android.core.model.WorkspaceKind
import com.meloberry.android.data.workspace.source.BundledSampleDataSource
import com.meloberry.android.data.workspace.source.ImageAssetVerifier
import com.meloberry.android.data.workspace.source.MbypkgArchiveDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MbypkgArchiveDataSourceTest {

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
    fun extractAndParse_unpacksBundledSampleMbypkgWithAssetsAndLayouts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sampleSource = BundledSampleDataSource()
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val bytes = sampleSource.buildSampleMbypkgBytes()
        val bundle = archiveSource.extractAndParse(ByteArrayInputStream(bytes)) { workspaceId ->
            File(tempFolder.root, workspaceId)
        }

        val ws = bundle.workspace
        assertEquals("Session_20261119_120000_001", ws.id)
        assertEquals("Meloberry Architecture & Pipeline", ws.title)
        assertTrue(ws.description.isNotBlank())
        assertEquals(WorkspaceKind.SESSION, ws.kind)
        assertEquals(2, ws.components.size)
        assertTrue(ws.globalLayouts.isEmpty())

        val article = ws.components[0]
        assertEquals(ComponentKind.ARTICLE, article.kind)
        assertEquals("Overview & Formulas", article.title)
        assertEquals(4, article.articleBlocks.size)
        val imgBlock = article.articleBlocks[1]
        assertEquals(ArticleBlockKind.IMAGE, imgBlock.kind)
        assertTrue(!imgBlock.caption.isNullOrBlank())
        assertTrue(!imgBlock.alt.isNullOrBlank())
        assertTrue(File(imgBlock.imageFilePath!!).exists())
        val calloutBlock = article.articleBlocks[2]
        assertEquals(ArticleBlockKind.CALLOUT, calloutBlock.kind)
        assertEquals(CalloutTone.CLINICAL, calloutBlock.calloutTone)
        val tableBlock = article.articleBlocks[3]
        assertEquals(ArticleBlockKind.TABLE, tableBlock.kind)
        assertTrue(tableBlock.tablePayload!!.stickyFirstCol)
        assertEquals(listOf(2, 3), tableBlock.tablePayload!!.hideableCols)

        val quizComp = ws.components[1]
        assertEquals(ComponentKind.QUIZ, quizComp.kind)
        assertEquals("Architecture Check", quizComp.title)
        assertEquals(9, quizComp.questions.size)
        val mcq = quizComp.questions[0] as McqQuestionModel
        assertEquals(4, mcq.options.size)
        assertTrue(!mcq.explanation.isNullOrBlank())
        assertNull(mcq.layoutOverrides["optionColumns"])
        val card = quizComp.questions[2] as FlashcardQuestionModel
        assertEquals(MediaPosition.ABOVE_IMAGE, card.front.position)
        assertTrue(card.front.isPositionExplicit)
        assertTrue(File(card.front.imageFilePath!!).exists())
        assertTrue(quizComp.questions[3] is FillBlankQuestionModel)
        assertTrue(quizComp.questions[4] is AssertionReasonQuestionModel)
        assertTrue(quizComp.questions[5] is SpotErrorQuestionModel)
        assertTrue(quizComp.questions[6] is DifferentiationQuestionModel)
        assertTrue(quizComp.questions[7] is MechanismQuestionModel)
        val relabel = quizComp.questions[8] as RelabelImageQuestionModel
        assertEquals(3, relabel.payload.boxes.size)
        assertTrue(File(relabel.payload.imageFilePath).exists())
    }

    @Test
    fun extractAndParse_blocksZipSlipTraversal_andRootedPaths() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val zipSlipBytes = buildZip(listOf("../../evil.txt" to "hack".encodeToByteArray()))
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(zipSlipBytes)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        val rootedManifestBytes = buildZip(
            listOf(
                "/manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_Rooted","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_Rooted","quizType":"Mcq","questions":[]}""".encodeToByteArray()
            )
        )
        val rootedResult = runCatching {
            archiveSource.extractAndParse(ByteArrayInputStream(rootedManifestBytes)) { id -> File(tempFolder.root, id) }
        }
        assertTrue(rootedResult.isFailure)
        assertTrue(rootedResult.exceptionOrNull()?.message?.contains("rooted", ignoreCase = true) == true)
    }

    @Test
    fun extractAndParse_rejectsDotId_preservesSiblingWorkspaces_andAllowsAssetWithDoubleDotInFilename() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val victimDir = File(tempFolder.root, "Session_victim").apply { mkdirs() }
        val victimAsset = File(victimDir, "keep.txt").apply { writeText("important") }

        val dotManifestPkg = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":".","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":".","quizType":"Mcq","questions":[]}""".encodeToByteArray()
            )
        )

        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(dotManifestPkg)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )
        assertTrue(victimAsset.exists())

        val existingTarget = File(tempFolder.root, "Set_existing").apply { mkdirs() }
        val existingMarker = File(existingTarget, "assets/existing.png").apply {
            parentFile?.mkdirs()
            writeText("keep")
        }
        val corruptWsPkg = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_existing","assets":[]}""".encodeToByteArray(),
                "workspace.json" to "{not valid json".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(corruptWsPkg)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )
        assertTrue(existingMarker.exists())

        val dottedAssetPkg = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_dotted","assets":["assets/fig..1.png"]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_dotted","quizType":"Mcq","questions":[{"id":"Q_1","index":1,"questionType":"Mcq","promptText":"Test","maxOptions":2,"isMultiCorrect":false,"options":[{"index":1,"text":"A","imagePath":"assets/fig..1.png","isCorrect":true},{"index":2,"text":"B","isCorrect":false}]}]}""".encodeToByteArray(),
                "assets/fig..1.png" to pngHeader
            )
        )

        val okBundle = archiveSource.extractAndParse(ByteArrayInputStream(dottedAssetPkg)) { id -> File(tempFolder.root, id) }
        val mcq = okBundle.workspace.standaloneQuestions.first() as McqQuestionModel
        assertTrue(File(mcq.options.first().imageFilePath!!).exists())
    }

    @Test
    fun extractAndParse_rejectsBlankIds_mismatchedNestedQuizId_malformedLayouts_andUnknownKinds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        // Blank question ID
        val blankQuestionId = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_BlankQ","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BlankQ","quizType":"Mcq","questions":[{"id":"","index":1,"questionType":"Mcq","promptText":"Q","options":[{"index":1,"text":"A","isCorrect":true},{"index":2,"text":"B","isCorrect":false}]}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(blankQuestionId)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // Blank component ID
        val blankComponentId = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"session","id":"Session_BlankC","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Session_BlankC","components":[{"id":"  ","index":1,"kind":"Article","articleBlocks":[]}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(blankComponentId)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        // nestedQuiz.id != component.id
        val mismatchedNestedQuiz = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"session","id":"Session_MismatchQuiz","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Session_MismatchQuiz","components":[{"id":"Comp_1","index":1,"kind":"Quiz","quiz":{"id":"Comp_OTHER","quizType":"Mcq","questions":[]}}]}""".encodeToByteArray()
            )
        )
        val mismatchRes = runCatching {
            archiveSource.extractAndParse(ByteArrayInputStream(mismatchedNestedQuiz)) { id -> File(tempFolder.root, id) }
        }
        assertTrue(mismatchRes.isFailure)
        assertTrue(mismatchRes.exceptionOrNull()?.message?.contains("does not match component ID") == true)

        // Malformed layouts.json
        val malformedLayouts = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_BadLayouts","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadLayouts","quizType":"Mcq","questions":[]}""".encodeToByteArray(),
                "layouts.json" to "{not valid json".encodeToByteArray()
            )
        )
        val layoutsRes = runCatching {
            archiveSource.extractAndParse(ByteArrayInputStream(malformedLayouts)) { id -> File(tempFolder.root, id) }
        }
        assertTrue(layoutsRes.isFailure)
        assertTrue(layoutsRes.exceptionOrNull() is IllegalArgumentException)

        // Unknown component.kind
        val unknownComponentKind = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"session","id":"Session_BadKind","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Session_BadKind","components":[{"id":"Comp_1","index":1,"kind":"Video"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(unknownComponentKind)) { id -> File(tempFolder.root, id) }
            }.exceptionOrNull() is IllegalArgumentException
        )

        // Unknown article block kind
        val unknownBlockKind = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"session","id":"Session_BadBlock","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Session_BadBlock","components":[{"id":"Comp_1","index":1,"kind":"Article","articleBlocks":[{"id":"Art_1","index":1,"kind":"UnknownBlock"}]}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(unknownBlockKind)) { id -> File(tempFolder.root, id) }
            }.exceptionOrNull() is IllegalArgumentException
        )

        // Unknown questionType
        val unknownQuestionType = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_BadQType","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadQType","quizType":"Mixed","questions":[{"id":"Q_1","index":1,"questionType":"Essay"}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(unknownQuestionType)) { id -> File(tempFolder.root, id) }
            }.exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun extractAndParse_preservesSubdirectoryAssets_andRejectsDuplicateAssetPaths() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val nestedPkg = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_NestedAssets","assets":["assets/sub1/fig.png","assets/sub2/fig.png"]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_NestedAssets","quizType":"Mcq","questions":[{"id":"Q_1","index":1,"questionType":"Mcq","promptText":"Test","promptImagePath":"assets/sub1/fig.png","options":[{"index":1,"text":"A","imagePath":"assets/sub2/fig.png","isCorrect":true},{"index":2,"text":"B","isCorrect":false}]}]}""".encodeToByteArray(),
                "assets/sub1/fig.png" to (pngHeader + byteArrayOf(1)),
                "assets/sub2/fig.png" to (pngHeader + byteArrayOf(2))
            )
        )
        val bundle = archiveSource.extractAndParse(ByteArrayInputStream(nestedPkg)) { id -> File(tempFolder.root, id) }
        val mcq = bundle.workspace.standaloneQuestions.single() as McqQuestionModel
        val file1 = File(mcq.promptImageFilePath!!)
        val file2 = File(mcq.options.first().imageFilePath!!)
        assertTrue(file1.exists() && file2.exists())
        assertEquals(1.toByte(), file1.readBytes().last())
        assertEquals(2.toByte(), file2.readBytes().last())

        // Duplicate asset entry (case-insensitive collision)
        val duplicatePkg = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_Dup","assets":["assets/fig.png"]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_Dup","quizType":"Mcq","questions":[]}""".encodeToByteArray(),
                "assets/fig.png" to pngHeader,
                "assets/FIG.png" to pngHeader
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(duplicatePkg)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )
    }

    @Test
    fun stageAndValidate_doesNotTouchTargetDirUntilCommitAssetsIsCalled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)
        val targetDir = File(tempFolder.root, "Session_20261119_120000_001")
        val bytes = BundledSampleDataSource().buildSampleMbypkgBytes()

        archiveSource.stageAndValidate(ByteArrayInputStream(bytes)) { id -> File(tempFolder.root, id) }.use { staged ->
            assertFalse(targetDir.exists())
            staged.commitAssets()
            assertTrue(File(targetDir, "assets/meloberry_flow.svg").exists())
        }
    }

    @Test
    fun imageAssetVerifier_headerOnlyRasterCheckHandlesLargeFile_andEnforcesSvgSizeCap() {
        // Large raster PNG (> MAX_SVG_BYTES) verified purely via 16-byte header
        val largePng = File(tempFolder.root, "large.png")
        RandomAccessFile(largePng, "rw").use { raf ->
            raf.setLength(ImageAssetVerifier.MAX_SVG_BYTES + 2L * 1024L * 1024L)
            raf.seek(0)
            raf.write(pngHeader)
        }
        assertTrue(ImageAssetVerifier.isValidSafeImage(largePng))

        // Oversized SVG (> MAX_SVG_BYTES) rejected before loading into heap
        val oversizedSvg = File(tempFolder.root, "huge.svg")
        RandomAccessFile(oversizedSvg, "rw").use { raf ->
            raf.setLength(ImageAssetVerifier.MAX_SVG_BYTES + 1024L)
            raf.seek(0)
            raf.write("<svg xmlns='http://www.w3.org/2000/svg'>".encodeToByteArray())
        }
        assertFalse(ImageAssetVerifier.isValidSafeImage(oversizedSvg))

        // Normal sanitized SVG accepted
        val validSvg = File(tempFolder.root, "ok.svg").apply {
            writeText("<svg xmlns='http://www.w3.org/2000/svg'><circle r='5'/></svg>")
        }
        assertTrue(ImageAssetVerifier.isValidSafeImage(validSvg))
    }

    @Test
    fun extractAndParse_rejectsMismatchedWorkspaceId_andInvalidManifestKind() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val mismatchedIdBytes = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_manifest","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"../../evil_id","quizType":"Mcq","questions":[]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(mismatchedIdBytes)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        val invalidKindBytes = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"unknown_kind","id":"Set_badkind","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_badkind","quizType":"Mcq","questions":[]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(invalidKindBytes)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )
    }

    @Test
    fun extractAndParse_parsesStandaloneQuizMetadata_mcqPromptImageAndExplanation_andCaseInsensitiveId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val bytes = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_Demo_1","assets":["assets/prompt.png"]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"set_demo_1","quizType":"Mcq","name":"Kinematics Quiz","description":"Velocity & acceleration","questions":[{"id":"Q_1","index":1,"questionType":"Mcq","promptText":"Identify curve","promptImagePath":"assets/prompt.png","explanation":"Slope is acceleration.","maxOptions":2,"isMultiCorrect":false,"options":[{"index":1,"text":"A","isCorrect":true},{"index":2,"text":"B","isCorrect":false}]}]}""".encodeToByteArray(),
                "assets/prompt.png" to pngHeader
            )
        )

        val bundle = archiveSource.extractAndParse(ByteArrayInputStream(bytes)) { id -> File(tempFolder.root, id) }
        val ws = bundle.workspace
        assertEquals("Kinematics Quiz", ws.title)
        assertEquals("Velocity & acceleration", ws.description)
        val mcq = ws.standaloneQuestions.single() as McqQuestionModel
        assertTrue(File(mcq.promptImageFilePath!!).exists())
        assertEquals("Slope is acceleration.", mcq.explanation)
    }

    @Test
    fun imageAssetVerifier_andArchiveSource_rejectCorruptOrDangerousAssets_andPathsOutsideAssets() = runTest {
        assertTrue(ImageAssetVerifier.isValidSafeImage(pngHeader))
        assertTrue(ImageAssetVerifier.isValidSafeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertTrue(ImageAssetVerifier.isValidSafeImage("GIF89a".encodeToByteArray()))
        assertTrue(ImageAssetVerifier.isValidSafeImage("RIFF\u0000\u0000\u0000\u0000WEBP".encodeToByteArray()))
        assertTrue(ImageAssetVerifier.isValidSafeImage("BM".encodeToByteArray()))
        assertTrue(ImageAssetVerifier.isValidSafeImage("<svg xmlns='http://www.w3.org/2000/svg'><circle r='5'/></svg>".encodeToByteArray()))

        assertFalse(ImageAssetVerifier.isValidSafeImage("not an image".encodeToByteArray()))
        assertFalse(ImageAssetVerifier.isValidSafeImage("<svg><script>alert(1)</script></svg>".encodeToByteArray()))
        assertFalse(ImageAssetVerifier.isValidSafeImage("<svg onload='alert(1)'></svg>".encodeToByteArray()))

        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val dangerousSvgPkg = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_BadSvg","assets":["assets/bad.svg"]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadSvg","quizType":"Mcq","questions":[]}""".encodeToByteArray(),
                "assets/bad.svg" to "<svg><script>evil()</script></svg>".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(dangerousSvgPkg)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )

        val outsideAssetsPkg = buildZip(
            listOf(
                "manifest.json" to """{"packageVersion":"1.0","kind":"quiz","id":"Set_BadPath","assets":[]}""".encodeToByteArray(),
                "workspace.json" to """{"id":"Set_BadPath","quizType":"Mcq","questions":[{"id":"Q_1","index":1,"questionType":"Mcq","promptText":"Test","promptImagePath":"../secret.png","options":[{"index":1,"text":"A","isCorrect":true},{"index":2,"text":"B","isCorrect":false}]}]}""".encodeToByteArray()
            )
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(outsideAssetsPkg)) { id -> File(tempFolder.root, id) }
            }.isFailure
        )
    }
}
