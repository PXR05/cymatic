package com.pxr.cymatic.ui.screens.library

import android.Manifest
import android.net.Uri
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
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.components.common.hasStoragePermission
import com.pxr.cymatic.ui.components.list.AudioFileContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun AlbumSongsScreen(
    albumName: String,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    viewModel: AlbumSongsViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current
    val queueSource = "album/${Uri.encode(albumName)}"

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadAlbumSongs(albumName)
        }
    }

    LaunchedEffect(albumName) {
        viewModel.loadAlbumSongs(albumName)
    }

    val filteredFiles by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    BaseScreen(
        title = albumName,
        onBackClick = { navController.popBackStack() },
        modifier = modifier
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
        } else if (errorMessage != null) {
            ErrorState(
                message = errorMessage ?: "Unknown error",
                onRetry = { viewModel.loadAlbumSongs(albumName) }
            )
        } else if (isLoading) {
            LoadingState()
        } else if (filteredFiles.isEmpty()) {
            EmptyState(
                title = "No Songs Found",
                message = "Cymatic did not find any songs for '$albumName'.",
                iconText = "( ! )"
            )
        } else {
            AudioFileList(
                audioFiles = filteredFiles,
                scrollTargetId = scrollTargetId,
                onItemClick = { audioFile ->
                    mediaController?.let {
                        handleItemClick(
                            mediaController = it,
                            audioFile,
                            queue = filteredFiles,
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
                        queue = filteredFiles,
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
