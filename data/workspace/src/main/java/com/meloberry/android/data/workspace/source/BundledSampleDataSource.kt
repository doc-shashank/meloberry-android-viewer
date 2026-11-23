package com.meloberry.android.data.workspace.source

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BundledSampleDataSource @Inject constructor() {

    private val json = Json { prettyPrint = true }

    fun buildSampleMbypkgBytes(): ByteArray {
        val sampleId = "Session_20261119_120000_001"
        val manifest = PackageManifestDto(
            packageVersion = "1.0",
            kind = "session",
            id = sampleId,
            exportedAtUtc = "2026-11-19T12:00:00Z",
            assets = listOf("assets/meloberry_flow.svg"),
            targetPlatform = "android"
        )

        val session = SessionPackageDto(
            id = sampleId,
            activeComponentId = "Component_20261119_120001_001",
            name = "Meloberry Architecture & Pipeline",
            description = "Interactive study session covering Meloberry articles, zoomable SVG diagrams, vertical MCQs, and 3D flashcards.",
            components = listOf(
                ComponentPackageDto(
                    id = "Component_20261119_120001_001",
                    index = 1,
                    kind = "Article",
                    title = "Overview & Formulas",
                    articleBlocks = listOf(
                        ArticleBlockPackageDto(
                            id = "ArtComp_20261119_120002_001",
                            index = 1,
                            kind = "Text",
                            textContent = "# Welcome to Meloberry Android\n\nMeloberry `.mbypkg` bundles run **100% natively** in Jetpack Compose — supporting **Markdown + LaTeX + SVG/GIF/Image Articles**, single & multi-correct **MCQs** with explanations, 3D-flipping **Flashcards**, and full telemetry export back to `mby analyze`.\n\n\$\$v = u + at\$\$\n\n- [x] Offline LaTeX math (like \$E = mc^2\$) rendered natively without WebView\n- [x] Task lists, ~~strikethrough~~, tables, and [safe links](https://example.com)\n- [ ] Export your own package with `mby pkg export <wsId> android`"
                        ),
                        ArticleBlockPackageDto(
                            id = "ArtComp_20261119_120003_002",
                            index = 2,
                            kind = "Image",
                            imagePath = "assets/meloberry_flow.svg"
                        ),
                        ArticleBlockPackageDto(
                            id = "ArtComp_20261119_120004_003",
                            index = 3,
                            kind = "Text",
                            textContent = "- **3-Tier Layouts:** Global defaults -> Component overrides -> Question overrides\n- **Offline Telemetry:** Track dwell time, first-try accuracy, and review flags"
                        )
                    )
                ),
                ComponentPackageDto(
                    id = "Component_20261119_120005_002",
                    index = 2,
                    kind = "Quiz",
                    title = "Architecture Check",
                    quiz = QuizPackageDto(
                        id = "Component_20261119_120005_002",
                        quizType = "Mixed",
                        questions = listOf(
                            QuestionPackageDto(
                                id = "Q_20261119_120006_001",
                                index = 1,
                                questionType = "Mcq",
                                promptText = "Which archive file inside `.mbypkg` declares the workspace `kind` (`session` vs `quiz`) and `id`?",
                                explanation = "`manifest.json` declares `packageVersion`, `kind`, `id`, `exportedAtUtc`, `assets[]`, and `targetPlatform`.",
                                maxOptions = 4,
                                isMultiCorrect = false,
                                options = listOf(
                                    OptionPackageDto(1, "`layouts.json`", null, false),
                                    OptionPackageDto(2, "`manifest.json`", null, true),
                                    OptionPackageDto(3, "`app_settings.db`", null, false),
                                    OptionPackageDto(4, "`index.html`", null, false)
                                )
                            ),
                            QuestionPackageDto(
                                id = "Q_20261119_120007_002",
                                index = 2,
                                questionType = "Mcq",
                                promptText = "Select **all** properties supported by Meloberry's 13-property `LayoutBoundsCatalog`:",
                                explanation = "Both `slideTransitionMs` (`100..1200`) and `flipDurationMs` (`150..1000`) are bounded in `LayoutBoundsCatalog`.",
                                maxOptions = 3,
                                isMultiCorrect = true,
                                options = listOf(
                                    OptionPackageDto(1, "`slideTransitionMs` (`100..1200`)", null, true),
                                    OptionPackageDto(2, "`flipDurationMs` (`150..1000`)", null, true),
                                    OptionPackageDto(3, "`networkTimeoutSec`", null, false)
                                )
                            ),
                            QuestionPackageDto(
                                id = "Q_20261119_120008_003",
                                index = 3,
                                questionType = "Flashcard",
                                front = SidePackageDto(
                                    text = "### Meloberry Pipeline\nBinary search complexity: \$O(\\log n)\$ — tap card to flip",
                                    imagePath = "assets/meloberry_flow.svg",
                                    position = "TextAboveImage"
                                ),
                                back = SidePackageDto(
                                    text = "**`mby pkg export <wsId> android`** runs mandatory `check` lint and exports a verified `.mbypkg` archive.",
                                    imagePath = null,
                                    position = "TextBelowImage"
                                )
                            )
                        )
                    )
                )
            )
        )

        val svgContent = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 200">
              <rect width="640" height="200" rx="18" fill="#FCE8EF"/>
              <rect x="28" y="54" width="160" height="92" rx="14" fill="#FFFFFF" stroke="#B8295B" stroke-width="3"/>
              <text x="108" y="96" font-family="sans-serif" font-size="16" font-weight="bold" fill="#1F1722" text-anchor="middle">mby.exe</text>
              <text x="108" y="120" font-family="sans-serif" font-size="13" fill="#6C5B70" text-anchor="middle">Lint &amp; Export</text>
              <path d="M196 100 L234 100" stroke="#B8295B" stroke-width="4" stroke-linecap="round"/>
              <polygon points="234,92 248,100 234,108" fill="#B8295B"/>
              <rect x="252" y="54" width="144" height="92" rx="14" fill="#B8295B"/>
              <text x="324" y="96" font-family="sans-serif" font-size="16" font-weight="bold" fill="#FFFFFF" text-anchor="middle">.mbypkg</text>
              <text x="324" y="120" font-family="sans-serif" font-size="13" fill="#FCE8EF" text-anchor="middle">Portable ZIP</text>
              <path d="M404 100 L442 100" stroke="#B8295B" stroke-width="4" stroke-linecap="round"/>
              <polygon points="442,92 456,100 442,108" fill="#B8295B"/>
              <rect x="460" y="54" width="152" height="92" rx="14" fill="#FFFFFF" stroke="#B8295B" stroke-width="3"/>
              <text x="536" y="96" font-family="sans-serif" font-size="16" font-weight="bold" fill="#1F1722" text-anchor="middle">Android App</text>
              <text x="536" y="120" font-family="sans-serif" font-size="13" fill="#6C5B70" text-anchor="middle">Native Compose</text>
            </svg>
        """.trimIndent().encodeToByteArray()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            writeZipEntry(zip, "manifest.json", json.encodeToString(manifest).encodeToByteArray())
            writeZipEntry(zip, "workspace.json", json.encodeToString(session).encodeToByteArray())
            writeZipEntry(zip, "assets/meloberry_flow.svg", svgContent)
        }
        return baos.toByteArray()
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }
}
