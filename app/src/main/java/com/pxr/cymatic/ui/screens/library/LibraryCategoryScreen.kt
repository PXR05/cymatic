package com.pxr.cymatic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import com.pxr.cymatic.playback.enqueueAudio
import com.pxr.cymatic.playback.playAudioQueue
import com.pxr.cymatic.sync.LibraryOfflineManager
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LibraryContextMenu
import com.pxr.cymatic.ui.components.common.LibraryFilterStatus
import com.pxr.cymatic.ui.components.common.LibraryFilteredEmptyState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.rememberStoragePermission
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
internal fun LibraryCategoryScreen(category: LibraryCategory, modifier: Modifier = Modifier) {
    val factory =
        remember(category) {
            viewModelFactory {
                initializer {
                    LibraryCategoryViewModel(checkNotNull(this[APPLICATION_KEY]), category)
                }
            }
        }
    val viewModel: LibraryCategoryViewModel = viewModel(key = category.name, factory = factory)
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current

    val permission = rememberStoragePermission { granted ->
        if (granted) {
            viewModel.refresh()
        }
    }

    var showPageMenu by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<LibraryCategoryItem?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    val groups = rememberLibraryGroups()
    val sources by RemoteSourceRegistry.sourcesFlow.collectAsState(
        initial = RemoteSourceRegistry.currentSources
    )
    val sourceNames = remember(sources) { sources.associate { it.id to it.displayName } }
    val showSourceSubLabel = remember(category, uiState.items) {
        category.splitBySource && uiState.items.map { it.sourceId }.distinct().size > 1
    }

    val items =
        uiState.items.map { item ->
            val subLabel =
                if (showSourceSubLabel) {
                    item.sourceId?.let { sourceNames[it] ?: "Remote" } ?: "This device"
                } else null
            NavigationItem(
                label = item.name,
                subLabel = subLabel,
                key = item.key,
                trailingContent = { color ->
                    LibraryGroupIndicator(category.group(groups, item), color)
                },
                onClick = {
                    navController.navigate(category.browseRoute(item))
                },
                onLongClick = { selectedItem = item },
            )
        }

    val availabilityFilter by LibraryFilterStore.filter.collectAsState()

    BaseScreen(
        title = category.title,
        onBackClick = { navController.popBackStack() },
        onMenuLongClick = { showPageMenu = true },
        modifier = modifier,
        searchQuery = viewModel.searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        isSearchActive = viewModel.isSearchActive,
        onSearchActiveChange = viewModel::onSearchActiveChange,
        showSearchAction = false,
    ) {
        LibraryFilterStatus(availabilityFilter)
        if (
            !permission.granted &&
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
                onRetry = { viewModel.refresh() },
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
                    message = "No ${category.title.lowercase()} match '${viewModel.searchQuery}'",
                    iconText = "( ? )",
                )
            } else {
                EmptyState(
                    title = "No ${category.title} Found",
                    message = "Cymatic did not find any ${category.title.lowercase()}.",
                    iconText = "( ! )",
                    actionLabel = "GO TO STORAGE",
                    onActionClick = { navController.navigate(Screen.StorageSettings.route) },
                )
            }
        } else {
            NavigationList(
                items = items,
                modifier = Modifier,
            )
        }
    }

    selectedItem?.let { item ->
        LibraryContextMenu(
            title = item.name,
            onKeepOffline =
                category
                    .group(groups, item)
                    ?.takeIf { it.canKeepOffline }
                    ?.let { group ->
                        { LibraryOfflineManager.keepSongs(context, group.songs, item.name) }
                    },
            onDismiss = { selectedItem = null },
            onPlay = {
                mediaController?.let { controller ->
                    viewModel.getSongs(item) { songs ->
                        controller.playAudioQueue(songs, category.songsRoute(item))
                    }
                }
            },
            onPlayNext = {
                mediaController?.let { controller ->
                    viewModel.getSongs(item) { songs ->
                        controller.enqueueAudio(
                            songs,
                            category.songsRoute(item),
                            next = true,
                        )
                    }
                }
            },
            onAddToQueue = {
                mediaController?.let { controller ->
                    viewModel.getSongs(item) { songs ->
                        controller.enqueueAudio(songs, category.songsRoute(item))
                    }
                }
            },
        )
    }

    if (showPageMenu) {
        LibraryContextMenu(
            title = category.title,
            showLibraryFilter = true,
            onDismiss = { showPageMenu = false },
            onSearch = {
                showPageMenu = false
                viewModel.onSearchActiveChange(true)
            },
        )
    }
}
