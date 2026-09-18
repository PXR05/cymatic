package com.pxr.cymatic.ui.screens.library.artist

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
import com.pxr.cymatic.playback.createMediaItem
import com.pxr.cymatic.ui.components.common.AlbumArtistContextMenu
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.hasStoragePermission
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
fun ArtistsScreen(
    modifier: Modifier = Modifier,
    viewModel: ArtistsViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadArtists()
        }
    }

    var selectedArtist by remember { mutableStateOf<String?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    val items = uiState.artists.map { artistName ->
        NavigationItem(
            label = artistName,
            onClick = {
                navController.navigate(Screen.ArtistSongs.createRoute(artistName))
            },
            onLongClick = { selectedArtist = artistName }
        )
    }

    BaseScreen(
        title = "Artists",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        searchQuery = viewModel.searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        isSearchActive = viewModel.isSearchActive,
        onSearchActiveChange = viewModel::onSearchActiveChange,
        showWallpaperBackdrop = true
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
                onRetry = { viewModel.loadArtists() }
            )
        } else if (uiState.isLoading) {
            LoadingState()
        } else if (uiState.artists.isEmpty()) {
            if (viewModel.isSearchActive && viewModel.searchQuery.isNotEmpty()) {
                EmptyState(
                    title = "No Matches Found",
                    message = "No artists match '${viewModel.searchQuery}'",
                    iconText = "( ? )"
                )
            } else {
                EmptyState(
                    title = "No Artists Found",
                    message = "Cymatic did not find any artists.",
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

    selectedArtist?.let { artistName ->
        AlbumArtistContextMenu(
            title = artistName,
            onDismiss = { selectedArtist = null },
            onPlay = {
                mediaController?.let { controller ->
                    viewModel.getSongsForArtist(artistName) { songs ->
                        if (songs.isNotEmpty()) {
                            val route = Screen.ArtistSongs.createRoute(artistName)
                            val mediaItems = songs.map { createMediaItem(it, route) }
                            controller.setMediaItems(mediaItems, 0, 0L)
                            controller.prepare()
                            controller.play()
                        }
                    }
                }
            },
            onPlayNext = {
                mediaController?.let { controller ->
                    viewModel.getSongsForArtist(artistName) { songs ->
                        if (songs.isNotEmpty()) {
                            val route = Screen.ArtistSongs.createRoute(artistName)
                            val mediaItems = songs.map { createMediaItem(it, route) }
                            val currentIndex = controller.currentMediaItemIndex
                            val targetIndex = if (currentIndex >= 0) currentIndex + 1 else 0
                            if (controller.mediaItemCount == 0) {
                                controller.setMediaItems(mediaItems)
                                controller.prepare()
                                controller.play()
                            } else {
                                controller.addMediaItems(targetIndex, mediaItems)
                            }
                        }
                    }
                }
            },
            onAddToQueue = {
                mediaController?.let { controller ->
                    viewModel.getSongsForArtist(artistName) { songs ->
                        if (songs.isNotEmpty()) {
                            val route = Screen.ArtistSongs.createRoute(artistName)
                            val mediaItems = songs.map { createMediaItem(it, route) }
                            if (controller.mediaItemCount == 0) {
                                controller.setMediaItems(mediaItems)
                                controller.prepare()
                                controller.play()
                            } else {
                                controller.addMediaItems(mediaItems)
                            }
                        }
                    }
                }
            }
        )
    }
}
