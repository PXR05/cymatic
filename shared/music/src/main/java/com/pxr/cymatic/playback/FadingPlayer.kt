package com.pxr.cymatic.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.data.store.SettingsStore
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@UnstableApi
class FadingPlayer(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
) : ForwardingPlayer(player) {

    private var fadeJob: Job? = null
    private val fadeDurationMs = 300L
    private val fadeIntervalMs = 15L

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        super.setMediaItems(mediaItems)
        startShuffleAtCurrentItem()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        super.setMediaItems(mediaItems, resetPosition)
        startShuffleAtCurrentItem()
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        startShuffleAtCurrentItem()
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        super.setMediaItem(mediaItem)
        startShuffleAtCurrentItem()
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        super.setMediaItem(mediaItem, startPositionMs)
        startShuffleAtCurrentItem()
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        super.setMediaItem(mediaItem, resetPosition)
        startShuffleAtCurrentItem()
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        super.setShuffleModeEnabled(shuffleModeEnabled)
        if (shuffleModeEnabled) startShuffleAtCurrentItem()
    }

    private fun startShuffleAtCurrentItem() {
        if (!player.shuffleModeEnabled || player.mediaItemCount == 0) return
        val indices =
            player.currentTimeline
                .mediaItemIndicesInPlaybackOrder(true)
                .startingWith(player.currentMediaItemIndex)
        player.setShuffleOrder(DefaultShuffleOrder(indices.toIntArray(), Random.nextLong()))
    }

    override fun play() {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        setPlayWhenReady(true)
    }

    override fun pause() {
        setPlayWhenReady(false)
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        fadeJob?.cancel()
        if (!SettingsStore.currentFadeEnabled || UsbPlaybackState.routeToUsb) {
            player.volume = 1.0f
            super.setPlayWhenReady(playWhenReady)
            return
        }
        if (playWhenReady) {
            if (!player.playWhenReady) {
                player.volume = 0.0f
                super.setPlayWhenReady(true)
            }
            val startVolume = player.volume
            fadeJob =
                scope.launch(Dispatchers.Main) {
                    val steps = (fadeDurationMs / fadeIntervalMs).toInt()
                    for (i in 1..steps) {
                        delay(fadeIntervalMs)
                        val newVolume = startVolume + (1.0f - startVolume) * (i.toFloat() / steps)
                        player.volume = newVolume.coerceIn(0.0f, 1.0f)
                    }
                    player.volume = 1.0f
                }
        } else {
            if (player.playWhenReady) {
                val startVolume = player.volume
                fadeJob =
                    scope.launch(Dispatchers.Main) {
                        val steps = (fadeDurationMs / fadeIntervalMs).toInt()
                        for (i in 1..steps) {
                            delay(fadeIntervalMs)
                            val newVolume = startVolume - startVolume * (i.toFloat() / steps)
                            player.volume = newVolume.coerceIn(0.0f, 1.0f)
                        }
                        player.volume = 0.0f
                        super.setPlayWhenReady(false)
                        player.volume = 1.0f
                    }
            } else {
                super.setPlayWhenReady(false)
            }
        }
    }

    override fun release() {
        fadeJob?.cancel()
        super.release()
    }

    fun cancelFade() {
        fadeJob?.cancel()
        player.volume = 1f
    }

    internal fun diagnosticSnapshot(): JSONObject =
        JSONObject()
            .put("enabled", SettingsStore.currentFadeEnabled)
            .put("bypassedForDirectUsb", UsbPlaybackState.routeToUsb)
            .put("fadeInProgress", fadeJob?.isActive == true)
            .put("durationMs", fadeDurationMs)
            .put("updateIntervalMs", fadeIntervalMs)
            .put("currentSoftwareGain", player.volume.toDouble())
}
