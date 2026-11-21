package com.meloberry.android.data.workspace.source

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DocumentStreamDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openInputStream(uriString: String): InputStream? {
        val parsed = Uri.parse(uriString)
        return context.contentResolver.openInputStream(parsed)
    }

    fun getWorkspaceDir(workspaceId: String): File {
        val root = File(context.filesDir, "workspaces")
        if (!root.exists()) root.mkdirs()
        return File(root, workspaceId)
    }

    fun deleteWorkspaceReports(workspaceId: String) {
        val reportsDir = File(context.filesDir, "reports/$workspaceId")
        if (reportsDir.exists()) {
            reportsDir.deleteRecursively()
        }
    }
}
