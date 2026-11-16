package com.pxr.cymatic.ui.screens.library

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
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.data.store.LibrarySyncSettings
import com.pxr.cymatic.playback.enqueueAudio
import com.pxr.cymatic.playback.playAudioQueue
import com.pxr.cymatic.sync.CatalogProgress
import com.pxr.cymatic.sync.LibraryCatalogManager
import com.pxr.cymatic.sync.RemoteLibrarySource
import com.pxr.cymatic.sync.SyncCredentialStore
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LibraryContextMenu
import com.pxr.cymatic.ui.components.common.LibraryFilterStatus
import com.pxr.cymatic.ui.components.common.LibraryFilteredEmptyState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.rememberStoragePermission
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
    viewModel: AllSongsViewModel = viewModel(),
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current
    val queueSource = Screen.AllSongs.createRoute()
    val catalogProgress by LibraryCatalogManager.progress.collectAsState()
    val scanProgress by LocalLibraryScanner.progress.collectAsState()
    val remoteConnected by
        produceState<Boolean?>(
            run {
                val address = LibrarySyncSettings.currentSyncUrl
                val username = LibrarySyncSettings.currentSyncUsername
                if (address.isBlank() || username.isBlank()) false
                else
                    SyncCredentialStore.cachedHasPassword(
                        RemoteLibrarySource("audiostream", address, username).id
                    )
            },
            context,
        ) {
            combine(
                    LibrarySyncSettings.syncUrlFlow,
                    LibrarySyncSettings.syncUsernameFlow,
                ) { address, username ->
                    address to username
                }
                .collect { (address, username) ->
                    value =
                        withContext(Dispatchers.IO) {
                            address.isNotBlank() &&
                                username.isNotBlank() &&
                                runCatching {
                                        SyncCredentialStore.hasPassword(
                                            context,
                                            RemoteLibrarySource("audiostream", address, username)
                                                .id,
                                        )
                                    }
                                    .getOrDefault(false)
                        }
                }
        }

    val permission = rememberStoragePermission { granted ->
        if (granted) {
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
    ) {
        LibraryFilterStatus(availabilityFilter)
        if (
            uiState.items.isEmpty() &&
                (catalogProgress is CatalogProgress.Refreshing ||
                    scanProgress is LocalScanProgress.Scanning ||
                    remoteConnected == null)
        ) {
            LoadingState()
        } else if (
            uiState.items.isEmpty() &&
                remoteConnected == true &&
                catalogProgress is CatalogProgress.Failed
        ) {
            ErrorState(
                message = "Could not load the remote library. Check the connection and try again.",
                onRetry = { LibraryCatalogManager.refresh(context) },
            )
        } else if (
            uiState.items.isEmpty() &&
                scanProgress is LocalScanProgress.Failed &&
                permission.granted
        ) {
            ErrorState(
                message = "Could not scan device music. Try again.",
                onRetry = { LocalLibraryScanner.scan(context) },
            )
        } else if (
            !permission.granted &&
                remoteConnected != true &&
                availabilityFilter == LibraryFilter.ALL &&
                uiState.items.isEmpty()
        ) {
            PermissionDeniedState(
                onGrantClick = {
                    permission.request()
                }
            )
        } else if (uiState.errorMessage != null) {
            ErrorState(
                message = uiState.errorMessage ?: "Unknown error",
                onRetry = { viewModel.loadAudioFiles() },
            )
        } else if (uiState.isLoading) {
            LoadingState()
        } else if (
            availabilityFilter != LibraryFilter.ALL &&
                !(viewModel.isSearchActive && viewModel.searchQuery.isNotEmpty()) &&
                uiState.items.isEmpty()
        ) {
            LibraryFilteredEmptyState()
        } else if (uiState.items.isEmpty()) {
            if (viewModel.isSearchActive && viewModel.searchQuery.isNotEmpty()) {
                EmptyState(
                    title = "No Matches Found",
                    message = "No songs match '${viewModel.searchQuery}'",
                    iconText = "( ? )",
                )
            } else {
                EmptyState(
                    title = "No Songs Found",
                    message =
                        if (remoteConnected == true)
                            "The server library has no songs. Add music on AudioStream, then refresh."
                        else "Cymatic did not find any audio files.",
                    iconText = "( ! )",
                    actionLabel =
                        if (remoteConnected == true) "REFRESH LIBRARY" else "GO TO STORAGE",
                    onActionClick = {
                        if (remoteConnected == true) LibraryCatalogManager.refresh(context)
                        else navController.navigate(Screen.StorageSettings.route)
                    },
                )
            }
        } else {
            SongListContent(viewModel.listState, uiState.items, queueSource, scrollTargetId)
        }
    }

    if (showHeaderMenu) {
        LibraryContextMenu(
            showLibraryFilter = true,
            title = "All Songs",
            onDismiss = { showHeaderMenu = false },
            onPlay =
                if (uiState.items.isNotEmpty())
                    ({
                        mediaController?.playAudioQueue(uiState.items, queueSource)
                    })
                else null,
            onPlayNext =
                if (uiState.items.isNotEmpty())
                    ({
                        mediaController?.enqueueAudio(uiState.items, queueSource, next = true)
                    })
                else null,
            onAddToQueue =
                if (uiState.items.isNotEmpty())
                    ({
                        mediaController?.enqueueAudio(uiState.items, queueSource)
                    })
                else null,
            onSearch = {
                showHeaderMenu = false
                viewModel.onSearchActiveChange(true)
            },
        )
    }

    SongListDialogs(viewModel.listState, uiState.items, queueSource)
}
