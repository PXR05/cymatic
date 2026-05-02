package com.pxr.cymatic.ui.components

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
    val maxAudioVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }
    var audioVolume by remember {
        mutableIntStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        )
    }
    var deviceDisplay by remember {
        mutableStateOf(resolveActiveOutput(audioManager))
    }

    DisposableEffect(context) {
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                audioVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                deviceDisplay = resolveActiveOutput(audioManager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                deviceDisplay = resolveActiveOutput(audioManager)
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        deviceDisplay = resolveActiveOutput(audioManager)

        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                16.dp,
                8.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${deviceDisplay.label} (${deviceDisplay.type})",
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = fontFamily,
            fontSize = 14.sp
        )

        Text(
            text = "${(audioVolume / maxAudioVolume.toFloat() * 100).toInt()}%",
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = fontFamily,
            fontSize = 14.sp
        )
    }
}

private data class DeviceDisplay(
    val label: String,
    val type: String,
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
    return when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> DeviceDisplay(
            label = productName.ifBlank { "BLUETOOTH" },
            type = "BT",
        )

        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> DeviceDisplay(
            label = "WIRED",
            type = "AUX"
        )

        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> DeviceDisplay(
            label = productName.ifBlank { "USB" },
            type = "USB"
        )

        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> DeviceDisplay(
            label = "LINE",
            type = "LINE"
        )

        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> DeviceDisplay(
            label = "EARPIECE",
            type = "EAR"
        )

        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> DeviceDisplay(
            label = "SPEAKER",
            type = "SPK"
        )

        else -> DeviceDisplay(
            label = productName.ifBlank { "DEVICE" },
            type = "UNK"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatusBarPreview() {
    StatusBar()
}