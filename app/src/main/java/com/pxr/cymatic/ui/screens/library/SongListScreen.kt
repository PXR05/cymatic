package com.pxr.cymatic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.playback.enqueueAudio
import com.pxr.cymatic.playback.playAudioQueue
import com.pxr.cymatic.playback.playAudioSelection
import com.pxr.cymatic.sync.LibraryRadioManager
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LibraryContextMenu
import com.pxr.cymatic.ui.components.common.LibraryFilterStatus
import com.pxr.cymatic.ui.components.common.LibraryFilteredEmptyState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.rememberStoragePermission
import com.pxr.cymatic.ui.components.list.AudioFileContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

internal class SongListState {
    var contextMenuFile by mutableStateOf<AudioFile?>(null)
}

@Composable
internal fun SongListScreen(
    title: String,
    audioFiles: List<AudioFile>,
    isLoading: Boolean,
    errorMessage: String?,
    queueSource: String,
    state: SongListState,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    onKeepOffline: (() -> Unit)? = null,
    needsStoragePermission: Boolean = true,
    showMenuWhenEmpty: Boolean = true,
    emptyContent: @Composable () -> Unit,
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current

    val permission = rememberStoragePermission { granted ->
        if (granted) {
            onReload()
        }
    }

    var showHeaderMenu by remember { mutableStateOf(false) }

    val availabilityFilter by LibraryFilterStore.filter.collectAsState()
    val sourceFilter by LibraryFilterStore.sourceFilter.collectAsState()
    val hasActiveFilter =
        availabilityFilter != LibraryFilter.ALL ||
                sourceFilter != com.pxr.cymatic.data.model.LibrarySourceFilter.All

    BaseScreen(
        title = title,
        onBackClick = { navController.popBackStack() },
        onMenuLongClick = { showHeaderMenu = true },
        modifier = modifier,
    ) {
        LibraryFilterStatus(availabilityFilter)
        if (
            needsStoragePermission &&
            !permission.granted &&
            !hasActiveFilter &&
            audioFiles.isEmpty()
        ) {
            PermissionDeniedState(
                onGrantClick = {
                    permission.request()
                }
            )
        } else if (errorMessage != null) {
            ErrorState(
                message = errorMessage,
                onRetry = onReload,
            )
        } else if (isLoading && audioFiles.isEmpty()) {
            LoadingState()
        } else if (hasActiveFilter && audioFiles.isEmpty()) {
            LibraryFilteredEmptyState()
        } else if (audioFiles.isEmpty()) {
            emptyContent()
        } else {
            SongListContent(state, audioFiles, queueSource, scrollTargetId)
        }
    }

    if (showHeaderMenu && (showMenuWhenEmpty || audioFiles.isNotEmpty())) {
        LibraryContextMenu(
            showLibraryFilter = true,
            title = title,
            onKeepOffline = onKeepOffline,
            onDismiss = { showHeaderMenu = false },
            onPlay = { mediaController?.playAudioQueue(audioFiles, queueSource) },
            onPlayNext = { mediaController?.enqueueAudio(audioFiles, queueSource, next = true) },
            onAddToQueue = { mediaController?.enqueueAudio(audioFiles, queueSource) },
        )
    }

    SongListDialogs(state, audioFiles, queueSource)
}

@Composable
internal fun SongListContent(
    state: SongListState,
    audioFiles: List<AudioFile>,
    queueSource: String,
    scrollTargetId: Long? = null,
) {
    val mediaController = LocalMediaController.current
    AudioFileList(
        audioFiles = audioFiles,
        scrollTargetId = scrollTargetId,
        onItemClick = { audioFile ->
            mediaController?.let {
                it.playAudioSelection(
                    audioFile,
                    queue = audioFiles,
                    queueSource = queueSource,
                )
            }
        },
        onItemLongClick = { audioFile -> state.contextMenuFile = audioFile },
    )
}

@Composable
internal fun SongListDialogs(
    state: SongListState,
    audioFiles: List<AudioFile>,
    queueSource: String,
) {
    val nav = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current
    val wheel = LocalWheelNavigation.current
    state.contextMenuFile?.let { file ->
        AudioFileContextMenu(
            audioFile = file,
            onDismiss = { state.contextMenuFile = null },
            onPlay = { mediaController?.playAudioSelection(it, audioFiles, queueSource) },
            onPlayNext = { mediaController?.enqueueAudio(listOf(it), queueSource, next = true) },
            onAddToQueue = { mediaController?.enqueueAudio(listOf(it), queueSource) },
            onTrackInfo = { nav.navigate(Screen.SongInfo.createRoute(it.id)) },
            onStartRadio = { seed ->
                LibraryRadioManager.startRadio(context, seed) { tracks, radioQueueSource ->
                    mediaController?.playAudioQueue(tracks, queueSource = radioQueueSource)
                    wheel?.onPlaybackRequested?.invoke()
                }
            },
        )
    }
}
