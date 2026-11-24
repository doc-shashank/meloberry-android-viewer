package com.meloberry.android.data.workspace.source

import com.meloberry.android.core.model.AssertionReasonPayload
import com.meloberry.android.core.model.DiffCellSpec
import com.meloberry.android.core.model.DifferentiationPayload
import com.meloberry.android.core.model.FillBlankPayload
import com.meloberry.android.core.model.FillBlankSlot
import com.meloberry.android.core.model.MechanismPayload
import com.meloberry.android.core.model.MechanismStep
import com.meloberry.android.core.model.MeloberryVersionInfo
import com.meloberry.android.core.model.RelabelBox
import com.meloberry.android.core.model.RelabelImagePayload
import com.meloberry.android.core.model.SpotErrorPayload
import com.meloberry.android.core.model.SpotErrorSegment
import com.meloberry.android.core.model.TableBlockPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BundledSampleDataSource @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun buildSampleMbypkgBytes(): ByteArray {
        val sampleId = "Session_20261119_120000_001"
        val versionLabel = "mby v${MeloberryVersionInfo.VERSION_NAME}"
        val manifest = PackageManifestDto(
            packageVersion = MeloberryVersionInfo.TARGET_PACKAGE_VERSION,
            kind = "session",
            id = sampleId,
            exportedAtUtc = "2026-11-25T00:00:00Z",
            assets = listOf("assets/meloberry_flow.svg"),
            targetPlatform = "android"
        )

        val tablePayloadJson = json.encodeToString(
            TableBlockPayload(
                title = "Meloberry v2.0 Artifact Matrix",
                caption = "Tap **Hide/Show** pills to drill active recall on column 2 & 3.",
                stickyFirstCol = true,
                hideableCols = listOf(2, 3),
                headers = listOf("Artifact", "Format", "Primary Role"),
                alignments = listOf("left", "center", "left"),
                rows = listOf(
                    listOf("`manifest.json`", "JSON", "Declares `packageVersion`, `kind`, `id`, and `assets[]`"),
                    listOf("`workspace.json`", "JSON", "Stores articles, callouts, tables, and all 8 question types"),
                    listOf("`layouts.json`", "JSON", "Global layout profiles (`mcq`, `flashcard`, `article`)")
                ),
                footnote = "Supports both packageVersion 1.0 and 2.0 archives."
            )
        )

        val fillBlankPayloadJson = json.encodeToString(
            FillBlankPayload(
                template = "The Meloberry CLI exports offline Android bundles as {{.mbypkg|mbypkg}} archives using {{packageVersion}} = `2.0`.",
                blanks = listOf(
                    FillBlankSlot(1, listOf(".mbypkg", "mbypkg")),
                    FillBlankSlot(2, listOf("packageVersion"))
                ),
                fuzzy = true,
                caseSensitive = false,
                explanation = "`.mbypkg` archives declare `packageVersion` inside `manifest.json` and unpack safely via staged validation."
            )
        )

        val assertionReasonPayloadJson = json.encodeToString(
            AssertionReasonPayload(
                assertion = "Meloberry Android renders `$\\LaTeX$` formulas, interactive tables, and all 8 question types completely offline.",
                reason = "All rendering and grading use native Jetpack Compose, `jlatexmath-android`, and pure-Kotlin domain evaluators without WebView.",
                answer = "both-explain",
                scheme = "4opt",
                explanation = "Zero-WebView native rendering guarantees offline determinism and fast scrolling."
            )
        )

        val spotErrorPayloadJson = json.encodeToString(
            SpotErrorPayload(
                template = "During import, [MbypkgArchiveDataSource] extracts files into a [staging directory] and skips [~~all image checks~~ -> magic-byte & SVG verification].",
                segments = listOf(
                    SpotErrorSegment(null, "During import,", isCandidate = false, isError = false),
                    SpotErrorSegment(1, "MbypkgArchiveDataSource", isCandidate = true, isError = false),
                    SpotErrorSegment(null, "extracts files into a", isCandidate = false, isError = false),
                    SpotErrorSegment(2, "staging directory", isCandidate = true, isError = false),
                    SpotErrorSegment(null, "and skips", isCandidate = false, isError = false),
                    SpotErrorSegment(
                        3,
                        "all image checks",
                        isCandidate = true,
                        isError = true,
                        corrections = listOf("magic-byte & SVG verification", "image verification")
                    )
                ),
                requireFix = true,
                allowNoError = true,
                noError = false,
                explanation = "`ImageAssetVerifier` validates magic bytes for rasters and sanitizes SVG payloads before committing staged assets."
            )
        )

        val differentiationPayloadJson = json.encodeToString(
            DifferentiationPayload(
                mode = "fill",
                title = "Layout Target Inheritance",
                bank = true,
                fuzzy = true,
                caseSensitive = false,
                allowNoError = false,
                headers = listOf("Content Kind", "Inherited Layout Profile"),
                alignments = listOf("left", "center"),
                rows = listOf(
                    listOf("Article Callout & Table", "{{article}}"),
                    listOf("Fill-Blank & Mechanism", "{{mcq}}"),
                    listOf("3D Flashcard", "{{flashcard}}")
                ),
                specialCells = listOf(
                    DiffCellSpec(0, 1, "{{article}}", isBlank = true, blankAnswers = listOf("article")),
                    DiffCellSpec(1, 1, "{{mcq}}", isBlank = true, blankAnswers = listOf("mcq")),
                    DiffCellSpec(2, 1, "{{flashcard}}", isBlank = true, blankAnswers = listOf("flashcard"))
                ),
                explanation = "All 6 new interactive question types inherit `mcq` layout bounds, while tables and callouts inherit `article`."
            )
        )

        val mechanismPayloadJson = json.encodeToString(
            MechanismPayload(
                title = "Safe `.mbypkg` Import Pipeline",
                steps = listOf(
                    MechanismStep(1, 1, "Stream ZIP entries into isolated staging folder with byte caps", isDistractor = false),
                    MechanismStep(2, 2, "Verify raster magic bytes & sanitize SVG assets", isDistractor = false),
                    MechanismStep(3, 2, "Validate `manifest.json`, `workspace.json`, and `refKey` uniqueness", isDistractor = false),
                    MechanismStep(4, 3, "Atomically commit assets & persist Room workspace row", isDistractor = false),
                    MechanismStep(5, 0, "Execute embedded JavaScript inside WebView", isDistractor = true)
                ),
                explanation = "Validation and asset verification run in parallel before atomic commit; Meloberry Android never executes WebView JavaScript."
            )
        )

        val relabelPayloadJson = json.encodeToString(
            RelabelImagePayload(
                imagePath = "assets/meloberry_flow.svg",
                imageFilePath = "assets/meloberry_flow.svg",
                prompt = "Label the three stages of the Meloberry study pipeline:",
                mode = "bank",
                style = "solid",
                boxes = listOf(
                    RelabelBox(1, versionLabel, listOf("mby", "CLI"), 0.044, 0.27, 0.25, 0.46),
                    RelabelBox(2, ".mbypkg", listOf("mbypkg", "Portable ZIP"), 0.394, 0.27, 0.225, 0.46),
                    RelabelBox(3, "Android App", listOf("Native Compose"), 0.719, 0.27, 0.238, 0.46)
                ),
                distractors = listOf("Cloud Server"),
                fuzzy = true,
                caseSensitive = false,
                explanation = "CLI `$versionLabel` compiles and lints content into a portable `.mbypkg` archive that runs offline in Meloberry Android."
            )
        )

        val session = SessionPackageDto(
            id = sampleId,
            activeComponentId = "Component_20261119_120001_001",
            name = "Meloberry Architecture & Pipeline",
            description = "Interactive v2.0 study session showcasing callouts, active-recall tables, zoomable SVG diagrams, and all 8 native question types.",
            components = listOf(
                ComponentPackageDto(
                    id = "Component_20261119_120001_001",
                    index = 1,
                    kind = "Article",
                    title = "Overview & Formulas",
                    refKey = "sample-overview",
                    articleBlocks = listOf(
                        ArticleBlockPackageDto(
                            id = "ArtComp_20261119_120002_001",
                            index = 1,
                            kind = "Text",
                            textContent = "# Welcome to Meloberry Android v${MeloberryVersionInfo.VERSION_NAME}\n\nMeloberry `.mbypkg` v2.0 bundles run **100% natively** in Jetpack Compose — supporting **Markdown + LaTeX + SVG/GIF/Image Articles**, **Callouts**, **Active-Recall Tables**, and all **8 interactive question types**, with full telemetry export back to `mby analyze`.\n\n\$\$v = u + at\$\$\n\n- [x] Offline LaTeX math (like \$E = mc^2\$) rendered natively without WebView\n- [x] In-session full-text search (tap the magnifying glass or press `Ctrl+K`)\n- [ ] Export your own package with `mby pkg export <wsId> android`"
                        ),
                        ArticleBlockPackageDto(
                            id = "ArtComp_20261119_120003_002",
                            index = 2,
                            kind = "Image",
                            imagePath = "assets/meloberry_flow.svg",
                            caption = "End-to-end offline workflow from `$versionLabel` to native Jetpack Compose.",
                            alt = "Meloberry CLI to Android .mbypkg pipeline diagram"
                        ),
                        ArticleBlockPackageDto(
                            id = "ArtComp_20261119_120004_003",
                            index = 3,
                            kind = "Callout",
                            calloutTone = "clinical",
                            calloutTitle = "Architecture Pearl",
                            textContent = "All **3-Tier Layouts** (`global -> component -> question`) and **offline telemetry** (dwell time, first-try accuracy, review flags) match `mby` CLI `v${MeloberryVersionInfo.VERSION_NAME}` semantics."
                        ),
                        ArticleBlockPackageDto(
                            id = "ArtComp_20261119_120004_004",
                            index = 4,
                            kind = "Table",
                            payloadJson = tablePayloadJson
                        )
                    )
                ),
                ComponentPackageDto(
                    id = "Component_20261119_120005_002",
                    index = 2,
                    kind = "Quiz",
                    title = "Architecture Check",
                    refKey = "sample-quiz",
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
                                    position = "TextAboveImage",
                                    isPositionExplicit = true
                                ),
                                back = SidePackageDto(
                                    text = "**`mby pkg export <wsId> android`** runs mandatory `check` lint and exports a verified `.mbypkg` v2.0 archive.",
                                    imagePath = null,
                                    position = "TextBelowImage",
                                    isPositionExplicit = false
                                )
                            ),
                            QuestionPackageDto(
                                id = "Q_20261119_120009_004",
                                index = 4,
                                questionType = "FillBlank",
                                payloadJson = fillBlankPayloadJson
                            ),
                            QuestionPackageDto(
                                id = "Q_20261119_120010_005",
                                index = 5,
                                questionType = "AssertionReason",
                                payloadJson = assertionReasonPayloadJson
                            ),
                            QuestionPackageDto(
                                id = "Q_20261119_120011_006",
                                index = 6,
                                questionType = "SpotError",
                                payloadJson = spotErrorPayloadJson
                            ),
                            QuestionPackageDto(
                                id = "Q_20261119_120012_007",
                                index = 7,
                                questionType = "Differentiation",
                                payloadJson = differentiationPayloadJson
                            ),
                            QuestionPackageDto(
                                id = "Q_20261119_120013_008",
                                index = 8,
                                questionType = "Mechanism",
                                payloadJson = mechanismPayloadJson
                            ),
                            QuestionPackageDto(
                                id = "Q_20261119_120014_009",
                                index = 9,
                                questionType = "RelabelImage",
                                payloadJson = relabelPayloadJson
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
              <text x="108" y="96" font-family="sans-serif" font-size="16" font-weight="bold" fill="#1F1722" text-anchor="middle">$versionLabel</text>
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
