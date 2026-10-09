package com.pxr.cymatic.audio.usb

import android.content.Context
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
import androidx.media3.exoplayer.audio.AudioSink
import com.pxr.cymatic.audio.dsd.DSD_MIME_TYPE
import com.pxr.cymatic.audio.dsd.DsdOutput
import com.pxr.cymatic.playback.FadingPlayer
import com.pxr.cymatic.playback.OutputInfoState
import com.pxr.cymatic.playback.toAudioMetadata
import java.io.Closeable
import org.json.JSONObject

@UnstableApi
internal class UsbPlaybackCoordinator(
    context: Context,
    private val player: ExoPlayer,
    private val fadingPlayer: FadingPlayer,
    private val audioAttributes: AudioAttributes,
) : Closeable {
    private val handler = Handler(Looper.getMainLooper())
    private val failedTracks = mutableMapOf<String, String>()
    private var pendingRoute: Runnable? = null
    private var switching = false
    private var handleBecomingNoisy = true
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
                }
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
                val output =
                    generateSequence(error as Throwable) { it.cause }
                        .firstNotNullOfOrNull {
                            when (it) {
                                is AudioSink.InitializationException ->
                                    it.format.customData as? DsdOutput
                                is AudioSink.WriteException -> it.format.customData as? DsdOutput
                                else -> null
                            }
                        }
                if (output?.dop == true && UsbDsdSupport.fallback(output.sourceRate / 16)) {
                    OutputInfoState.event("DoP failed; retrying PCM: ${error.message}")
                    UsbPlaybackState.update("DSD over PCM unavailable · retrying as PCM")
                    switchRoute(true)
                    return
                }
                val cause = generateSequence(error as Throwable) { it.cause }.last()
                fallback(cause.message ?: error.errorCodeName)
            }
        }

    fun start(enabled: Boolean) {
        player.addListener(listener)
        UsbPlaybackState.enabled = enabled
        devices.start(enabled)
        applyBackgroundPlaybackPolicy(UsbPlaybackState.routeToUsb)
        refreshRoute()
    }

    fun setEnabled(enabled: Boolean) {
        if (UsbPlaybackState.enabled == enabled) return
        UsbPlaybackState.enabled = enabled
        failedTracks.clear()
        devices.setEnabled(enabled)
        refreshRoute()
    }

    fun refreshDsdOutput() {
        devices.refreshDsdOutput()
        if (
            UsbPlaybackState.enabled &&
                devices.ready &&
                player.currentMediaItem?.toAudioMetadata()?.format == DSD_MIME_TYPE
        ) {
            trackKey()?.let(failedTracks::remove)
            if (UsbPlaybackState.routeToUsb) switchRoute(true) else refreshRoute()
        }
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
        OutputInfoState.event("Direct USB fallback to Android: $reason")
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
        OutputInfoState.event("Route switch: ${if(direct) "Direct USB" else "Android AudioTrack"}")
        val resume = player.playWhenReady
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        switching = true
        try {
            fadingPlayer.cancelFade()
            player.stop()
            UsbPlaybackState.setActive(false)
            UsbPlaybackState.routeToUsb = direct
            player.setAudioAttributes(audioAttributes, !direct)
            applyBackgroundPlaybackPolicy(direct)
            player.setPlaybackParameters(PlaybackParameters.DEFAULT)
            if (player.mediaItemCount > 0) {
                player.seekTo(index.coerceIn(0, player.mediaItemCount - 1), position)
                player.prepare()
                player.playWhenReady = resume
            }
        } finally {
            switching = false
        }
    }

    override fun close() {
        pendingRoute?.let(handler::removeCallbacks)
        player.removeListener(listener)
        devices.close()
        applyBackgroundPlaybackPolicy(false)
        UsbPlaybackState.setActive(false)
        UsbPlaybackState.routeToUsb = false
    }

    /**
     * Direct USB bypasses AudioTrack, so ExoPlayer's automatic pauses for audio focus and for
     * audio-becoming-noisy (speaker fallback on route changes, e.g. when another app comes to
     * the foreground) must both stay off. Otherwise backgrounding the app pauses a healthy
     * USB stream even though there is no speaker output to protect.
     */
    private fun applyBackgroundPlaybackPolicy(direct: Boolean) {
        handleBecomingNoisy = !direct
        player.setHandleAudioBecomingNoisy(!direct)
    }

    internal fun diagnosticSnapshot(): JSONObject =
        JSONObject()
            .put("directUsbEnabled", UsbPlaybackState.enabled)
            .put("requestedRouteDirectUsb", UsbPlaybackState.routeToUsb)
            .put("dacReady", devices.ready)
            .put("usbStreamActive", UsbPlaybackState.active.value)
            .put("preferredRoute", if (preferredRoute()) "Direct USB" else "Android AudioTrack")
            .put("sourceEligibleForDirectUsb", supportedSource())
            .put("switchingRoute", switching)
            .put("routeSwitchPending", pendingRoute != null)
            .put("currentTrackFallbackReason", failedTracks[trackKey()] ?: "None")
            .put("deviceWaitingReason", if (devices.ready) "None" else unavailableReason)
            .put(
                "audioFocus",
                if (UsbPlaybackState.routeToUsb) "Disabled for direct USB"
                else "Managed by ExoPlayer",
            )
            .put(
                "audioBecomingNoisy",
                if (UsbPlaybackState.routeToUsb) "Ignored for direct USB"
                else if (handleBecomingNoisy) "Pauses playback"
                else "Ignored",
            )
}
