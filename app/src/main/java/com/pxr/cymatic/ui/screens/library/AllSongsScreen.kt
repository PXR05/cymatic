package com.pxr.cymatic.ui.screens.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.list.AudioFileContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.hasStoragePermission
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun AllSongsScreen(
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    viewModel: AllSongsViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current
    val queueSource = "all_songs"

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadAudioFiles()
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    BaseScreen(
        title = "All Songs",
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
                onRetry = { viewModel.loadAudioFiles() }
            )
        } else if (uiState.isLoading) {
            LoadingState()
        } else if (uiState.songs.isEmpty()) {
            if (viewModel.isSearchActive && viewModel.searchQuery.isNotEmpty()) {
                EmptyState(
                    title = "No Matches Found",
                    message = "No songs match '${viewModel.searchQuery}'",
                    iconText = "( ? )"
                )
            } else {
                EmptyState(
                    title = "No Songs Found",
                    message = "Cymatic did not find any audio files.",
                    iconText = "( ! )",
                    actionLabel = "GO TO STORAGE",
                    onActionClick = { navController.navigate("setting/storage") }
                )
            }
        } else {
            AudioFileList(
                audioFiles = uiState.songs,
                scrollTargetId = scrollTargetId,
                onItemClick = { audioFile ->
                    mediaController?.let {
                        handleItemClick(
                            mediaController = it,
                            audioFile,
                            queue = uiState.songs,
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
                        queue = uiState.songs,
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
