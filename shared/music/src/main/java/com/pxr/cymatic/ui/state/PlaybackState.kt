package com.pxr.cymatic.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.pxr.cymatic.data.model.AudioMetadata
import com.pxr.cymatic.playback.PlaybackQueue
import com.pxr.cymatic.playback.QUEUE_SOURCE_KEY
import com.pxr.cymatic.playback.playbackQueue
import com.pxr.cymatic.playback.toAudioMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class PlaybackState(
    val currentMediaId: String?,
    val isPlaying: Boolean,
    val playbackState: Int,
    val currentPositionMs: Long,
    val bufferedPositionMs: Long,
    val durationMs: Long?,
    val isShuffling: Boolean,
    val repeatMode: Int,
    val currentIndex: Int,
    val totalTracks: Int,
    val queueSource: String?,
    val queue: PlaybackQueue,
    val metadata: AudioMetadata? = null,
)

@Composable
fun rememberPlaybackState(
    mediaController: MediaController?,
    positionUpdateMs: Long = 500L,
): PlaybackState {
    var currentMediaId by remember { mutableStateOf(mediaController?.currentMediaItem?.mediaId) }
    var metadata by remember {
        mutableStateOf(mediaController?.currentMediaItem?.toAudioMetadata())
    }
    var isPlaying by remember { mutableStateOf(mediaController?.isPlaying == true) }
    var playbackState by remember {
        mutableIntStateOf(mediaController?.playbackState ?: Player.STATE_IDLE)
    }
    var currentPositionMs by remember { mutableLongStateOf(mediaController?.currentPosition ?: 0L) }
    var bufferedPositionMs by remember {
        mutableLongStateOf(mediaController?.bufferedPosition ?: 0L)
    }
    var durationMs by remember { mutableStateOf(mediaController.durationOrNull()) }
    var isShuffling by remember { mutableStateOf(mediaController?.shuffleModeEnabled == true) }
    var repeatMode by remember {
        mutableIntStateOf(mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF)
    }
    var currentIndex by remember { mutableIntStateOf(mediaController?.currentMediaItemIndex ?: 0) }
    var totalTracks by remember { mutableIntStateOf(mediaController?.mediaItemCount ?: 0) }
    var queueSource by remember { mutableStateOf(mediaController.queueSourceOrNull()) }
    var queue by remember { mutableStateOf(mediaController?.playbackQueue() ?: PlaybackQueue()) }

    fun resetState() {
        currentMediaId = null
        metadata = null
        isPlaying = false
        playbackState = Player.STATE_IDLE
        currentPositionMs = 0L
        bufferedPositionMs = 0L
        durationMs = null
        isShuffling = false
        repeatMode = Player.REPEAT_MODE_OFF
        currentIndex = 0
        totalTracks = 0
        queueSource = null
        queue = PlaybackQueue()
    }

    fun updateFromController(controller: MediaController, refreshQueue: Boolean = true) {
        currentMediaId = controller.currentMediaItem?.mediaId
        metadata = controller.currentMediaItem?.toAudioMetadata()
        isPlaying = controller.isPlaying
        playbackState = controller.playbackState
        currentPositionMs = controller.currentPosition
        bufferedPositionMs = controller.bufferedPosition
        durationMs = controller.durationOrNull()
        isShuffling = controller.shuffleModeEnabled
        repeatMode = controller.repeatMode
        currentIndex = controller.currentMediaItemIndex
        totalTracks = controller.mediaItemCount
        queueSource = controller.queueSourceOrNull()
        if (refreshQueue) queue = controller.playbackQueue()
    }

    DisposableEffect(mediaController) {
        if (mediaController == null) {
            resetState()
            return@DisposableEffect onDispose {}
        }

        updateFromController(mediaController)

        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    val refreshQueue =
                        events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                            events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
                            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                            events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                            events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
                    updateFromController(mediaController, refreshQueue)
                }
            }

        mediaController.addListener(listener)
        onDispose {
            mediaController.removeListener(listener)
        }
    }

    LaunchedEffect(mediaController, isPlaying, playbackState, positionUpdateMs) {
        if (mediaController == null) return@LaunchedEffect
        while (isActive) {
            currentPositionMs = mediaController.currentPosition
            bufferedPositionMs = mediaController.bufferedPosition
            durationMs = mediaController.durationOrNull()
            delay(positionUpdateMs)
        }
    }

    return PlaybackState(
        currentMediaId = currentMediaId,
        isPlaying = isPlaying,
        playbackState = playbackState,
        currentPositionMs = currentPositionMs,
        bufferedPositionMs = bufferedPositionMs,
        durationMs = durationMs,
        isShuffling = isShuffling,
        repeatMode = repeatMode,
        currentIndex = currentIndex,
        totalTracks = totalTracks,
        queueSource = queueSource,
        queue = queue,
        metadata = metadata,
    )
}

private fun MediaController?.durationOrNull(): Long? {
    val duration = this?.duration ?: return null
    return if (duration == C.TIME_UNSET) null else duration
}

private fun MediaController?.queueSourceOrNull(): String? {
    return this?.currentMediaItem?.mediaMetadata?.extras?.getString(QUEUE_SOURCE_KEY)?.takeIf {
        it.isNotBlank()
    }
}
