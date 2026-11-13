package com.pxr.cymatic.playback

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@UnstableApi
internal class PlaybackStateLogger(private val player: Player) {
    private companion object {
        const val TAG = "PlaybackService"
    }

    fun log(message: String, warn: Boolean = false) {
        val stateMessage =
            "$message | " +
                "state=${playbackStateName(player.playbackState)}, " +
                "playWhenReady=${player.playWhenReady}, " +
                "isPlaying=${player.isPlaying}, " +
                "suppression=${suppressionReasonName(player.playbackSuppressionReason)}, " +
                "position=${player.currentPosition}, " +
                "buffered=${player.bufferedPosition}, " +
                "mediaId=${player.currentMediaItem?.mediaId}"
        if (warn) {
            Log.w(TAG, stateMessage)
        }
    }

    fun playbackStateName(state: Int): String {
        return when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
    }

    fun playWhenReadyReasonName(reason: Int): String {
        return when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
            else -> "UNKNOWN($reason)"
        }
    }

    fun suppressionReasonName(reason: Int): String {
        return when (reason) {
            Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "NONE"
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS ->
                "TRANSIENT_AUDIO_FOCUS_LOSS"

            Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> "UNSUITABLE_AUDIO_OUTPUT"
            else -> "UNKNOWN($reason)"
        }
    }

    fun transitionReasonName(reason: Int): String {
        return when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
            else -> "UNKNOWN($reason)"
        }
    }
}
