package com.pxr.cymatic.ui.screens.library.album

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.hasStoragePermission
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
fun AlbumsScreen(
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadAlbums()
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    val items = uiState.albums.map { albumName ->
        NavigationItem(albumName) {
            navController.navigate(Screen.AlbumSongs.createRoute(albumName))
        }
    }

    BaseScreen(
        title = "Albums",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        searchQuery = viewModel.searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        isSearchActive = viewModel.isSearchActive,
        onSearchActiveChange = viewModel::onSearchActiveChange
    ) {
        if (!hasPermission) {
            PermissionDeniedState(
                onGrantClick = {
                    permissionLauncher.launch(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_AUDIO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                    )
                }
            )
        } else if (uiState.errorMessage != null) {
            ErrorState(
                message = uiState.errorMessage ?: "Unknown error",
                onRetry = { viewModel.loadAlbums() }
            )
        } else if (uiState.isLoading) {
            LoadingState()
        } else if (uiState.albums.isEmpty()) {
            if (viewModel.isSearchActive && viewModel.searchQuery.isNotEmpty()) {
                EmptyState(
                    title = "No Matches Found",
                    message = "No albums match '${viewModel.searchQuery}'",
                    iconText = "( ? )"
                )
            } else {
                EmptyState(
                    title = "No Albums Found",
                    message = "Cymatic did not find any albums. Scanned music will appear here.",
                    iconText = "( ! )",
                    actionLabel = "GO TO STORAGE",
                    onActionClick = { navController.navigate(Screen.StorageSettings.route) }
                )
            }
        } else {
            NavigationList(
                items = items,
                modifier = Modifier
            )
        }
    }
}
