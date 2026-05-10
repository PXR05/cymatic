package com.pxr.cymatic.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager

data class AudioOutputDevice(
    val key: String,
    val label: String,
    val type: String
)

fun resolveActiveOutput(audioManager: AudioManager): AudioOutputDevice {
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    val preferredTypes = listOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
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

    val active = preferredTypes
        .firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
        ?: devices.firstOrNull()

    return active?.toOutputDevice() ?: AudioOutputDevice(
        key = "unknown",
        label = "?",
        type = "?"
    )
}

private fun AudioDeviceInfo.toOutputDevice(): AudioOutputDevice {
    val productName = productName?.toString()?.trim().orEmpty()
    val display = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
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
    val keyLabel = display.second
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "device" }
    return AudioOutputDevice(
        key = "${display.first.lowercase()}:$keyLabel",
        label = display.second,
        type = display.first
    )
}
