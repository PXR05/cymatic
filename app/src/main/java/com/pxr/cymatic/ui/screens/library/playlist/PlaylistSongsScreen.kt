package com.pxr.cymatic.ui.screens.library.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.playback.createMediaItem
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.common.AlbumArtistContextMenu
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.components.list.AudioFileContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
fun PlaylistSongsScreen(
    playlistId: Long,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    viewModel: PlaylistSongsViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current

    val playlist by viewModel.playlist.collectAsState()
    val audioFiles by viewModel.audioFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val queueSource = Screen.PlaylistSongs.createRoute(playlistId)

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    var showHeaderMenu by remember { mutableStateOf(false) }

    BaseScreen(
        title = playlist?.name ?: "Playlist",
        onBackClick = { navController.popBackStack() },
        onTitleClick = { showHeaderMenu = true },
        modifier = modifier
    ) {
        if (errorMessage != null) {
            ErrorState(
                message = errorMessage ?: "Unknown error",
                onRetry = { viewModel.loadPlaylist(playlistId) }
            )
        } else if (isLoading) {
            LoadingState()
        } else if (audioFiles.isEmpty()) {
            EmptyState(
                title = "Playlist is Empty",
                message = "Add tracks to this playlist from the context menu of a track.",
                iconText = "( ! )",
                actionLabel = "GO TO SONGS",
                onActionClick = { navController.navigate(Screen.AllSongs.createRoute()) }
            )
        } else {
            AudioFileList(
                audioFiles = audioFiles,
                scrollTargetId = scrollTargetId,
                onItemClick = { audioFile ->
                    mediaController?.let {
                        handleItemClick(
                            mediaController = it,
                            audioFile,
                            queue = audioFiles,
                            queueSource = queueSource
                        )
                    }
                },
                onItemLongClick = { audioFile -> viewModel.contextMenuFile = audioFile }
            )
        }
    }

    viewModel.contextMenuFile?.let { audioFile ->
        AudioFileContextMenu(
            audioFile = audioFile,
            onDismiss = { viewModel.contextMenuFile = null },
            onPlay = { file ->
                mediaController?.let {
                    handleItemClick(
                        mediaController = it,
                        audioFile = file,
                        queue = audioFiles,
                        queueSource = queueSource
                    )
                }
            },
            onPlayNext = { file ->
                mediaController?.let { controller ->
                    val currentIndex = controller.currentMediaItemIndex
                    val targetIndex = if (currentIndex >= 0) currentIndex + 1 else 0
                    val item = createMediaItem(file, queueSource)
                    if (controller.mediaItemCount == 0) {
                        controller.setMediaItem(item)
                        controller.prepare()
                        controller.play()
                    } else {
                        controller.addMediaItem(targetIndex, item)
                    }
                }
            },
            onAddToQueue = { file ->
                mediaController?.let { controller ->
                    val item = createMediaItem(file, queueSource)
                    if (controller.mediaItemCount == 0) {
                        controller.setMediaItem(item)
                        controller.prepare()
                        controller.play()
                    } else {
                        controller.addMediaItem(item)
                    }
                }
            },
            onTrackInfo = { file -> viewModel.infoDialogId = file.id }
        )
    }

    if (showHeaderMenu && audioFiles.isNotEmpty()) {
        AlbumArtistContextMenu(
            title = playlist?.name ?: "Playlist",
            onDismiss = { showHeaderMenu = false },
            onPlay = {
                mediaController?.let { controller ->
                    val items = audioFiles.map { createMediaItem(it, queueSource) }
                    controller.setMediaItems(items, 0, 0L)
                    controller.prepare()
                    controller.play()
                }
            },
            onPlayNext = {
                mediaController?.let { controller ->
                    val items = audioFiles.map { createMediaItem(it, queueSource) }
                    val currentIndex = controller.currentMediaItemIndex
                    val targetIndex = if (currentIndex >= 0) currentIndex + 1 else 0
                    if (controller.mediaItemCount == 0) {
                        controller.setMediaItems(items)
                        controller.prepare()
                        controller.play()
                    } else {
                        controller.addMediaItems(targetIndex, items)
                    }
                }
            },
            onAddToQueue = {
                mediaController?.let { controller ->
                    val items = audioFiles.map { createMediaItem(it, queueSource) }
                    if (controller.mediaItemCount == 0) {
                        controller.setMediaItems(items)
                        controller.prepare()
                        controller.play()
                    } else {
                        controller.addMediaItems(items)
                    }
                }
            }
        )
    }

    viewModel.infoDialogId?.let { id ->
        SongInfoDialog(
            showDialog = true,
            mediaId = id,
            onDismissRequest = { viewModel.infoDialogId = null }
        )
    }
}
