package com.pxr.cymatic.ui.components.common

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.audio.resolveActiveOutput
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.launch

@SuppressLint("DefaultLocale")
@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
) {
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val scope = rememberCoroutineScope()
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
                scope.launch { SettingsStore.setActiveAudioDevice(activeDevice.key) }
                maxAudioVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                activeDevice = resolveActiveOutput(audioManager)
                scope.launch { SettingsStore.setActiveAudioDevice(activeDevice.key) }
                maxAudioVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        activeDevice = resolveActiveOutput(audioManager)
        scope.launch { SettingsStore.setActiveAudioDevice(activeDevice.key) }

        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    fun openOutputSwitcherFallback() {
        listOf(
            Settings.Panel.ACTION_VOLUME,
            Settings.ACTION_SOUND_SETTINGS
        ).forEach {
            try {
                val intent = Intent(it)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(
                    "Status Bar",
                    "Failed to open fallback activity for output switcher: $it",
                    e
                )
            }
        }
    }

    fun openOutputSwitcher() {
        val action =
            "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG"
        val sysPackage = "com.android.systemui"
        val receiver =
            "com.android.systemui.media.dialog.MediaOutputDialogReceiver"

        try {
            val intent = Intent(action).apply {
                component = ComponentName(sysPackage, receiver)
            }
            context.sendBroadcast(intent)
        } catch (e: SecurityException) {
            Log.w(
                "Status Bar",
                "broadcast blocked by system restriction, trying fallbacks",
                e
            )
            openOutputSwitcherFallback()
        } catch (e: Exception) {
            Log.e(
                "Status Bar",
                "broadcast failed: ${e::class.simpleName}: ${e.message}",
                e
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "[${activeDevice.type}] ${activeDevice.label}",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable(
                    onClick = { openOutputSwitcher() },
                    indication = null,
                    interactionSource = null
                )
        )

        Box(
            modifier = Modifier.weight(1f)
        )

        Text(
            text = if (isMuted) "MUTED" else "${(audioVolume / maxAudioVolume.toFloat() * 100).toInt()}%",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable(
                    onClick = {
                        if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_UNMUTE,
                                AudioManager.FLAG_SHOW_UI
                            )
                            if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC) || audioManager.getStreamVolume(
                                    AudioManager.STREAM_MUSIC
                                 ) == 0
                            ) {
                                Toast.makeText(
                                    context,
                                    "Failed to unmute audio. Please try raising the volume manually.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
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

@Preview(showBackground = true)
@Composable
fun StatusBarPreview() {
    StatusBar()
}
