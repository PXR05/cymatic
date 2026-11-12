package com.pxr.cymatic.ui.screens.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.data.media.LocalLibraryScanner
import com.pxr.cymatic.data.media.LocalScanProgress
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.playback.createMediaItem
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.sync.CatalogProgress
import com.pxr.cymatic.sync.LibraryCatalogManager
import com.pxr.cymatic.sync.RemoteLibrarySource
import com.pxr.cymatic.sync.SyncCredentialStore
import com.pxr.cymatic.ui.components.common.AlbumArtistContextMenu
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LibraryFilterStatus
import com.pxr.cymatic.ui.components.common.LibraryFilteredEmptyState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.components.common.hasStoragePermission
import com.pxr.cymatic.ui.components.list.AudioFileContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

@Composable
fun AllSongsScreen(
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    viewModel: AllSongsViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current
    val queueSource = Screen.AllSongs.createRoute()
    val catalogProgress by LibraryCatalogManager.progress.collectAsState()
    val scanProgress by LocalLibraryScanner.progress.collectAsState()
    val remoteConnected by produceState<Boolean?>(null, context) {
        combine(
            SettingsStore.syncUrlFlow,
            SettingsStore.syncUsernameFlow
        ) { address, username -> address to username }
            .collect { (address, username) ->
                value = withContext(Dispatchers.IO) {
                    address.isNotBlank() && username.isNotBlank() && runCatching {
                        SyncCredentialStore.hasPassword(
                            context,
                            RemoteLibrarySource("audiostream", address, username).id
                        )
                    }.getOrDefault(false)
                }
            }
    }

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            LocalLibraryScanner.scan(context)
            viewModel.loadAudioFiles()
        }
    }

    var showHeaderMenu by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    val availabilityFilter by LibraryFilterStore.filter.collectAsState()

    BaseScreen(
        title = "All Songs",
        onBackClick = { navController.popBackStack() },
        onMenuLongClick = { showHeaderMenu = true },
        modifier = modifier,
        searchQuery = viewModel.searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        isSearchActive = viewModel.isSearchActive,
        onSearchActiveChange = viewModel::onSearchActiveChange,
        showSearchAction = false,
        showWallpaperBackdrop = true
    ) {
        LibraryFilterStatus(availabilityFilter)
        if (uiState.songs.isEmpty() &&
            (catalogProgress is CatalogProgress.Refreshing || scanProgress is LocalScanProgress.Scanning || remoteConnected == null)
        ) {
            LoadingState()
        } else if (uiState.songs.isEmpty() && remoteConnected == true && catalogProgress is CatalogProgress.Failed) {
            ErrorState(
                message = "Could not load the remote library. Check the connection and try again.",
                onRetry = { LibraryCatalogManager.refresh(context) },
            )
        } else if (uiState.songs.isEmpty() && scanProgress is LocalScanProgress.Failed && hasPermission) {
            ErrorState(
                message = "Could not scan device music. Try again.",
                onRetry = { LocalLibraryScanner.scan(context) },
            )
        } else if (!hasPermission && remoteConnected != true && availabilityFilter == LibraryFilter.ALL && uiState.songs.isEmpty()) {
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
        } else if (availabilityFilter != LibraryFilter.ALL && !(viewModel.isSearchActive && viewModel.searchQuery.isNotEmpty()) && uiState.songs.isEmpty()) {
            LibraryFilteredEmptyState()
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
                    message = if (remoteConnected == true) "The server library has no songs. Add music on AudioStream, then refresh." else "Cymatic did not find any audio files.",
                    iconText = "( ! )",
                    actionLabel = if (remoteConnected == true) "REFRESH LIBRARY" else "GO TO STORAGE",
                    onActionClick = {
                        if (remoteConnected == true) LibraryCatalogManager.refresh(context)
                        else navController.navigate(Screen.StorageSettings.route)
                    }
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

    if (showHeaderMenu) {
        AlbumArtistContextMenu(
            showLibraryFilter = true,
            title = "All Songs",
            onDismiss = { showHeaderMenu = false },
            onPlay = if (uiState.songs.isNotEmpty()) {
                {
                    mediaController?.let { controller ->
                        val items = uiState.songs.map { createMediaItem(it, queueSource) }
                        controller.setMediaItems(items, 0, 0L)
                        controller.prepare()
                        controller.play()
                    }
                }
            } else null,
            onPlayNext = if (uiState.songs.isNotEmpty()) {
                {
                    mediaController?.let { controller ->
                        val items = uiState.songs.map { createMediaItem(it, queueSource) }
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
                }
            } else null,
            onAddToQueue = if (uiState.songs.isNotEmpty()) {
                {
                    mediaController?.let { controller ->
                        val items = uiState.songs.map { createMediaItem(it, queueSource) }
                        if (controller.mediaItemCount == 0) {
                            controller.setMediaItems(items)
                            controller.prepare()
                            controller.play()
                        } else {
                            controller.addMediaItems(items)
                        }
                    }
                }
            } else null,
            onSearch = {
                showHeaderMenu = false
                viewModel.onSearchActiveChange(true)
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
