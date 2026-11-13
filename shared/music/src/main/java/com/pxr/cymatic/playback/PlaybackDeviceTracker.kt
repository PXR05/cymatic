package com.pxr.cymatic.playback

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.audio.resolveActiveOutput
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.data.store.EqSettingsStore
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
internal class PlaybackDeviceTracker(
    private val audioManager: AudioManager,
    private val player: Player,
    private val serviceScope: CoroutineScope,
) : AutoCloseable {
    private var audioDeviceCallback: AudioDeviceCallback? = null

    fun start() {
        fun updateActiveDevice() {
            serviceScope.launch {
                EqSettingsStore.setActiveAudioDevice(resolveActiveOutput(audioManager).key)
            }
        }

        audioDeviceCallback =
            object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                        updateActiveDevice()
                        val hasBluetooth = addedDevices.any { device ->
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        }
                        if (hasBluetooth) {
                            serviceScope.launch {
                                if (
                                    SettingsStore.currentResumeOnBluetoothReconnect &&
                                        !UsbPlaybackState.routeToUsb
                                ) {
                                    delay(500L)
                                    withContext(Dispatchers.Main) {
                                        if (player.mediaItemCount > 0 && !player.isPlaying) {
                                            player.play()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                        updateActiveDevice()
                    }
                }
                .also { audioManager.registerAudioDeviceCallback(it, null) }

        updateActiveDevice()
    }

    override fun close() {
        audioDeviceCallback?.let(audioManager::unregisterAudioDeviceCallback)
        audioDeviceCallback = null
    }
}
