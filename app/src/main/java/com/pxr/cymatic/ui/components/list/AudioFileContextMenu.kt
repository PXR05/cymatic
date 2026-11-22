package com.pxr.cymatic.ui.components.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pxr.cymatic.R
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.LibraryAvailability
import com.pxr.cymatic.sync.LibraryOfflineManager
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
fun AudioFileContextMenu(
    audioFile: AudioFile,
    onDismiss: () -> Unit,
    onPlay: (AudioFile) -> Unit,
    onPlayNext: (AudioFile) -> Unit,
    onAddToQueue: (AudioFile) -> Unit,
    onTrackInfo: (AudioFile) -> Unit,
) {
    val title = audioFile.metadata.title ?: "Unknown Title"
    val context = LocalContext.current
    val canDownload =
        audioFile.sourceId != null &&
                audioFile.remoteId != null &&
                audioFile.availability != LibraryAvailability.OFFLINE
    val keepOffline = {
        LibraryOfflineManager.keepTrack(
            context,
            requireNotNull(audioFile.sourceId),
            requireNotNull(audioFile.remoteId),
            title,
        )
        onDismiss()
    }

    val nav = LocalNavController.current

    val wheel = checkNotNull(LocalWheelNavigation.current)
    WheelContextMenu(
        title = title,
        items =
            listOfNotNull(
                NavigationItem(
                    "Play",
                    icon = pixelIcon(R.drawable.ic_pixel_play),
                    onClick = {
                        onPlay(audioFile)
                        wheel.onPlaybackRequested()
                    },
                ),
                NavigationItem(
                    "Play Next",
                    icon = pixelIcon(R.drawable.ic_pixel_play_next),
                    onClick = {
                        onPlayNext(audioFile)
                        onDismiss()
                    },
                ),
                NavigationItem(
                    "Add to Queue",
                    icon = pixelIcon(R.drawable.ic_pixel_queue),
                    onClick = {
                        onAddToQueue(audioFile)
                        onDismiss()
                    },
                ),
                NavigationItem(
                    "Add to Playlist",
                    icon = pixelIcon(R.drawable.ic_pixel_add),
                    onClick = {
                        nav.navigate(Screen.AddToPlaylist.createRoute(audioFile.id))
                    },
                ),
                if (canDownload)
                    NavigationItem(
                        "Keep offline",
                        icon = pixelIcon(R.drawable.ic_pixel_offline),
                        onClick = keepOffline,
                    )
                else null,
                NavigationItem(
                    "Track Info",
                    icon = pixelIcon(R.drawable.ic_pixel_info),
                    onClick = {
                        onTrackInfo(audioFile)
                    },
                ),
            ),
        onDismiss = onDismiss,
    )
}
