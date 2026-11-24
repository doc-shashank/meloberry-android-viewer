package com.meloberry.android.core.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.PendingExportPayload
import com.meloberry.android.core.model.UserMessage
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import java.io.File
import java.util.UUID

private const val INLINE_SAVEABLE_EXPORT_LIMIT = 200_000

private class CreateDocumentWithMimeContract : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent {
        val (suggestedFileName, mimeType) = input
        val effectiveMime = mimeType.ifBlank { "text/plain" }
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = effectiveMime
            putExtra(Intent.EXTRA_TITLE, suggestedFileName)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
fun MeloberrySnackbarEffect(
    messages: List<UserMessage>,
    snackbarHostState: SnackbarHostState,
    onMessageShown: (Long) -> Unit
) {
    val currentMessage = messages.firstOrNull()
    LaunchedEffect(currentMessage?.id) {
        if (currentMessage != null) {
            snackbarHostState.showSnackbar(currentMessage.text)
            onMessageShown(currentMessage.id)
        }
    }
}

@Composable
fun PendingDocumentExportEffect(
    pendingExport: PendingExportPayload?,
    onExportConsumed: () -> Unit,
    onSaveExportToUri: (uriString: String, content: String) -> Unit
) {
    val context = LocalContext.current
    var pendingInlineContent by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCacheFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingDocumentResult by rememberSaveable { mutableStateOf(false) }
    val latestExport by rememberUpdatedState(pendingExport)

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = remember { CreateDocumentWithMimeContract() }
    ) { uri ->
        val cachedFile = pendingCacheFilePath?.let { File(it) }
        val contentToWrite = pendingInlineContent
            ?: cachedFile?.takeIf { it.isFile }?.let { file ->
                runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            }
            ?: latestExport?.content

        cachedFile?.let { runCatching { it.delete() } }
        pendingInlineContent = null
        pendingCacheFilePath = null
        awaitingDocumentResult = false
        onExportConsumed()

        if (uri != null && contentToWrite != null) {
            onSaveExportToUri(uri.toString(), contentToWrite)
        }
    }

    LaunchedEffect(pendingExport, awaitingDocumentResult) {
        if (pendingExport != null && !awaitingDocumentResult) {
            awaitingDocumentResult = true
            if (pendingExport.content.length <= INLINE_SAVEABLE_EXPORT_LIMIT) {
                pendingInlineContent = pendingExport.content
                pendingCacheFilePath = null
            } else {
                val stagedFile = File(context.cacheDir, "export_stage_${UUID.randomUUID()}.tmp")
                val stagedOk = runCatching {
                    stagedFile.writeText(pendingExport.content, Charsets.UTF_8)
                    true
                }.getOrDefault(false)
                pendingInlineContent = null
                pendingCacheFilePath = if (stagedOk) stagedFile.absolutePath else null
            }
            val effectiveMime = when (pendingExport.mimeType.lowercase()) {
                "application/json" -> "application/json"
                "text/markdown", "text/plain" -> "text/plain"
                else -> pendingExport.mimeType.ifBlank { "text/plain" }
            }
            createDocumentLauncher.launch(pendingExport.suggestedFileName to effectiveMime)
        }
    }
}

@Composable
fun MeloberryPageScaffold(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String? = null,
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    userMessages: List<UserMessage>,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
    headerTrailing: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalMeloberryColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    MeloberrySnackbarEffect(
        messages = userMessages,
        snackbarHostState = snackbarHostState,
        onMessageShown = onMessageShown
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        color = palette.accent,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    headerTrailing()
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = palette.muted,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = palette.accent)
                        }
                    }
                    isEmpty -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = palette.surface,
                            border = BorderStroke(1.dp, palette.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = emptyIcon,
                                    contentDescription = null,
                                    tint = palette.accent
                                )
                                Text(
                                    text = emptyTitle,
                                    color = palette.text,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!emptySubtitle.isNullOrBlank()) {
                                    Text(
                                        text = emptySubtitle,
                                        color = palette.muted,
                                        fontSize = 13.sp
                                    )
                                }
                                if (emptyActionLabel != null && onEmptyAction != null) {
                                    Button(
                                        onClick = onEmptyAction,
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                                    ) {
                                        Text(emptyActionLabel)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), content = content)
                    }
                }
            }
        }
    }
}
