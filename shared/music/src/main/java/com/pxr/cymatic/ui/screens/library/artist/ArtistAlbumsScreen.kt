package com.pxr.cymatic.ui.screens.library.artist

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun ArtistAlbumsScreen(
    artistName: String,
    modifier: Modifier = Modifier,
    viewModel: ArtistAlbumsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val state by viewModel.uiState.collectAsState()
    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.load(artistName)
    }

    LaunchedEffect(artistName) { viewModel.load(artistName) }

    BaseScreen(
        title = artistName,
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        showWallpaperBackdrop = true,
    ) {
        when {
            !hasPermission -> PermissionDeniedState(
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
            state.errorMessage != null -> ErrorState(
                message = state.errorMessage ?: "Unknown error",
                onRetry = { viewModel.load(artistName) },
            )
            state.isLoading -> LoadingState()
            state.totalTracks == 0 -> EmptyState(
                title = "No Music Found",
                message = "Cymatic did not find any music for '$artistName'.",
                iconText = "( ! )",
            )
            else -> NavigationList(
                items = listOf(
                    NavigationItem(
                        label = "All",
                        subLabel = "${state.totalTracks} ${trackLabel(state.totalTracks)}",
                        onClick = { navController.navigate(Screen.ArtistSongs.createRoute(artistName)) },
                    )
                ) + state.albums.map { album ->
                    NavigationItem(
                        label = album.name,
                        subLabel = "${album.trackCount} ${trackLabel(album.trackCount)}",
                        onClick = {
                            navController.navigate(Screen.ArtistAlbumSongs.createRoute(artistName, album.name))
                        },
                    )
                },
            )
        }
    }
}

private fun trackLabel(count: Int) = if (count == 1) "track" else "tracks"
