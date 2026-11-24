package com.meloberry.android.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meloberry.android.core.model.MeloberryVersionInfo
import com.meloberry.android.core.model.ThemeMode
import com.meloberry.android.core.model.UserMessage
import com.meloberry.android.core.model.WorkspaceKind
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
    currentThemeMode: ThemeMode = ThemeMode.BERRY,
    onSelectThemeMode: ((ThemeMode) -> Unit)? = null,
    incomingPackageUri: String? = null,
    onIncomingPackageUriConsumed: () -> Unit = {},
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

    LaunchedEffect(incomingPackageUri) {
        if (!incomingPackageUri.isNullOrBlank()) {
            viewModel.importPackage(incomingPackageUri)
            onIncomingPackageUriConsumed()
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
        onImportClick = {
            if (!uiState.isImporting) {
                openPackageLauncher.launch(arrayOf("*/*"))
            }
        },
        onOpenWorkspace = onOpenWorkspace,
        onDeleteWorkspace = viewModel::deleteWorkspace,
        onMessageShown = viewModel::onMessageShown,
        currentThemeMode = currentThemeMode,
        onSelectThemeMode = onSelectThemeMode,
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
    currentThemeMode: ThemeMode = ThemeMode.BERRY,
    onSelectThemeMode: ((ThemeMode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    var pendingDeleteWorkspace by remember { mutableStateOf<WorkspaceSummary?>(null) }
    var showThemeMenu by remember { mutableStateOf(false) }

    pendingDeleteWorkspace?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteWorkspace = null },
            title = { Text("Delete workspace?") },
            text = {
                Text(
                    "Deleting \"${target.title.ifBlank { target.id }}\" will permanently remove this workspace, its local media assets, and all saved session analysis history & reports for it."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteWorkspace = null
                        onDeleteWorkspace(target.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.bad)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteWorkspace = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    MeloberryPageScaffold(
        title = "Meloberry Library",
        subtitle = "Open an imported Study Session or Quiz, or import a .mbypkg exported by Meloberry CLI (mby).",
        isLoading = uiState.isLoading,
        isEmpty = uiState.workspaces.isEmpty(),
        emptyIcon = Icons.Default.FolderOpen,
        emptyTitle = "No workspaces imported yet",
        emptyActionLabel = if (uiState.isImporting) "Importing..." else "Import .mbypkg",
        onEmptyAction = if (uiState.isImporting) null else onImportClick,
        userMessages = uiState.userMessages,
        onMessageShown = onMessageShown,
        modifier = modifier,
        headerTrailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = palette.accentSoft,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Text(
                        text = "v${MeloberryVersionInfo.VERSION_NAME}",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                if (onSelectThemeMode != null) {
                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "App theme",
                                tint = palette.accent
                            )
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            listOf(
                                ThemeMode.BERRY to "Berry",
                                ThemeMode.LIGHT to "Light",
                                ThemeMode.DARK to "Dark",
                                ThemeMode.PAPER to "Paper"
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontWeight = if (mode == currentThemeMode) FontWeight.Bold else FontWeight.Normal) },
                                    trailingIcon = if (mode == currentThemeMode) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    onClick = {
                                        showThemeMenu = false
                                        onSelectThemeMode(mode)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!uiState.isImporting) {
                        onImportClick()
                    }
                },
                modifier = Modifier.alpha(if (uiState.isImporting) 0.6f else 1f),
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
                    onDelete = { pendingDeleteWorkspace = item }
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
    val kindLabel = when (item.kind) {
        WorkspaceKind.SESSION -> "SESSION"
        WorkspaceKind.QUIZ -> {
            val sub = item.subType.takeIf { it.isNotBlank() && !it.equals("quiz", ignoreCase = true) }
            if (sub != null) "QUIZ • ${sub.uppercase()}" else "QUIZ"
        }
    }
    val itemCountLabel = when (item.kind) {
        WorkspaceKind.SESSION -> if (item.itemCount == 1) "1 component" else "${item.itemCount} components"
        WorkspaceKind.QUIZ -> if (item.itemCount == 1) "1 question" else "${item.itemCount} questions"
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(palette.accentSoft, RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = kindLabel,
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = itemCountLabel,
                        color = palette.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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
