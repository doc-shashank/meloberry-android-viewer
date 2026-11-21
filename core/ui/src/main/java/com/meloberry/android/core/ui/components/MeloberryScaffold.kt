package com.meloberry.android.core.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.PendingExportPayload
import com.meloberry.android.core.model.UserMessage
import com.meloberry.android.core.ui.theme.LocalMeloberryColors

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
    var pendingContent by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingDocumentResult by rememberSaveable { mutableStateOf(false) }
    val latestExport by rememberUpdatedState(pendingExport)

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val contentToWrite = pendingContent ?: latestExport?.content
        pendingContent = null
        awaitingDocumentResult = false
        onExportConsumed()
        if (uri != null && contentToWrite != null) {
            onSaveExportToUri(uri.toString(), contentToWrite)
        }
    }

    LaunchedEffect(pendingExport, awaitingDocumentResult) {
        if (pendingExport != null && !awaitingDocumentResult) {
            awaitingDocumentResult = true
            pendingContent = if (pendingExport.content.length <= 200_000) pendingExport.content else null
            createDocumentLauncher.launch(pendingExport.suggestedFileName)
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
                Text(
                    text = title,
                    color = palette.accent,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
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
