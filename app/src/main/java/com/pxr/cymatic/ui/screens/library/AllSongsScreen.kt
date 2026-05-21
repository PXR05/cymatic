package com.pxr.cymatic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.list.AudioFileContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun AllSongsScreen(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val queueSource = "all_songs"

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredAudioFiles = remember(audioFiles, searchQuery, isSearchActive) {
        if (isSearchActive && searchQuery.isNotEmpty()) {
            audioFiles.filter { audioFile ->
                val title = audioFile.metadata.title.orEmpty()
                val artist = audioFile.metadata.artist.orEmpty()
                val album = audioFile.metadata.album.orEmpty()
                title.contains(searchQuery, ignoreCase = true) ||
                        artist.contains(searchQuery, ignoreCase = true) ||
                        album.contains(searchQuery, ignoreCase = true)
            }
        } else {
            audioFiles
        }
    }

    var contextMenuFile by remember { mutableStateOf<AudioFile?>(null) }
    var infoDialogId by remember { mutableStateOf<Long?>(null) }

    BaseScreen(
        title = "All Songs",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        isSearchActive = isSearchActive,
        onSearchActiveChange = { isSearchActive = it }
    ) {
        AudioFileList(
            audioFiles = filteredAudioFiles,
            scrollTargetId = scrollTargetId,
            onItemClick = { audioFile ->
                mediaController?.let {
                    handleItemClick(
                        mediaController = it,
                        audioFile,
                        queue = filteredAudioFiles,
                        queueSource = queueSource
                    )
                }
            },
            onItemLongClick = { audioFile -> contextMenuFile = audioFile }
        )
    }

    contextMenuFile?.let { audioFile ->
        AudioFileContextMenu(
            audioFile = audioFile,
            onDismiss = { contextMenuFile = null },
            onPlay = { file ->
                mediaController?.let {
                    handleItemClick(
                        mediaController = it,
                        audioFile = file,
                        queue = filteredAudioFiles,
                        queueSource = queueSource
                    )
                }
            },
            onTrackInfo = { file -> infoDialogId = file.id }
        )
    }

    infoDialogId?.let { id ->
        SongInfoDialog(
            showDialog = true,
            mediaId = id,
            onDismissRequest = { infoDialogId = null }
        )
    }
}


