package com.pxr.cymatic.playback

import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.audio.usb.UsbVolumeState
import com.pxr.cymatic.data.store.SettingsStore
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject

@UnstableApi
class FadingPlayer(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
) : ForwardingPlayer(player) {

    private var fadeJob: Job? = null
    private var fadingOut = false
    private val fadeDurationMs = 300L
    private val fadeIntervalMs = 15L
    private val listeners = CopyOnWriteArraySet<Player.Listener>()
    private val remoteDeviceInfo =
        DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(100)
            .build()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                    if (UsbPlaybackState.volumeActive.value) {
                        val commands = this@FadingPlayer.availableCommands
                        listeners.forEach { it.onAvailableCommandsChanged(commands) }
                    }
                }

                override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                    if (UsbPlaybackState.volumeActive.value) {
                        val info = this@FadingPlayer.deviceInfo
                        listeners.forEach { it.onDeviceInfoChanged(info) }
                    }
                }

                override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
                    if (UsbPlaybackState.volumeActive.value) {
                        val currentVolume = this@FadingPlayer.deviceVolume
                        val currentMuted = this@FadingPlayer.isDeviceMuted
                        listeners.forEach { it.onDeviceVolumeChanged(currentVolume, currentMuted) }
                    }
                }
            }
        )
        scope.launch(Dispatchers.Main) {
            var lastRemote = false
            combine(
                    UsbPlaybackState.volumeActive,
                    UsbVolumeState.requestedPercent,
                    UsbVolumeState.level,
                ) { remote, requested, level ->
                    Triple(remote, requested, requested == 0 || level.muted)
                }
                .collect { (remote, volume, muted) ->
                    if (remote != lastRemote) {
                        lastRemote = remote
                        val commands = availableCommands
                        val info = deviceInfo
                        val currentVolume = deviceVolume
                        val currentMuted = isDeviceMuted
                        listeners.forEach {
                            it.onAvailableCommandsChanged(commands)
                            it.onDeviceInfoChanged(info)
                            it.onDeviceVolumeChanged(currentVolume, currentMuted)
                        }
                    } else if (remote) {
                        listeners.forEach { it.onDeviceVolumeChanged(volume, muted) }
                    }
                }
        }
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners.add(listener)
        if (UsbPlaybackState.volumeActive.value) {
            listener.onAvailableCommandsChanged(availableCommands)
            listener.onDeviceInfoChanged(deviceInfo)
            listener.onDeviceVolumeChanged(deviceVolume, isDeviceMuted)
        }
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    @Suppress("DEPRECATION")
    override fun isCommandAvailable(command: Int): Boolean {
        if (
            UsbPlaybackState.volumeActive.value &&
                (command == Player.COMMAND_GET_DEVICE_VOLUME ||
                    command == Player.COMMAND_SET_DEVICE_VOLUME ||
                    command == Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS ||
                    command == Player.COMMAND_ADJUST_DEVICE_VOLUME ||
                    command == Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
        ) {
            return true
        }
        return super.isCommandAvailable(command)
    }

    @Suppress("DEPRECATION")
    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!UsbPlaybackState.volumeActive.value) return base
        return base
            .buildUpon()
            .addAll(
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
            )
            .build()
    }

    override fun getDeviceInfo(): DeviceInfo =
        if (UsbPlaybackState.volumeActive.value) remoteDeviceInfo else super.getDeviceInfo()

    override fun getDeviceVolume(): Int =
        if (UsbPlaybackState.volumeActive.value) UsbVolumeState.requestedPercent.value
        else super.getDeviceVolume()

    override fun isDeviceMuted(): Boolean =
        if (UsbPlaybackState.volumeActive.value) {
            UsbVolumeState.requestedPercent.value == 0 || UsbVolumeState.level.value.muted
        } else {
            super.isDeviceMuted()
        }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun setDeviceVolume(volume: Int) {
        if (UsbPlaybackState.volumeActive.value) UsbVolumeState.setRequested(volume)
        else super.setDeviceVolume(volume)
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        if (UsbPlaybackState.volumeActive.value) UsbVolumeState.setRequested(volume)
        else super.setDeviceVolume(volume, flags)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun increaseDeviceVolume() {
        if (UsbPlaybackState.volumeActive.value) UsbVolumeState.adjust(2)
        else super.increaseDeviceVolume()
    }

    override fun increaseDeviceVolume(flags: Int) {
        if (UsbPlaybackState.volumeActive.value) UsbVolumeState.adjust(2)
        else super.increaseDeviceVolume(flags)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun decreaseDeviceVolume() {
        if (UsbPlaybackState.volumeActive.value) UsbVolumeState.adjust(-2)
        else super.decreaseDeviceVolume()
    }

    override fun decreaseDeviceVolume(flags: Int) {
        if (UsbPlaybackState.volumeActive.value) UsbVolumeState.adjust(-2)
        else super.decreaseDeviceVolume(flags)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun setDeviceMuted(muted: Boolean) {
        if (UsbPlaybackState.volumeActive.value) UsbVolumeState.setMuted(muted)
        else super.setDeviceMuted(muted)
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        if (UsbPlaybackState.volumeActive.value) UsbVolumeState.setMuted(muted)
        else super.setDeviceMuted(muted, flags)
    }

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

    override fun seekTo(positionMs: Long) {
        finishFadeOutBeforeSeek()
        super.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        finishFadeOutBeforeSeek()
        super.seekTo(mediaItemIndex, positionMs)
    }

    private fun finishFadeOutBeforeSeek() {
        if (!fadingOut) return
        fadeJob?.cancel()
        fadingOut = false
        super.setPlayWhenReady(false)
        player.volume = 1.0f
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        fadeJob?.cancel()
        fadingOut = false
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
                fadingOut = true
                val startVolume = player.volume
                fadeJob =
                    scope.launch(Dispatchers.Main) {
                        val steps = (fadeDurationMs / fadeIntervalMs).toInt()
                        for (i in 1..steps) {
                            delay(fadeIntervalMs)
                            val newVolume = startVolume - startVolume * (i.toFloat() / steps)
                            player.volume = newVolume.coerceIn(0.0f, 1.0f)
                        }
                        fadingOut = false
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
        fadingOut = false
        listeners.clear()
        super.release()
    }

    fun cancelFade() {
        fadeJob?.cancel()
        fadingOut = false
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
