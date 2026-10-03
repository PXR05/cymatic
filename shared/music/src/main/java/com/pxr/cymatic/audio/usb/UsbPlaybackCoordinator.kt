package com.pxr.cymatic.audio.usb

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pxr.cymatic.playback.FadingPlayer
import com.pxr.cymatic.playback.toAudioMetadata
import java.io.Closeable

@UnstableApi
internal class UsbPlaybackCoordinator(
    context: Context,
    private val player: ExoPlayer,
    private val fadingPlayer: FadingPlayer,
    private val audioAttributes: AudioAttributes,
) : Closeable {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val failedTracks = mutableMapOf<String, String>()
    private var focus: AudioFocusRequest? = null
    private var pendingRoute: Runnable? = null
    private var switching = false
    private var unavailableReason = "USB DAC unavailable"
    private val devices =
        UsbConnectionManager(
            context,
            onReady = {
                failedTracks.clear()
                refreshRoute()
            },
            onWaiting = { message ->
                unavailableReason = message
                refreshRoute()
            },
        )

    private val listener =
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!switching) refreshRoute()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (switching) return
                if (playWhenReady && preferredRoute() != UsbPlaybackState.routeToUsb) {
                    refreshRoute()
                } else if (playWhenReady && UsbPlaybackState.routeToUsb && !requestFocus()) {
                    player.pause()
                    UsbPlaybackState.update("Paused: audio focus unavailable")
                } else if (!playWhenReady && player.playbackState == Player.STATE_IDLE) {
                    releaseFocus()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED)
                    releaseFocus()
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (switching || !UsbPlaybackState.routeToUsb || tracks.groups.isEmpty()) return
                val supported =
                    tracks.groups.any { group ->
                        group.type == C.TRACK_TYPE_AUDIO &&
                                (0 until group.length).any { index ->
                                    DirectUsbSourcePolicy.canDecode(group.getTrackFormat(index)) &&
                                            group.isTrackSupported(index)
                                }
                    }
                if (!supported) fallback("Track format is not supported by direct USB")
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!UsbPlaybackState.routeToUsb) return
                val cause = generateSequence(error as Throwable) { it.cause }.last()
                fallback(cause.message ?: error.errorCodeName)
            }
        }

    fun start(enabled: Boolean) {
        player.addListener(listener)
        UsbPlaybackState.enabled = enabled
        devices.start(enabled)
        refreshRoute()
    }

    fun setEnabled(enabled: Boolean) {
        if (UsbPlaybackState.enabled == enabled) return
        UsbPlaybackState.enabled = enabled
        failedTracks.clear()
        devices.setEnabled(enabled)
        refreshRoute()
    }

    private fun trackKey(): String? =
        player.currentMediaItem?.let {
            "${it.mediaId}:${it.localConfiguration?.uri}"
        }

    private fun supportedSource(): Boolean {
        return DirectUsbSourcePolicy.isCandidate(player.currentMediaItem?.toAudioMetadata()?.format)
    }

    private fun preferredRoute(): Boolean =
        UsbPlaybackState.enabled &&
                devices.ready &&
                supportedSource() &&
                trackKey() !in failedTracks

    private fun fallback(reason: String) {
        trackKey()?.let { failedTracks[it] = reason }
        refreshRoute()
    }

    private fun refreshRoute() {
        pendingRoute?.let(handler::removeCallbacks)
        pendingRoute =
            Runnable {
                pendingRoute = null
                val direct = preferredRoute()
                if (direct != UsbPlaybackState.routeToUsb) switchRoute(direct)
                if (!direct) {
                    val reason =
                        when {
                            !UsbPlaybackState.enabled -> null
                            !devices.ready -> unavailableReason
                            !supportedSource() -> "Track format is not supported by direct USB"
                            else -> failedTracks[trackKey()]
                        }
                    UsbPlaybackState.update(
                        if (reason == null) "Off" else "Normal playback · $reason"
                    )
                }
            }
                .also(handler::post)
    }

    private fun switchRoute(direct: Boolean) {
        val resume = player.playWhenReady
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        switching = true
        try {
            fadingPlayer.cancelFade()
            player.stop()
            releaseFocus()
            UsbPlaybackState.setActive(false)
            UsbPlaybackState.routeToUsb = direct
            player.setAudioAttributes(audioAttributes, !direct)
            player.setPlaybackParameters(PlaybackParameters.DEFAULT)
            if (player.mediaItemCount > 0) {
                player.seekTo(index.coerceIn(0, player.mediaItemCount - 1), position)
                player.prepare()
                player.playWhenReady = resume && (!direct || requestFocus())
                if (resume && direct && !player.playWhenReady)
                    UsbPlaybackState.update("Paused: audio focus unavailable")
            }
        } finally {
            switching = false
        }
    }

    private fun requestFocus(): Boolean {
        if (focus != null) return true
        val request =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(
                    { change ->
                        if (change < 0 && UsbPlaybackState.routeToUsb) {
                            player.pause()
                            player.stop()
                            releaseFocus()
                            UsbPlaybackState.update("Paused: audio focus lost")
                        }
                    },
                    handler,
                )
                .build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            return false
        focus = request
        return true
    }

    private fun releaseFocus() {
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
    }

    override fun close() {
        pendingRoute?.let(handler::removeCallbacks)
        player.removeListener(listener)
        devices.close()
        releaseFocus()
        UsbPlaybackState.setActive(false)
        UsbPlaybackState.routeToUsb = false
    }
}
