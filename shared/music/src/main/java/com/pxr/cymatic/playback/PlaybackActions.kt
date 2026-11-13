package com.pxr.cymatic.playback

import androidx.media3.common.Player

data class PlaybackActions(
    val shuffle: () -> Unit,
    val previous: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val repeat: () -> Unit,
) {
    companion object {
        fun forPlayer(player: Player) =
            PlaybackActions(
                shuffle = { player.shuffleModeEnabled = !player.shuffleModeEnabled },
                previous = player::seekToPrevious,
                playPause = { if (player.isPlaying) player.pause() else player.play() },
                next = player::seekToNext,
                repeat = player::cycleRepeat,
            )
    }
}

fun Player.cycleRepeat() {
    repeatMode =
        when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
}
