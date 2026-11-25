package com.meloberry.android.data.workspace.source

import com.meloberry.android.core.database.entity.WorkspaceEntity
import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.ArticleBlockModel
import com.meloberry.android.core.model.AssertionReasonPayload
import com.meloberry.android.core.model.AssertionReasonQuestionModel
import com.meloberry.android.core.model.CalloutTone
import com.meloberry.android.core.model.ComponentKind
import com.meloberry.android.core.model.ComponentModel
import com.meloberry.android.core.model.DifferentiationPayload
import com.meloberry.android.core.model.DifferentiationQuestionModel
import com.meloberry.android.core.model.FillBlankPayload
import com.meloberry.android.core.model.FillBlankQuestionModel
import com.meloberry.android.core.model.FlashcardQuestionModel
import com.meloberry.android.core.model.FlashcardSideModel
import com.meloberry.android.core.model.IoDispatcher
import com.meloberry.android.core.model.LayoutTargetType
import com.meloberry.android.core.model.McqOptionModel
import com.meloberry.android.core.model.McqQuestionModel
import com.meloberry.android.core.model.MechanismPayload
import com.meloberry.android.core.model.MechanismQuestionModel
import com.meloberry.android.core.model.MediaPosition
import com.meloberry.android.core.model.MeloberryVersionInfo
import com.meloberry.android.core.model.QuestionModel
import com.meloberry.android.core.model.QuestionType
import com.meloberry.android.core.model.RelabelBox
import com.meloberry.android.core.model.RelabelImagePayload
import com.meloberry.android.core.model.RelabelImageQuestionModel
import com.meloberry.android.core.model.SpotErrorPayload
import com.meloberry.android.core.model.SpotErrorQuestionModel
import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.TableBlockPayload
import com.meloberry.android.core.model.WorkspaceKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

internal data class ExtractedWorkspaceBundle(
    val entity: WorkspaceEntity,
    val workspace: StudyWorkspace
)

internal class StagedWorkspaceBundle(
    val entity: WorkspaceEntity,
    val workspace: StudyWorkspace,
    private val stagingRoot: File,
    private val stagingAssetsDir: File,
    private val targetDir: File,
    private val onCommit: (stagingAssetsDir: File, targetDir: File) -> Unit
) : Closeable {
    private var committed = false

    fun commitAssets() {
        check(!committed) { "Staged workspace assets have already been committed." }
        onCommit(stagingAssetsDir, targetDir)
        committed = true
    }

    override fun close() {
        stagingRoot.deleteRecursively()
    }
}

@Singleton
internal class MbypkgArchiveDataSource @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private companion object {
        const val MAX_ENTRY_BYTES = 50L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 250L * 1024L * 1024L
        val SAFE_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        val DRIVE_PREFIX_REGEX = Regex("^[A-Za-z]:")
        val REF_KEY_REGEX = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")
        val RESERVED_REF_REGEX = Regex("^[cq][0-9]+$")
        val VALID_ALIGNMENTS = setOf("left", "center", "right")
        val VALID_AR_ANSWERS = setOf(
            "both-explain",
            "both-no-explain",
            "a-true-r-false",
            "a-false-r-true",
            "both-false"
        )
        val VALID_AR_SCHEMES = setOf("4opt", "5opt")
        val VALID_DIFF_MODES = setOf("fill", "spot-wrong", "rearrange")
        val VALID_RELABEL_MODES = setOf("reveal", "bank", "recall")
        val VALID_RELABEL_STYLES = setOf("solid", "blur", "pixelate", "whiteout")
    }

    suspend fun extractAndParse(
        inputStream: InputStream,
        resolveTargetDir: (workspaceId: String) -> File
    ): ExtractedWorkspaceBundle = stageAndValidate(inputStream, resolveTargetDir).use { staged ->
        staged.commitAssets()
        ExtractedWorkspaceBundle(entity = staged.entity, workspace = staged.workspace)
    }

    suspend fun stageAndValidate(
        inputStream: InputStream,
        resolveTargetDir: (workspaceId: String) -> File
    ): StagedWorkspaceBundle = withContext(ioDispatcher) {
        val stagingRoot = kotlin.io.path.createTempDirectory("mbypkg_stage_").toFile()
        val stagingAssetsDir = File(stagingRoot, "assets").apply { mkdirs() }
        val stagingAssetsPrefix = stagingAssetsDir.canonicalPath + File.separator

        try {
            var manifestRaw: String? = null
            var workspaceRaw: String? = null
            var layoutsRaw: String? = null
            var totalDecompressedBytes = 0L
            val seenAssetPaths = mutableSetOf<String>()

            ZipInputStream(inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val rawNormalized = entry.name.replace('\\', '/')
                        require(!rawNormalized.startsWith('/') && !DRIVE_PREFIX_REGEX.containsMatchIn(rawNormalized)) {
                            "Unsafe rooted zip entry path: ${entry.name}"
                        }
                        val segments = rawNormalized.split('/').filter { it.isNotEmpty() }
                        require(segments.isNotEmpty() && segments.none { it == "." || it == ".." }) {
                            "Unsafe zip entry path: ${entry.name}"
                        }
                        val normalizedName = segments.joinToString("/")
                        when {
                            normalizedName.equals("manifest.json", ignoreCase = true) -> {
                                val (text, nextTotal) = readEntryUtf8Capped(zip, "manifest.json", totalDecompressedBytes)
                                totalDecompressedBytes = nextTotal
                                manifestRaw = text
                            }
                            normalizedName.equals("workspace.json", ignoreCase = true) -> {
                                val (text, nextTotal) = readEntryUtf8Capped(zip, "workspace.json", totalDecompressedBytes)
                                totalDecompressedBytes = nextTotal
                                workspaceRaw = text
                            }
                            normalizedName.equals("layouts.json", ignoreCase = true) -> {
                                val (text, nextTotal) = readEntryUtf8Capped(zip, "layouts.json", totalDecompressedBytes)
                                totalDecompressedBytes = nextTotal
                                layoutsRaw = text
                            }
                            segments.first().equals("assets", ignoreCase = true) -> {
                                val relSegments = segments.drop(1)
                                require(relSegments.isNotEmpty() && relSegments.none { it.isBlank() || it == "." || it == ".." }) {
                                    "Invalid asset entry path: ${entry.name}"
                                }
                                val canonicalRelKey = relSegments.joinToString("/").lowercase(Locale.ROOT)
                                require(seenAssetPaths.add(canonicalRelKey)) {
                                    "Duplicate or colliding asset path in archive: ${entry.name}"
                                }
                                val stagedAsset = File(stagingAssetsDir, relSegments.joinToString(File.separator))
                                require(stagedAsset.canonicalPath.startsWith(stagingAssetsPrefix)) {
                                    "Blocked Zip-Slip path traversal for asset: ${entry.name}"
                                }
                                stagedAsset.parentFile?.mkdirs()
                                FileOutputStream(stagedAsset).buffered().use { out ->
                                    totalDecompressedBytes = copyEntryCapped(zip, out, normalizedName, totalDecompressedBytes)
                                }
                                require(ImageAssetVerifier.isValidSafeImage(stagedAsset)) {
                                    "Archive asset '$normalizedName' failed image verification."
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val manifestJson = requireNotNull(manifestRaw) { "Invalid .mbypkg archive: missing manifest.json" }
            val workspaceJson = requireNotNull(workspaceRaw) { "Invalid .mbypkg archive: missing workspace.json" }

            val manifest = json.decodeFromString<PackageManifestDto>(manifestJson)
            require(
                manifest.packageVersion.isBlank() ||
                    manifest.packageVersion.trim() in MeloberryVersionInfo.SUPPORTED_PACKAGE_VERSIONS
            ) {
                "Unsupported packageVersion '${manifest.packageVersion}'. Supported: ${MeloberryVersionInfo.SUPPORTED_PACKAGE_VERSIONS.joinToString(", ")}."
            }
            require(manifest.id.isNotBlank() && manifest.kind.isNotBlank()) {
                "Corrupted manifest.json: id and kind are required."
            }
            require(manifest.kind.equals("session", ignoreCase = true) || manifest.kind.equals("quiz", ignoreCase = true)) {
                "Unsupported workspace kind in manifest.json: ${manifest.kind}"
            }
            requireSafeId(manifest.id, "workspace")

            val targetDir = resolveTargetDir(manifest.id)
            val anchorParentCanonical = resolveTargetDir("anchor").canonicalFile.parentFile?.canonicalPath
            require(targetDir.canonicalFile.parentFile?.canonicalPath == anchorParentCanonical) {
                "Workspace ID '${manifest.id}' escapes workspaces directory."
            }

            val normalizedLayoutsJson = layoutsRaw ?: "{}"
            val parsedWorkspace = parseWorkspace(
                id = manifest.id,
                kindText = manifest.kind,
                workspaceJson = workspaceJson,
                layoutsJson = normalizedLayoutsJson,
                storageDirPath = targetDir.absolutePath
            )

            val itemCount = when (parsedWorkspace.kind) {
                WorkspaceKind.SESSION -> parsedWorkspace.components.size
                WorkspaceKind.QUIZ -> parsedWorkspace.standaloneQuestions.size
            }

            val entity = WorkspaceEntity(
                id = parsedWorkspace.id,
                title = parsedWorkspace.title,
                description = parsedWorkspace.description,
                kind = if (parsedWorkspace.kind == WorkspaceKind.SESSION) "session" else "quiz",
                subType = parsedWorkspace.subType,
                itemCount = itemCount,
                workspaceJson = workspaceJson,
                layoutsJson = normalizedLayoutsJson,
                storageDirPath = targetDir.absolutePath,
                importedAtEpochMs = System.currentTimeMillis()
            )

            StagedWorkspaceBundle(
                entity = entity,
                workspace = parsedWorkspace,
                stagingRoot = stagingRoot,
                stagingAssetsDir = stagingAssetsDir,
                targetDir = targetDir,
                onCommit = ::commitStagedAssets
            )
        } catch (t: Throwable) {
            stagingRoot.deleteRecursively()
            throw t
        }
    }

    internal fun commitStagedAssets(stagingAssetsDir: File, targetDir: File) {
        val parentDir = targetDir.parentFile?.apply { mkdirs() }
        val suffix = UUID.randomUUID().toString().take(8)
        val tempCommitDir = File(parentDir ?: targetDir, "${targetDir.name}.commit_$suffix")
        val backupDir = File(parentDir ?: targetDir, "${targetDir.name}.bak_$suffix")

        try {
            val destAssets = File(tempCommitDir, "assets").apply { mkdirs() }
            val destAssetsPrefix = destAssets.canonicalPath + File.separator
            stagingAssetsDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relPath = file.relativeTo(stagingAssetsDir).path
                    val outFile = File(destAssets, relPath)
                    require(outFile.canonicalPath.startsWith(destAssetsPrefix)) {
                        "Blocked Zip-Slip path traversal for asset: $relPath"
                    }
                    outFile.parentFile?.mkdirs()
                    if (!file.renameTo(outFile)) {
                        file.copyTo(outFile, overwrite = true)
                    }
                }
            }

            if (targetDir.exists() && !targetDir.renameTo(backupDir)) {
                targetDir.deleteRecursively()
            }
            if (!tempCommitDir.renameTo(targetDir)) {
                tempCommitDir.copyRecursively(targetDir, overwrite = true)
                tempCommitDir.deleteRecursively()
            }
            if (backupDir.exists()) {
                backupDir.deleteRecursively()
            }
        } catch (t: Throwable) {
            tempCommitDir.deleteRecursively()
            if (backupDir.exists() && !targetDir.exists()) {
                backupDir.renameTo(targetDir)
            }
            throw t
        }
    }

    fun deserializeEntity(entity: WorkspaceEntity): StudyWorkspace =
        parseWorkspace(
            id = entity.id,
            kindText = entity.kind,
            workspaceJson = entity.workspaceJson,
            layoutsJson = entity.layoutsJson,
            storageDirPath = entity.storageDirPath
        )

    private fun parseWorkspace(
        id: String,
        kindText: String,
        workspaceJson: String,
        layoutsJson: String,
        storageDirPath: String
    ): StudyWorkspace {
        val rawLayouts: Map<String, Map<String, String>> = if (layoutsJson.isBlank()) {
            emptyMap()
        } else {
            try {
                json.decodeFromString(layoutsJson)
            } catch (e: Exception) {
                throw IllegalArgumentException("Malformed layouts.json: ${e.message}", e)
            }
        }

        val globalLayouts = buildMap {
            for ((key, value) in rawLayouts) {
                LayoutTargetType.fromRaw(key)?.let { put(it, value) }
            }
        }

        val usedRefs = mutableSetOf<String>()

        return if (kindText.equals("session", ignoreCase = true)) {
            val dto = json.decodeFromString<SessionPackageDto>(workspaceJson)
            require(dto.id.isBlank() || dto.id.equals(id, ignoreCase = true)) {
                "Mismatched workspace ID in workspace.json: '${dto.id}' != '$id'"
            }
            StudyWorkspace(
                id = id,
                title = dto.name?.takeIf { it.isNotBlank() } ?: id,
                description = dto.description.orEmpty(),
                kind = WorkspaceKind.SESSION,
                subType = "session",
                activeComponentId = dto.activeComponentId,
                components = dto.components.sortedBy { it.index }.map { mapComponent(it, storageDirPath, usedRefs) },
                standaloneQuestions = emptyList(),
                globalLayouts = globalLayouts,
                storageDirPath = storageDirPath
            )
        } else {
            val dto = json.decodeFromString<QuizPackageDto>(workspaceJson)
            require(dto.id.isBlank() || dto.id.equals(id, ignoreCase = true)) {
                "Mismatched workspace ID in workspace.json: '${dto.id}' != '$id'"
            }
            val resolvedTitle = dto.name?.takeIf { it.isNotBlank() } ?: id
            val resolvedDesc = dto.description?.takeIf { it.isNotBlank() }
                ?: "Standalone quiz (${dto.questions.size} questions)"
            StudyWorkspace(
                id = id,
                title = resolvedTitle,
                description = resolvedDesc,
                kind = WorkspaceKind.QUIZ,
                subType = dto.quizType.lowercase(Locale.ROOT),
                activeComponentId = null,
                components = emptyList(),
                standaloneQuestions = dto.questions.sortedBy { it.index }.map { mapQuestion(it, storageDirPath, usedRefs) },
                globalLayouts = globalLayouts,
                storageDirPath = storageDirPath
            )
        }
    }

    private fun mapComponent(
        dto: ComponentPackageDto,
        storageDirPath: String,
        usedRefs: MutableSet<String>
    ): ComponentModel {
        requireSafeId(dto.id, "component")
        require(dto.index >= 1) { "Invalid component index ${dto.index} on '${dto.id}'" }
        val kind = when {
            dto.kind.equals("Article", ignoreCase = true) -> ComponentKind.ARTICLE
            dto.kind.equals("Quiz", ignoreCase = true) -> ComponentKind.QUIZ
            else -> throw IllegalArgumentException("Unsupported component kind '${dto.kind}' on '${dto.id}'.")
        }
        val compRefKey = validateAndRegisterRef(dto.refKey, usedRefs, "component '${dto.id}'")

        val blocks = dto.articleBlocks.sortedBy { it.index }.map { b ->
            mapArticleBlock(b, storageDirPath, usedRefs)
        }
        val nestedQuiz = dto.quiz
        if (kind == ComponentKind.QUIZ) {
            val qz = requireNotNull(nestedQuiz) {
                "Quiz component '${dto.id}' is missing nested quiz payload."
            }
            requireSafeId(qz.id, "nested quiz")
            require(qz.id.equals(dto.id, ignoreCase = true)) {
                "Nested quiz ID '${qz.id}' does not match component ID '${dto.id}'."
            }
        } else if (nestedQuiz != null) {
            requireSafeId(nestedQuiz.id, "nested quiz")
        }
        val questions = nestedQuiz?.questions
            ?.sortedBy { it.index }
            ?.map { mapQuestion(it, storageDirPath, usedRefs) }
            .orEmpty()

        return ComponentModel(
            id = dto.id,
            index = dto.index,
            kind = kind,
            quizType = nestedQuiz?.quizType?.lowercase(Locale.ROOT),
            questions = questions,
            articleBlocks = blocks,
            layoutOverrides = dto.layoutOverrides.orEmpty(),
            title = dto.title?.takeIf { it.isNotBlank() },
            refKey = compRefKey
        )
    }

    private fun mapArticleBlock(
        b: ArticleBlockPackageDto,
        storageDirPath: String,
        usedRefs: MutableSet<String>
    ): ArticleBlockModel {
        requireSafeId(b.id, "article block")
        require(b.index >= 1) { "Invalid article block index ${b.index} on '${b.id}'" }
        val blockKind = when {
            b.kind.equals("Text", ignoreCase = true) -> ArticleBlockKind.TEXT
            b.kind.equals("Image", ignoreCase = true) -> ArticleBlockKind.IMAGE
            b.kind.equals("Callout", ignoreCase = true) -> ArticleBlockKind.CALLOUT
            b.kind.equals("Table", ignoreCase = true) -> ArticleBlockKind.TABLE
            else -> throw IllegalArgumentException("Unsupported article block kind '${b.kind}' on '${b.id}'.")
        }
        val refKey = validateAndRegisterRef(b.refKey, usedRefs, "article block '${b.id}'")

        val calloutTone: CalloutTone? = when {
            !b.calloutTone.isNullOrBlank() -> CalloutTone.fromRaw(b.calloutTone)
                ?: throw IllegalArgumentException("Invalid calloutTone '${b.calloutTone}' on article block '${b.id}'.")
            blockKind == ArticleBlockKind.CALLOUT -> CalloutTone.PEARL
            else -> null
        }

        if (blockKind == ArticleBlockKind.CALLOUT) {
            require(!b.textContent.isNullOrBlank()) {
                "Callout article block '${b.id}' must have non-empty textContent."
            }
        }

        val tablePayload: TableBlockPayload? = if (blockKind == ArticleBlockKind.TABLE) {
            parseAndValidateTablePayload(b.payloadJson, b.id)
        } else {
            null
        }

        return ArticleBlockModel(
            id = b.id,
            index = b.index,
            kind = blockKind,
            textContent = b.textContent,
            imageFilePath = resolveAssetPath(storageDirPath, b.imagePath),
            caption = b.caption?.trim()?.takeIf { it.isNotEmpty() },
            alt = b.alt?.trim()?.takeIf { it.isNotEmpty() },
            calloutTone = calloutTone,
            calloutTitle = b.calloutTitle?.trim()?.takeIf { it.isNotEmpty() },
            refKey = refKey,
            tablePayload = tablePayload
        )
    }

    private fun parseAndValidateTablePayload(rawJson: String?, blockId: String): TableBlockPayload {
        require(!rawJson.isNullOrBlank()) {
            "Missing payloadJson for table block '$blockId'."
        }
        val decoded = try {
            json.decodeFromString<TableBlockPayload>(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid payloadJson for table block '$blockId': ${e.message}", e)
        }
        require(decoded.headers.isNotEmpty()) {
            "Table block '$blockId' must define at least one header column."
        }
        require(decoded.rows.isNotEmpty()) {
            "Table block '$blockId' must define at least one data row."
        }
        val colCount = decoded.headers.size
        decoded.rows.forEachIndexed { idx, row ->
            require(row.size == colCount) {
                "Table block '$blockId' row ${idx + 1} has ${row.size} column(s), expected $colCount."
            }
        }
        decoded.hideableCols.forEach { colIdx ->
            require(colIdx in 1..colCount) {
                "Table block '$blockId' hideableCols index $colIdx is out of range 1..$colCount."
            }
        }
        val normalizedAlignments = if (decoded.alignments.isEmpty()) {
            List(colCount) { "left" }
        } else {
            require(decoded.alignments.size == colCount) {
                "Table block '$blockId' alignments count (${decoded.alignments.size}) does not match headers count ($colCount)."
            }
            decoded.alignments.map { rawAlign ->
                val norm = rawAlign.trim().lowercase(Locale.ROOT)
                require(norm in VALID_ALIGNMENTS) {
                    "Table block '$blockId' has invalid column alignment '$rawAlign'."
                }
                norm
            }
        }
        return decoded.copy(alignments = normalizedAlignments)
    }

    private fun mapQuestion(
        dto: QuestionPackageDto,
        storageDirPath: String,
        usedRefs: MutableSet<String>
    ): QuestionModel {
        requireSafeId(dto.id, "question")
        require(dto.index >= 1) { "Invalid question index ${dto.index} on '${dto.id}'" }
        val qRefKey = validateAndRegisterRef(dto.refKey, usedRefs, "question '${dto.id}'")
        val overrides = dto.layoutOverrides.orEmpty()
        val questionType = QuestionType.fromRaw(dto.questionType)
            ?: throw IllegalArgumentException("Unsupported question type '${dto.questionType}' on '${dto.id}'.")

        return when (questionType) {
            QuestionType.MCQ -> {
                val boundedMaxOptions = if (dto.maxOptions in 2..26) dto.maxOptions else 4
                McqQuestionModel(
                    id = dto.id,
                    index = dto.index,
                    promptText = dto.promptText.orEmpty(),
                    maxOptions = boundedMaxOptions,
                    isMultiCorrect = dto.isMultiCorrect,
                    options = dto.options.sortedBy { it.index }.map { opt ->
                        require(opt.index >= 1) { "Invalid MCQ option index ${opt.index} on '${dto.id}'" }
                        McqOptionModel(
                            index = opt.index,
                            text = opt.text,
                            imageFilePath = resolveAssetPath(storageDirPath, opt.imagePath),
                            isCorrect = opt.isCorrect
                        )
                    },
                    layoutOverrides = overrides,
                    promptImageFilePath = resolveAssetPath(storageDirPath, dto.promptImagePath),
                    explanation = dto.explanation?.takeIf { it.isNotBlank() },
                    refKey = qRefKey
                )
            }
            QuestionType.FLASHCARD -> {
                FlashcardQuestionModel(
                    id = dto.id,
                    index = dto.index,
                    front = mapSide(dto.front, storageDirPath),
                    back = mapSide(dto.back, storageDirPath),
                    layoutOverrides = overrides,
                    refKey = qRefKey
                )
            }
            QuestionType.FILL_BLANK -> {
                val payload = parseAndValidateFillBlankPayload(dto.payloadJson, dto.id)
                FillBlankQuestionModel(
                    id = dto.id,
                    index = dto.index,
                    payload = payload,
                    layoutOverrides = overrides,
                    refKey = qRefKey
                )
            }
            QuestionType.ASSERTION_REASON -> {
                val payload = parseAndValidateAssertionReasonPayload(dto.payloadJson, dto.id)
                AssertionReasonQuestionModel(
                    id = dto.id,
                    index = dto.index,
                    payload = payload,
                    layoutOverrides = overrides,
                    refKey = qRefKey
                )
            }
            QuestionType.SPOT_ERROR -> {
                val payload = parseAndValidateSpotErrorPayload(dto.payloadJson, dto.id, storageDirPath)
                SpotErrorQuestionModel(
                    id = dto.id,
                    index = dto.index,
                    payload = payload,
                    layoutOverrides = overrides,
                    refKey = qRefKey
                )
            }
            QuestionType.DIFFERENTIATION -> {
                val payload = parseAndValidateDifferentiationPayload(dto.payloadJson, dto.id)
                DifferentiationQuestionModel(
                    id = dto.id,
                    index = dto.index,
                    payload = payload,
                    layoutOverrides = overrides,
                    refKey = qRefKey
                )
            }
            QuestionType.MECHANISM -> {
                val payload = parseAndValidateMechanismPayload(dto.payloadJson, dto.id)
                MechanismQuestionModel(
                    id = dto.id,
                    index = dto.index,
                    payload = payload,
                    layoutOverrides = overrides,
                    refKey = qRefKey
                )
            }
            QuestionType.RELABEL_IMAGE -> {
                val payload = parseAndValidateRelabelImagePayload(dto.payloadJson, dto.id, storageDirPath)
                RelabelImageQuestionModel(
                    id = dto.id,
                    index = dto.index,
                    payload = payload,
                    layoutOverrides = overrides,
                    refKey = qRefKey
                )
            }
        }
    }

    private fun parseAndValidateFillBlankPayload(rawJson: String?, questionId: String): FillBlankPayload {
        require(!rawJson.isNullOrBlank()) { "Missing payloadJson for FillBlank question '$questionId'." }
        val payload = try {
            json.decodeFromString<FillBlankPayload>(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid payloadJson for FillBlank question '$questionId': ${e.message}", e)
        }
        require(payload.template.isNotBlank()) { "FillBlank question '$questionId' template cannot be blank." }
        require(payload.blanks.isNotEmpty()) { "FillBlank question '$questionId' must contain at least one blank." }
        require(payload.blanks.all { slot -> slot.index >= 1 }) {
            "FillBlank question '$questionId' has a blank with invalid index."
        }
        require(payload.blanks.map { it.index }.distinct().size == payload.blanks.size) {
            "FillBlank question '$questionId' contains duplicate blank indices."
        }
        require(payload.blanks.all { slot -> slot.answers.isNotEmpty() && slot.answers.all { it.isNotBlank() } }) {
            "FillBlank question '$questionId' has a blank with empty answers."
        }
        return payload
    }

    private fun parseAndValidateAssertionReasonPayload(rawJson: String?, questionId: String): AssertionReasonPayload {
        require(!rawJson.isNullOrBlank()) { "Missing payloadJson for AssertionReason question '$questionId'." }
        val payload = try {
            json.decodeFromString<AssertionReasonPayload>(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid payloadJson for AssertionReason question '$questionId': ${e.message}", e)
        }
        require(payload.assertion.isNotBlank() && payload.reason.isNotBlank()) {
            "AssertionReason question '$questionId' requires non-empty assertion and reason."
        }
        val normAnswer = payload.answer.trim().lowercase(Locale.ROOT)
        require(normAnswer in VALID_AR_ANSWERS) {
            "AssertionReason question '$questionId' has invalid answer '${payload.answer}'."
        }
        val normScheme = if (payload.scheme.isBlank()) {
            if (normAnswer == "both-false") "5opt" else "4opt"
        } else {
            payload.scheme.trim().lowercase(Locale.ROOT)
        }
        require(normScheme in VALID_AR_SCHEMES) {
            "AssertionReason question '$questionId' has invalid scheme '${payload.scheme}'."
        }
        require(!(normAnswer == "both-false" && normScheme == "4opt")) {
            "AssertionReason question '$questionId' cannot use scheme '4opt' with answer 'both-false'; use '5opt'."
        }
        return payload.copy(answer = normAnswer, scheme = normScheme)
    }

    private fun parseAndValidateSpotErrorPayload(
        rawJson: String?,
        questionId: String,
        storageDirPath: String
    ): SpotErrorPayload {
        require(!rawJson.isNullOrBlank()) { "Missing payloadJson for SpotError question '$questionId'." }
        val payload = try {
            json.decodeFromString<SpotErrorPayload>(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid payloadJson for SpotError question '$questionId': ${e.message}", e)
        }
        require(payload.template.isNotBlank()) { "SpotError question '$questionId' template cannot be blank." }
        require(payload.segments.isNotEmpty()) { "SpotError question '$questionId' must contain at least one segment." }
        val resolvedImage = resolveAssetPath(storageDirPath, payload.imagePath)
        return payload.copy(imagePath = resolvedImage, imageFilePath = resolvedImage)
    }

    private fun parseAndValidateDifferentiationPayload(rawJson: String?, questionId: String): DifferentiationPayload {
        require(!rawJson.isNullOrBlank()) { "Missing payloadJson for Differentiation question '$questionId'." }
        val payload = try {
            json.decodeFromString<DifferentiationPayload>(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid payloadJson for Differentiation question '$questionId': ${e.message}", e)
        }
        val normMode = payload.mode.trim().lowercase(Locale.ROOT)
        require(normMode in VALID_DIFF_MODES) {
            "Differentiation question '$questionId' has invalid mode '${payload.mode}'."
        }
        require(payload.headers.size >= 2) {
            "Differentiation question '$questionId' requires at least 2 headers."
        }
        if (normMode == "rearrange") {
            require(payload.headers.size >= 3) {
                "Differentiation rearrange question '$questionId' requires at least 3 headers."
            }
        }
        require(payload.rows.isNotEmpty()) {
            "Differentiation question '$questionId' requires at least 1 row."
        }
        val colCount = payload.headers.size
        payload.rows.forEachIndexed { rIdx, row ->
            require(row.size == colCount) {
                "Differentiation question '$questionId' row ${rIdx + 1} has ${row.size} column(s), expected $colCount."
            }
        }
        payload.specialCells.forEach { cell ->
            require(cell.rowIndex in payload.rows.indices && cell.colIndex in 0 until colCount) {
                "Differentiation question '$questionId' specialCell (${cell.rowIndex}, ${cell.colIndex}) is out of table bounds."
            }
        }
        if (normMode == "fill") {
            require(payload.specialCells.any { it.isBlank && !it.blankAnswers.isNullOrEmpty() }) {
                "Differentiation fill question '$questionId' must contain at least one blank cell."
            }
        }
        val normalizedAlignments = if (payload.alignments.isEmpty()) {
            List(colCount) { "left" }
        } else {
            require(payload.alignments.size == colCount) {
                "Differentiation question '$questionId' alignments count mismatch."
            }
            payload.alignments.map { rawAlign ->
                val norm = rawAlign.trim().lowercase(Locale.ROOT)
                require(norm in VALID_ALIGNMENTS) {
                    "Differentiation question '$questionId' has invalid alignment '$rawAlign'."
                }
                norm
            }
        }
        return payload.copy(mode = normMode, alignments = normalizedAlignments)
    }

    private fun parseAndValidateMechanismPayload(rawJson: String?, questionId: String): MechanismPayload {
        require(!rawJson.isNullOrBlank()) { "Missing payloadJson for Mechanism question '$questionId'." }
        val payload = try {
            json.decodeFromString<MechanismPayload>(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid payloadJson for Mechanism question '$questionId': ${e.message}", e)
        }
        require(payload.steps.size >= 2) {
            "Mechanism question '$questionId' requires at least 2 steps."
        }
        require(payload.steps.count { !it.isDistractor } >= 2) {
            "Mechanism question '$questionId' requires at least 2 non-distractor ordered steps."
        }
        require(payload.steps.map { it.stepId }.distinct().size == payload.steps.size) {
            "Mechanism question '$questionId' contains duplicate stepId values."
        }
        require(payload.steps.all { it.text.isNotBlank() }) {
            "Mechanism question '$questionId' contains a step with blank text."
        }
        return payload
    }

    private fun parseAndValidateRelabelImagePayload(
        rawJson: String?,
        questionId: String,
        storageDirPath: String
    ): RelabelImagePayload {
        require(!rawJson.isNullOrBlank()) { "Missing payloadJson for RelabelImage question '$questionId'." }
        val payload = try {
            json.decodeFromString<RelabelImagePayload>(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid payloadJson for RelabelImage question '$questionId': ${e.message}", e)
        }
        require(payload.imagePath.isNotBlank()) {
            "RelabelImage question '$questionId' must specify a non-empty imagePath."
        }
        val resolvedImage = requireNotNull(resolveAssetPath(storageDirPath, payload.imagePath)) {
            "RelabelImage question '$questionId' imagePath could not be resolved."
        }
        val normMode = payload.mode.trim().lowercase(Locale.ROOT)
        require(normMode in VALID_RELABEL_MODES) {
            "RelabelImage question '$questionId' has invalid mode '${payload.mode}'."
        }
        val normStyle = payload.style.trim().lowercase(Locale.ROOT)
        require(normStyle in VALID_RELABEL_STYLES) {
            "RelabelImage question '$questionId' has invalid style '${payload.style}'."
        }
        require(payload.boxes.isNotEmpty()) {
            "RelabelImage question '$questionId' must define at least one mask box."
        }
        require(payload.boxes.map { it.index }.distinct().size == payload.boxes.size) {
            "RelabelImage question '$questionId' contains duplicate mask box indices."
        }
        payload.boxes.forEach { box ->
            require(box.label.isNotBlank()) {
                "RelabelImage question '$questionId' box #${box.index} must have a non-blank label."
            }
            require(hasValidRelabelBoxBounds(box)) {
                "RelabelImage question '$questionId' box #${box.index} has out-of-bounds coordinates (x=${box.x}, y=${box.y}, w=${box.w}, h=${box.h})."
            }
        }
        return payload.copy(
            imagePath = resolvedImage,
            imageFilePath = resolvedImage,
            mode = normMode,
            style = normStyle
        )
    }

    private fun hasValidRelabelBoxBounds(box: RelabelBox): Boolean =
        box.x.isFinite() && box.y.isFinite() && box.w.isFinite() && box.h.isFinite() &&
            box.x >= 0.0 && box.y >= 0.0 &&
            box.w > 0.0 && box.h > 0.0 &&
            box.x + box.w <= 1.001 && box.y + box.h <= 1.001

    private fun validateAndRegisterRef(
        rawRef: String?,
        usedRefs: MutableSet<String>,
        ownerLabel: String
    ): String? {
        if (rawRef.isNullOrBlank()) return null
        val normalized = rawRef.trim().lowercase(Locale.ROOT)
        require(REF_KEY_REGEX.matches(normalized)) {
            "Invalid refKey '$rawRef' on $ownerLabel. Must match ${REF_KEY_REGEX.pattern}."
        }
        require(!RESERVED_REF_REGEX.matches(normalized)) {
            "Reserved positional refKey '$rawRef' is not allowed on $ownerLabel."
        }
        require(usedRefs.add(normalized)) {
            "Duplicate refKey '$normalized' detected in workspace (conflict on $ownerLabel)."
        }
        return normalized
    }

    private fun mapSide(dto: SidePackageDto?, storageDirPath: String): FlashcardSideModel =
        if (dto == null) {
            FlashcardSideModel(
                text = null,
                imageFilePath = null,
                position = MediaPosition.BELOW_IMAGE,
                isPositionExplicit = false
            )
        } else {
            FlashcardSideModel(
                text = dto.text,
                imageFilePath = resolveAssetPath(storageDirPath, dto.imagePath),
                position = MediaPosition.fromRaw(dto.position),
                isPositionExplicit = dto.isPositionExplicit
            )
        }

    private fun requireSafeId(id: String, entityLabel: String) {
        require(id.isNotBlank() && SAFE_ID_REGEX.matches(id) && !id.contains("..")) {
            "Invalid $entityLabel ID: '$id'"
        }
    }

    private fun resolveAssetPath(storageDirPath: String, rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return null
        val normalized = rawPath.trim().replace('\\', '/')
        require(!normalized.startsWith('/') && !DRIVE_PREFIX_REGEX.containsMatchIn(normalized)) {
            "Unsafe rooted asset path: '$rawPath'"
        }
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        require(
            segments.size >= 2 &&
                segments.first().equals("assets", ignoreCase = true) &&
                segments.drop(1).none { it == "." || it == ".." }
        ) {
            "Unsafe asset path outside assets/: '$rawPath'"
        }
        val assetsDir = File(storageDirPath, "assets")
        val targetFile = File(assetsDir, segments.drop(1).joinToString(File.separator))
        val assetsPrefix = assetsDir.canonicalPath + File.separator
        require(targetFile.canonicalPath.startsWith(assetsPrefix)) {
            "Asset path escapes workspace assets directory: '$rawPath'"
        }
        return targetFile.absolutePath
    }

    private fun readEntryUtf8Capped(
        zip: ZipInputStream,
        entryLabel: String,
        runningTotal: Long
    ): Pair<String, Long> {
        val buffer = ByteArrayOutputStream()
        val nextTotal = copyEntryCapped(zip, buffer, entryLabel, runningTotal)
        return buffer.toByteArray().decodeToString().removePrefix("\uFEFF") to nextTotal
    }

    private fun copyEntryCapped(
        zip: ZipInputStream,
        destination: OutputStream,
        entryLabel: String,
        runningTotal: Long
    ): Long {
        var entryBytes = 0L
        var newTotal = runningTotal
        val chunk = ByteArray(8192)
        while (true) {
            val read = zip.read(chunk)
            if (read <= 0) break
            entryBytes += read
            newTotal += read
            require(entryBytes <= MAX_ENTRY_BYTES) {
                "Archive entry '$entryLabel' exceeds maximum size (${MAX_ENTRY_BYTES / (1024 * 1024)} MB)."
            }
            require(newTotal <= MAX_TOTAL_BYTES) {
                "Archive decompressed payload exceeds maximum size (${MAX_TOTAL_BYTES / (1024 * 1024)} MB)."
            }
            destination.write(chunk, 0, read)
        }
        return newTotal
    }
}
