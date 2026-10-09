package com.pxr.cymatic.audio

import android.media.AudioManager
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.data.store.DeviceVolumeSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Runs in the playback service, including when the screen is asleep or the UI is closed. */
internal class DeviceVolumeMemory(private val audio: AudioManager) {
    suspend fun monitor() {
        var currentDevice: String? = null
        var candidate: String? = null
        var savedVolume = -1
        while (currentCoroutineContext().isActive) {
            if (UsbPlaybackState.routeToUsb) {
                currentDevice = null
                candidate = null
            } else {
                val device = resolveActiveOutput(audio).volumeKey
                if (device != currentDevice && device != candidate) {
                    candidate = device
                } else {
                    val maximum =
                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    if (device != currentDevice) {
                        val remembered = DeviceVolumeSettings.get(device, direct = false)
                        if (remembered != null && !UsbPlaybackState.routeToUsb) {
                            audio.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (remembered.coerceIn(0, 1000) * maximum / 1000f).roundToInt(),
                                0,
                            )
                        }
                        currentDevice = device
                        savedVolume = -1
                    }
                    if (!UsbPlaybackState.routeToUsb) {
                        val volume =
                            (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 1000f / maximum)
                                .roundToInt()
                        if (volume != savedVolume) {
                            DeviceVolumeSettings.save(device, direct = false, volume)
                            savedVolume = volume
                        }
                    }
                }
            }
            delay(500L)
        }
    }
}
