package com.pxr.cymatic.ui.screens.library.album

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.playback.createMediaItem
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.components.common.hasStoragePermission
import com.pxr.cymatic.ui.components.common.AlbumArtistContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

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
    val queueSource = Screen.AlbumSongs.createRoute(albumName)

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

    var showHeaderMenu by remember { mutableStateOf(false) }
    val filteredFiles by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    BaseScreen(
        title = albumName,
        onBackClick = { navController.popBackStack() },
        onTitleClick = { showHeaderMenu = true },
        modifier = modifier,
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

    if (showHeaderMenu && filteredFiles.isNotEmpty()) {
        AlbumArtistContextMenu(
            title = albumName,
            onDismiss = { showHeaderMenu = false },
            onPlay = {
                mediaController?.let { controller ->
                    val items = filteredFiles.map { createMediaItem(it, queueSource) }
                    controller.setMediaItems(items, 0, 0L)
                    controller.prepare()
                    controller.play()
                }
            },
            onPlayNext = {
                mediaController?.let { controller ->
                    val items = filteredFiles.map { createMediaItem(it, queueSource) }
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
                    val items = filteredFiles.map { createMediaItem(it, queueSource) }
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
