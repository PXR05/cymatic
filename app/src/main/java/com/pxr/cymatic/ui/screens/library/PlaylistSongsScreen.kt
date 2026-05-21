package com.pxr.cymatic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.list.AudioFileContextMenu
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlaylistSongsScreen(
    playlistId: Long,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current

    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var audioFiles by remember { mutableStateOf<List<AudioFile>>(emptyList()) }
    var contextMenuFile by remember { mutableStateOf<AudioFile?>(null) }
    var infoDialogId by remember { mutableStateOf<Long?>(null) }

    val queueSource = "playlist/$playlistId"

    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            val repo = PlaylistRepository.getInstance(context)
            playlist = repo.getPlaylist(playlistId)
            audioFiles = repo.getPlaylistAudio(playlistId)
        }
    }

    BaseScreen(
        title = playlist?.name ?: "Playlist",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
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
                        queue = audioFiles,
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
