package com.pxr.cymatic.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

data class AudioOutputDevice(
    val key: String,
    val label: String,
    val type: String,
    val volumeKey: String = key,
    val detection: String = "Connected-device preference heuristic",
)

fun resolveActiveOutput(audioManager: AudioManager): AudioOutputDevice {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val routed = runCatching {
            audioManager
                .getAudioDevicesForAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .firstOrNull()
        }
            .getOrNull()
        if (routed != null)
            return routed.toOutputDevice().copy(detection = "AudioManager media-attribute routing")
    }
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    val preferredTypes =
        listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        )

    val active =
        preferredTypes.firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
            ?: devices.firstOrNull()

    return active?.toOutputDevice()
        ?: AudioOutputDevice(
            key = "unknown",
            label = "?",
            type = "?",
        )
}

private fun AudioDeviceInfo.toOutputDevice(): AudioOutputDevice {
    val productName = productName?.toString()?.trim().orEmpty()
    val display =
        when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT" to productName.ifBlank { "BLUETOOTH" }

            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "AUX" to "WIRED"

            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB" to productName.ifBlank { "USB" }

            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE" to "LINE"

            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EAR" to "EARPIECE"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPK" to "SPEAKER"
            else -> "UNK" to productName.ifBlank { "DEVICE" }
        }
    val keyLabel =
        display.second.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "device" }
    return AudioOutputDevice(
        key = "${display.first.lowercase()}:$keyLabel",
        label = display.second,
        type = display.first,
        // USB addresses contain a connection number and change on reconnect.
        volumeKey =
            "${display.first.lowercase()}:$keyLabel" +
                if (display.first == "BT" && address.isNotBlank()) ":$address" else "",
    )
}
