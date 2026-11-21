package com.meloberry.android.data.analysis.source

import android.content.Context
import android.net.Uri
import com.meloberry.android.core.model.IoDispatcher
import com.meloberry.android.core.model.SessionAnalysisReport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AnalysisFileDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun encodeReport(report: SessionAnalysisReport): String = withContext(ioDispatcher) {
        json.encodeToString(report)
    }

    suspend fun decodeReport(rawJson: String): SessionAnalysisReport = withContext(ioDispatcher) {
        json.decodeFromString(rawJson)
    }

    suspend fun saveLocalFiles(
        runId: String,
        workspaceId: String,
        reportJson: String,
        markdown: String
    ) = withContext(ioDispatcher) {
        val reportsDir = File(context.filesDir, "reports/$workspaceId").apply { mkdirs() }
        File(reportsDir, "${runId}.analysis.json").writeText(reportJson)
        File(reportsDir, "${runId}.analysis.md").writeText(markdown)
    }

    suspend fun deleteLocalRunFiles(workspaceId: String, runId: String) = withContext(ioDispatcher) {
        val reportsDir = File(context.filesDir, "reports/$workspaceId")
        File(reportsDir, "${runId}.analysis.json").delete()
        File(reportsDir, "${runId}.analysis.md").delete()
        if (reportsDir.exists() && reportsDir.listFiles()?.isEmpty() == true) {
            reportsDir.delete()
        }
    }

    suspend fun writeToUri(uriString: String, content: String) = withContext(ioDispatcher) {
        val uri = Uri.parse(uriString)
        val outStream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: runCatching { context.contentResolver.openOutputStream(uri, "w") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri)
            ?: error("Unable to open destination file.")
        outStream.bufferedWriter().use { writer ->
            writer.write(content)
        }
    }
}
