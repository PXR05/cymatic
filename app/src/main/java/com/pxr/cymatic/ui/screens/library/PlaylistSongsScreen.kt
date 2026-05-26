package com.pxr.cymatic.ui.screens.library

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.list.AudioFileContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController

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

    val queueSource = "playlist/$playlistId"

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    BaseScreen(
        title = playlist?.name ?: "Playlist",
        onBackClick = { navController.popBackStack() },
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
                message = "Add tracks to this playlist from the context menu in the All Songs list.",
                iconText = "( ! )",
                actionLabel = "GO TO ALL SONGS",
                onActionClick = { navController.navigate("all_songs") }
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
            onTrackInfo = { file -> viewModel.infoDialogId = file.id }
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
