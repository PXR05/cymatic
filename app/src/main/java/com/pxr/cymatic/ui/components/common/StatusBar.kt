package com.pxr.cymatic.ui.components.common

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R

@SuppressLint("DefaultLocale")
@Composable
fun StatusBar(
    context: Context = LocalContext.current,
) {
    val fontFamily = FontFamily(Font(R.font.pixel))
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var maxAudioVolume by remember {
        mutableIntStateOf(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    }
    var isMuted by remember {
        mutableStateOf(audioManager.isStreamMute(AudioManager.STREAM_MUSIC))
    }
    var audioVolume by remember {
        mutableIntStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        )
    }
    var devices by remember {
        mutableStateOf(
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        )
    }
    var activeDevice by remember {
        mutableStateOf(resolveActiveOutput(audioManager))
    }

    DisposableEffect(context) {
        val volumeFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        val muteFilter = IntentFilter("android.media.STREAM_MUTE_CHANGED_ACTION")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                audioVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
            }
        }
        context.registerReceiver(receiver, volumeFilter)
        context.registerReceiver(receiver, muteFilter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                activeDevice = resolveActiveOutput(audioManager)
                devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
                maxAudioVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                activeDevice = resolveActiveOutput(audioManager)
                devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
                maxAudioVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        activeDevice = resolveActiveOutput(audioManager)

        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    var showOutputSwitcher by remember { mutableStateOf(false) }

    OutputSwitcherDialog(
        devices = devices,
        defaultDevice = activeDevice.device,
        showDialog = showOutputSwitcher,
        onDismissRequest = { showOutputSwitcher = false },
        onConfirmation = { device ->
            if (device != null) {
                val res = audioManager.setCommunicationDevice(device)
                if (res) {
                    activeDevice = resolveActiveOutput(audioManager)
                } else {
                    Toast.makeText(
                        context,
                        "Failed to switch output device",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "[${activeDevice.type}] ${activeDevice.label}",
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = fontFamily,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(
                    onClick = {
                        showOutputSwitcher = true
                    },
                    indication = null,
                    interactionSource = null
                )
        )

        Text(
            text = if (isMuted) "MUTED" else "${(audioVolume / maxAudioVolume.toFloat() * 100).toInt()}%",
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = fontFamily,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(
                    onClick = {
                        if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_UNMUTE,
                                AudioManager.FLAG_SHOW_UI
                            )
                        } else {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_MUTE,
                                AudioManager.FLAG_SHOW_UI
                            )
                        }
                    },
                    indication = null,
                    interactionSource = null
                )
        )
    }
}

private data class DeviceDisplay(
    val label: String,
    val type: String,
    var device: AudioDeviceInfo? = null
)

private fun resolveActiveOutput(audioManager: AudioManager): DeviceDisplay {
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

    return if (active == null) {
        DeviceDisplay(label = "?", type = "?")
    } else {
        deviceToDisplay(active)
    }
}

private fun deviceToDisplay(device: AudioDeviceInfo): DeviceDisplay {
    val productName = device.productName?.toString()?.trim().orEmpty()
    var label = productName.ifBlank { "DEVICE" }
    var type = "UNK"
    when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
            label = productName.ifBlank { "BLUETOOTH" }
            type = "BT"
        }

        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
            label = "WIRED"
            type = "AUX"
        }

        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
            label = productName.ifBlank { "USB" }
            type = "USB"
        }

        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> {
            label = "LINE"
            type = "LINE"
        }

        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {
            label = "EARPIECE"
            type = "EAR"
        }

        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
            label = "SPEAKER"
            type = "SPK"
        }

        else -> DeviceDisplay(
            label = productName.ifBlank { "DEVICE" },
            type = "UNK"
        )
    }
    return DeviceDisplay(label, type, device)
}

@Preview(showBackground = true)
@Composable
fun StatusBarPreview() {
    StatusBar()
}

