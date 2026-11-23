package com.meloberry.android.data.workspace.source

import com.meloberry.android.core.database.entity.WorkspaceEntity
import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.ArticleBlockModel
import com.meloberry.android.core.model.ComponentKind
import com.meloberry.android.core.model.ComponentModel
import com.meloberry.android.core.model.FlashcardQuestionModel
import com.meloberry.android.core.model.FlashcardSideModel
import com.meloberry.android.core.model.IoDispatcher
import com.meloberry.android.core.model.LayoutTargetType
import com.meloberry.android.core.model.McqOptionModel
import com.meloberry.android.core.model.McqQuestionModel
import com.meloberry.android.core.model.MediaPosition
import com.meloberry.android.core.model.QuestionModel
import com.meloberry.android.core.model.StudyWorkspace
import com.meloberry.android.core.model.WorkspaceKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

internal data class ExtractedWorkspaceBundle(
    val entity: WorkspaceEntity,
    val workspace: StudyWorkspace
)

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
    }

    suspend fun extractAndParse(
        inputStream: InputStream,
        resolveTargetDir: (workspaceId: String) -> File
    ): ExtractedWorkspaceBundle = withContext(ioDispatcher) {
        val stagingRoot = kotlin.io.path.createTempDirectory("mbypkg_stage_").toFile()
        val stagingAssetsDir = File(stagingRoot, "assets").apply { mkdirs() }
        val stagingAssetsPrefix = stagingAssetsDir.canonicalPath + File.separator

        try {
            var manifestRaw: String? = null
            var workspaceRaw: String? = null
            var layoutsRaw: String? = null
            var totalDecompressedBytes = 0L

            ZipInputStream(inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.replace('\\', '/').trimStart('/')
                        val segments = name.split('/').filter { it.isNotEmpty() }
                        require(segments.isNotEmpty() && segments.none { it == "." || it == ".." }) {
                            "Unsafe zip entry path: ${entry.name}"
                        }
                        when {
                            name.equals("manifest.json", ignoreCase = true) -> {
                                val (text, nextTotal) = readEntryUtf8Capped(zip, "manifest.json", totalDecompressedBytes)
                                totalDecompressedBytes = nextTotal
                                manifestRaw = text
                            }
                            name.equals("workspace.json", ignoreCase = true) -> {
                                val (text, nextTotal) = readEntryUtf8Capped(zip, "workspace.json", totalDecompressedBytes)
                                totalDecompressedBytes = nextTotal
                                workspaceRaw = text
                            }
                            name.equals("layouts.json", ignoreCase = true) -> {
                                val (text, nextTotal) = readEntryUtf8Capped(zip, "layouts.json", totalDecompressedBytes)
                                totalDecompressedBytes = nextTotal
                                layoutsRaw = text
                            }
                            name.startsWith("assets/", ignoreCase = true) -> {
                                val fileName = File(name).name
                                if (fileName.isNotBlank() && fileName != "." && fileName != "..") {
                                    val stagedAsset = File(stagingAssetsDir, fileName)
                                    require(stagedAsset.canonicalPath.startsWith(stagingAssetsPrefix)) {
                                        "Blocked Zip-Slip path traversal for asset: $fileName"
                                    }
                                    FileOutputStream(stagedAsset).buffered().use { out ->
                                        totalDecompressedBytes = copyEntryCapped(zip, out, fileName, totalDecompressedBytes)
                                    }
                                    require(ImageAssetVerifier.isValidSafeImage(stagedAsset)) {
                                        "Archive asset '$name' failed image verification."
                                    }
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
            require(manifest.packageVersion.isBlank() || manifest.packageVersion == "1.0") {
                "Unsupported packageVersion '${manifest.packageVersion}'."
            }
            require(manifest.id.isNotBlank() && manifest.kind.isNotBlank()) {
                "Corrupted manifest.json: id and kind are required."
            }
            require(manifest.kind.equals("session", ignoreCase = true) || manifest.kind.equals("quiz", ignoreCase = true)) {
                "Unsupported workspace kind in manifest.json: ${manifest.kind}"
            }
            require(SAFE_ID_REGEX.matches(manifest.id) && !manifest.id.contains("..")) {
                "Unsafe workspace ID in manifest.json: ${manifest.id}"
            }

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

            commitStagedAssets(stagingAssetsDir, targetDir)

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

            ExtractedWorkspaceBundle(entity = entity, workspace = parsedWorkspace)
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun commitStagedAssets(stagingAssetsDir: File, targetDir: File) {
        val parentDir = targetDir.parentFile?.apply { mkdirs() }
        val suffix = UUID.randomUUID().toString().take(8)
        val tempCommitDir = File(parentDir ?: targetDir, "${targetDir.name}.commit_$suffix")
        val backupDir = File(parentDir ?: targetDir, "${targetDir.name}.bak_$suffix")

        try {
            val destAssets = File(tempCommitDir, "assets").apply { mkdirs() }
            val destAssetsPrefix = destAssets.canonicalPath + File.separator
            stagingAssetsDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val outFile = File(destAssets, file.name)
                    require(outFile.canonicalPath.startsWith(destAssetsPrefix)) {
                        "Blocked Zip-Slip path traversal for asset: ${file.name}"
                    }
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
        val rawLayouts = runCatching {
            json.decodeFromString<Map<String, Map<String, String>>>(layoutsJson)
        }.getOrDefault(emptyMap())

        val globalLayouts = buildMap {
            for ((key, value) in rawLayouts) {
                LayoutTargetType.fromRaw(key)?.let { put(it, value) }
            }
        }

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
                components = dto.components.sortedBy { it.index }.map { mapComponent(it, storageDirPath) },
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
                subType = dto.quizType.lowercase(),
                activeComponentId = null,
                components = emptyList(),
                standaloneQuestions = dto.questions.sortedBy { it.index }.map { mapQuestion(it, storageDirPath) },
                globalLayouts = globalLayouts,
                storageDirPath = storageDirPath
            )
        }
    }

    private fun mapComponent(dto: ComponentPackageDto, storageDirPath: String): ComponentModel {
        require(dto.id.isBlank() || (SAFE_ID_REGEX.matches(dto.id) && !dto.id.contains(".."))) {
            "Invalid component ID: '${dto.id}'"
        }
        require(dto.index >= 1) { "Invalid component index ${dto.index} on '${dto.id}'" }
        val kind = if (dto.kind.equals("Article", ignoreCase = true)) ComponentKind.ARTICLE else ComponentKind.QUIZ
        val blocks = dto.articleBlocks.sortedBy { it.index }.map { b ->
            require(b.id.isBlank() || (SAFE_ID_REGEX.matches(b.id) && !b.id.contains(".."))) {
                "Invalid article block ID: '${b.id}'"
            }
            require(b.index >= 1) { "Invalid article block index ${b.index} on '${b.id}'" }
            ArticleBlockModel(
                id = b.id,
                index = b.index,
                kind = if (b.kind.equals("Image", ignoreCase = true)) ArticleBlockKind.IMAGE else ArticleBlockKind.TEXT,
                textContent = b.textContent,
                imageFilePath = resolveAssetPath(storageDirPath, b.imagePath)
            )
        }
        dto.quiz?.let { qz ->
            require(qz.id.isBlank() || (SAFE_ID_REGEX.matches(qz.id) && !qz.id.contains(".."))) {
                "Invalid nested quiz ID: '${qz.id}'"
            }
        }
        val questions = dto.quiz?.questions?.sortedBy { it.index }?.map { mapQuestion(it, storageDirPath) }.orEmpty()
        return ComponentModel(
            id = dto.id,
            index = dto.index,
            kind = kind,
            quizType = dto.quiz?.quizType?.lowercase(),
            questions = questions,
            articleBlocks = blocks,
            layoutOverrides = dto.layoutOverrides.orEmpty(),
            title = dto.title?.takeIf { it.isNotBlank() }
        )
    }

    private fun mapQuestion(dto: QuestionPackageDto, storageDirPath: String): QuestionModel {
        require(dto.id.isBlank() || (SAFE_ID_REGEX.matches(dto.id) && !dto.id.contains(".."))) {
            "Invalid question ID: '${dto.id}'"
        }
        require(dto.index >= 1) { "Invalid question index ${dto.index} on '${dto.id}'" }
        val overrides = dto.layoutOverrides.orEmpty()
        return if (dto.questionType.equals("Mcq", ignoreCase = true)) {
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
                explanation = dto.explanation?.takeIf { it.isNotBlank() }
            )
        } else {
            FlashcardQuestionModel(
                id = dto.id,
                index = dto.index,
                front = mapSide(dto.front, storageDirPath),
                back = mapSide(dto.back, storageDirPath),
                layoutOverrides = overrides
            )
        }
    }

    private fun mapSide(dto: SidePackageDto?, storageDirPath: String): FlashcardSideModel =
        if (dto == null) {
            FlashcardSideModel(text = null, imageFilePath = null, position = MediaPosition.BELOW_IMAGE)
        } else {
            FlashcardSideModel(
                text = dto.text,
                imageFilePath = resolveAssetPath(storageDirPath, dto.imagePath),
                position = MediaPosition.fromRaw(dto.position)
            )
        }

    private fun resolveAssetPath(storageDirPath: String, rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return null
        val normalized = rawPath.trim().replace('\\', '/')
        require(normalized.startsWith("assets/", ignoreCase = true)) {
            "Unsafe asset path outside assets/: '$rawPath'"
        }
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        require(segments.size == 2 && segments[1] != "." && segments[1] != "..") {
            "Invalid asset path '$rawPath'"
        }
        val assetsDir = File(storageDirPath, "assets")
        val targetFile = File(assetsDir, segments[1])
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
