package com.meloberry.android.data.workspace

import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.ComponentKind
import com.meloberry.android.core.model.FlashcardQuestionModel
import com.meloberry.android.core.model.McqQuestionModel
import com.meloberry.android.core.model.MediaPosition
import com.meloberry.android.core.model.WorkspaceKind
import com.meloberry.android.data.workspace.source.BundledSampleDataSource
import com.meloberry.android.data.workspace.source.ImageAssetVerifier
import com.meloberry.android.data.workspace.source.MbypkgArchiveDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MbypkgArchiveDataSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
        assertEquals(3, article.articleBlocks.size)
        val imgBlock = article.articleBlocks[1]
        assertEquals(ArticleBlockKind.IMAGE, imgBlock.kind)
        assertTrue(File(imgBlock.imageFilePath!!).exists())

        val quizComp = ws.components[1]
        assertEquals(ComponentKind.QUIZ, quizComp.kind)
        assertEquals("Architecture Check", quizComp.title)
        val mcq = quizComp.questions[0] as McqQuestionModel
        assertEquals(4, mcq.options.size)
        assertTrue(!mcq.explanation.isNullOrBlank())
        assertNull(mcq.layoutOverrides["optionColumns"])
        val card = quizComp.questions[2] as FlashcardQuestionModel
        assertEquals(MediaPosition.ABOVE_IMAGE, card.front.position)
        assertTrue(File(card.front.imageFilePath!!).exists())
    }

    @Test
    fun extractAndParse_blocksZipSlipTraversal() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("../../evil.txt"))
            zip.write("hack".encodeToByteArray())
            zip.closeEntry()
        }

        val result = runCatching {
            archiveSource.extractAndParse(ByteArrayInputStream(baos.toByteArray())) { id ->
                File(tempFolder.root, id)
            }
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun extractAndParse_rejectsDotId_preservesSiblingWorkspaces_andAllowsAssetWithDoubleDotInFilename() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        // Pre-populate an existing workspace that must survive invalid imports
        val victimDir = File(tempFolder.root, "Session_victim").apply { mkdirs() }
        val victimAsset = File(victimDir, "keep.txt").apply { writeText("important") }

        val dotManifestPkg = ByteArrayOutputStream()
        ZipOutputStream(dotManifestPkg).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"packageVersion":"1.0","kind":"quiz","id":".","assets":[]}""".encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("workspace.json"))
            zip.write("""{"id":".","quizType":"Mcq","questions":[]}""".encodeToByteArray())
            zip.closeEntry()
        }

        val dotResult = runCatching {
            archiveSource.extractAndParse(ByteArrayInputStream(dotManifestPkg.toByteArray())) { id ->
                File(tempFolder.root, id)
            }
        }
        assertTrue(dotResult.isFailure)
        assertTrue(victimAsset.exists())

        // Broken workspace.json with existing target ID must not wipe existing targetDir
        val existingTarget = File(tempFolder.root, "Set_existing").apply { mkdirs() }
        val existingMarker = File(existingTarget, "assets/existing.png").apply {
            parentFile?.mkdirs()
            writeText("keep")
        }
        val corruptWsPkg = ByteArrayOutputStream()
        ZipOutputStream(corruptWsPkg).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"packageVersion":"1.0","kind":"quiz","id":"Set_existing","assets":[]}""".encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("workspace.json"))
            zip.write("{not valid json".encodeToByteArray())
            zip.closeEntry()
        }
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(corruptWsPkg.toByteArray())) { id ->
                    File(tempFolder.root, id)
                }
            }.isFailure
        )
        assertTrue(existingMarker.exists())

        // Valid asset filename containing ".." (not a traversal segment) succeeds
        val dottedAssetPkg = ByteArrayOutputStream()
        ZipOutputStream(dottedAssetPkg).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"packageVersion":"1.0","kind":"quiz","id":"Set_dotted","assets":["assets/fig..1.png"]}""".encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("workspace.json"))
            zip.write(
                """{"id":"Set_dotted","quizType":"Mcq","questions":[{"id":"Q_1","index":1,"questionType":"Mcq","promptText":"Test","maxOptions":2,"isMultiCorrect":false,"options":[{"index":1,"text":"A","imagePath":"assets/fig..1.png","isCorrect":true},{"index":2,"text":"B","isCorrect":false}]}]}""".encodeToByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("assets/fig..1.png"))
            zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            zip.closeEntry()
        }

        val okBundle = archiveSource.extractAndParse(ByteArrayInputStream(dottedAssetPkg.toByteArray())) { id ->
            File(tempFolder.root, id)
        }
        val mcq = okBundle.workspace.standaloneQuestions.first() as McqQuestionModel
        assertTrue(File(mcq.options.first().imageFilePath!!).exists())
    }

    @Test
    fun extractAndParse_rejectsMismatchedWorkspaceId_andInvalidManifestKind() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        fun buildZip(manifestJson: String, workspaceJson: String): ByteArray {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifestJson.encodeToByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("workspace.json"))
                zip.write(workspaceJson.encodeToByteArray())
                zip.closeEntry()
            }
            return baos.toByteArray()
        }

        val mismatchedIdBytes = buildZip(
            manifestJson = """{"packageVersion":"1.0","kind":"quiz","id":"Set_manifest","assets":[]}""",
            workspaceJson = """{"id":"../../evil_id","quizType":"Mcq","questions":[]}"""
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(mismatchedIdBytes)) { id ->
                    File(tempFolder.root, id)
                }
            }.isFailure
        )

        val invalidKindBytes = buildZip(
            manifestJson = """{"packageVersion":"1.0","kind":"unknown_kind","id":"Set_badkind","assets":[]}""",
            workspaceJson = """{"id":"Set_badkind","quizType":"Mcq","questions":[]}"""
        )
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(invalidKindBytes)) { id ->
                    File(tempFolder.root, id)
                }
            }.isFailure
        )
    }

    @Test
    fun extractAndParse_parsesStandaloneQuizMetadata_mcqPromptImageAndExplanation_andCaseInsensitiveId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"packageVersion":"1.0","kind":"quiz","id":"Set_Demo_1","assets":["assets/prompt.png"]}""".encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("workspace.json"))
            zip.write(
                """{"id":"set_demo_1","quizType":"Mcq","name":"Kinematics Quiz","description":"Velocity & acceleration","questions":[{"id":"Q_1","index":1,"questionType":"Mcq","promptText":"Identify curve","promptImagePath":"assets/prompt.png","explanation":"Slope is acceleration.","maxOptions":2,"isMultiCorrect":false,"options":[{"index":1,"text":"A","isCorrect":true},{"index":2,"text":"B","isCorrect":false}]}]}""".encodeToByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("assets/prompt.png"))
            zip.write(pngHeader)
            zip.closeEntry()
        }

        val bundle = archiveSource.extractAndParse(ByteArrayInputStream(baos.toByteArray())) { id ->
            File(tempFolder.root, id)
        }
        val ws = bundle.workspace
        assertEquals("Kinematics Quiz", ws.title)
        assertEquals("Velocity & acceleration", ws.description)
        val mcq = ws.standaloneQuestions.single() as McqQuestionModel
        assertTrue(File(mcq.promptImageFilePath!!).exists())
        assertEquals("Slope is acceleration.", mcq.explanation)
    }

    @Test
    fun imageAssetVerifier_andArchiveSource_rejectCorruptOrDangerousAssets_andPathsOutsideAssets() = runTest {
        assertTrue(ImageAssetVerifier.isValidSafeImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertTrue(ImageAssetVerifier.isValidSafeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertTrue(ImageAssetVerifier.isValidSafeImage("GIF89a".encodeToByteArray()))
        assertTrue(ImageAssetVerifier.isValidSafeImage("RIFF\u0000\u0000\u0000\u0000WEBP".encodeToByteArray()))
        assertTrue(ImageAssetVerifier.isValidSafeImage("BM".encodeToByteArray()))
        assertTrue(ImageAssetVerifier.isValidSafeImage("<svg xmlns='http://www.w3.org/2000/svg'><circle r='5'/></svg>".encodeToByteArray()))

        assertTrue(!ImageAssetVerifier.isValidSafeImage("not an image".encodeToByteArray()))
        assertTrue(!ImageAssetVerifier.isValidSafeImage("<svg><script>alert(1)</script></svg>".encodeToByteArray()))
        assertTrue(!ImageAssetVerifier.isValidSafeImage("<svg onload='alert(1)'></svg>".encodeToByteArray()))

        val dispatcher = StandardTestDispatcher(testScheduler)
        val archiveSource = MbypkgArchiveDataSource(dispatcher)

        val dangerousSvgPkg = ByteArrayOutputStream()
        ZipOutputStream(dangerousSvgPkg).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"packageVersion":"1.0","kind":"quiz","id":"Set_BadSvg","assets":["assets/bad.svg"]}""".encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("workspace.json"))
            zip.write("""{"id":"Set_BadSvg","quizType":"Mcq","questions":[]}""".encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("assets/bad.svg"))
            zip.write("<svg><script>evil()</script></svg>".encodeToByteArray())
            zip.closeEntry()
        }
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(dangerousSvgPkg.toByteArray())) { id ->
                    File(tempFolder.root, id)
                }
            }.isFailure
        )

        val outsideAssetsPkg = ByteArrayOutputStream()
        ZipOutputStream(outsideAssetsPkg).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"packageVersion":"1.0","kind":"quiz","id":"Set_BadPath","assets":[]}""".encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("workspace.json"))
            zip.write(
                """{"id":"Set_BadPath","quizType":"Mcq","questions":[{"id":"Q_1","index":1,"questionType":"Mcq","promptText":"Test","promptImagePath":"../secret.png","options":[{"index":1,"text":"A","isCorrect":true},{"index":2,"text":"B","isCorrect":false}]}]}""".encodeToByteArray()
            )
            zip.closeEntry()
        }
        assertTrue(
            runCatching {
                archiveSource.extractAndParse(ByteArrayInputStream(outsideAssetsPkg.toByteArray())) { id ->
                    File(tempFolder.root, id)
                }
            }.isFailure
        )
    }
}
