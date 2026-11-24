package com.meloberry.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meloberry.android.core.model.ThemeMode
import com.meloberry.android.feature.history.AnalysisHistoryRoute
import com.meloberry.android.feature.library.LibraryRoute
import com.meloberry.android.feature.player.SessionPlayerRoute
import kotlinx.serialization.Serializable

@Serializable
data object LibraryDest

@Serializable
data object HistoryDest

@Serializable
data class PlayerDest(val workspaceId: String)

@Composable
fun MeloberryApp(
    appThemeMode: ThemeMode = ThemeMode.BERRY,
    onSelectAppThemeMode: ((ThemeMode) -> Unit)? = null,
    incomingPackageUri: String? = null,
    onIncomingPackageUriConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isPlayerOpen = currentDest?.hasRoute<PlayerDest>() == true
    val layoutType = if (isPlayerOpen) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

    LaunchedEffect(incomingPackageUri) {
        if (!incomingPackageUri.isNullOrBlank() && currentDest?.hasRoute<LibraryDest>() != true) {
            navController.navigateTopLevel(LibraryDest)
        }
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            item(
                selected = currentDest?.hasRoute<LibraryDest>() == true,
                onClick = { navController.navigateTopLevel(LibraryDest) },
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Library") },
                label = { Text("Library") }
            )
            item(
                selected = currentDest?.hasRoute<HistoryDest>() == true,
                onClick = { navController.navigateTopLevel(HistoryDest) },
                icon = { Icon(Icons.Default.Analytics, contentDescription = "History") },
                label = { Text("History") }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = LibraryDest
        ) {
            composable<LibraryDest> {
                LibraryRoute(
                    onOpenWorkspace = { workspaceId ->
                        navController.navigate(PlayerDest(workspaceId)) {
                            launchSingleTop = true
                        }
                    },
                    currentThemeMode = appThemeMode,
                    onSelectThemeMode = onSelectAppThemeMode,
                    incomingPackageUri = incomingPackageUri,
                    onIncomingPackageUriConsumed = onIncomingPackageUriConsumed
                )
            }
            composable<HistoryDest> {
                AnalysisHistoryRoute()
            }
            composable<PlayerDest> {
                SessionPlayerRoute(
                    onBack = { navController.popBackStack<PlayerDest>(inclusive = true) }
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
