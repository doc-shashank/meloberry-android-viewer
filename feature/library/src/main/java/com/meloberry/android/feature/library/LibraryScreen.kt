package com.meloberry.android.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meloberry.android.core.model.UserMessage
import com.meloberry.android.core.model.WorkspaceSummary
import com.meloberry.android.core.ui.components.MeloberryPageScaffold
import com.meloberry.android.core.ui.theme.LocalMeloberryColors

data class LibraryUiState(
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val workspaces: List<WorkspaceSummary> = emptyList(),
    val pendingOpenWorkspaceId: String? = null,
    val userMessages: List<UserMessage> = emptyList()
)

@Composable
fun LibraryRoute(
    onOpenWorkspace: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val openPackageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPackage(uri.toString())
        }
    }

    val pendingOpenId = uiState.pendingOpenWorkspaceId
    LaunchedEffect(pendingOpenId) {
        if (pendingOpenId != null) {
            onOpenWorkspace(pendingOpenId)
            viewModel.onNavigationHandled()
        }
    }

    LibraryScreen(
        uiState = uiState,
        onImportClick = { openPackageLauncher.launch(arrayOf("*/*")) },
        onOpenWorkspace = onOpenWorkspace,
        onDeleteWorkspace = viewModel::deleteWorkspace,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onImportClick: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onDeleteWorkspace: (String) -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current

    MeloberryPageScaffold(
        title = "Meloberry Library",
        subtitle = "Open an imported Study Session or Quiz, or import a .mbypkg exported by mby.exe.",
        isLoading = uiState.isLoading,
        isEmpty = uiState.workspaces.isEmpty(),
        emptyIcon = Icons.Default.FolderOpen,
        emptyTitle = "No workspaces imported yet",
        emptyActionLabel = "Import .mbypkg",
        onEmptyAction = onImportClick,
        userMessages = uiState.userMessages,
        onMessageShown = onMessageShown,
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImportClick,
                containerColor = palette.accent,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (uiState.isImporting) "Importing..." else "Import .mbypkg") }
            )
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.workspaces, key = { it.id }) { item ->
                WorkspaceCard(
                    item = item,
                    onPlay = { onOpenWorkspace(item.id) },
                    onDelete = { onDeleteWorkspace(item.id) }
                )
            }
        }
    }
}

@Composable
private fun WorkspaceCard(
    item: WorkspaceSummary,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalMeloberryColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(18.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = item.title,
                    color = palette.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = item.description.ifBlank { item.id },
                    color = palette.muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledIconButton(
                    onClick = onPlay,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = palette.accent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start workspace"
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete workspace",
                        tint = palette.muted
                    )
                }
            }
        }
    }
}
