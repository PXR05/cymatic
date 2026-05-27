package com.pxr.cymatic.ui.screens.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.audio.resolveActiveOutput
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.launch

@Composable
fun PlaybackSettingsScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val resumeOnBt by SettingsStore.resumeOnBluetoothReconnectFlow.collectAsState(
        initial = SettingsStore.currentResumeOnBluetoothReconnect
    )
    val fadeEnabled by SettingsStore.fadeEnabledFlow.collectAsState(
        initial = SettingsStore.currentFadeEnabled
    )

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var activeDevice by remember {
        mutableStateOf(resolveActiveOutput(audioManager))
    }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                activeDevice = resolveActiveOutput(audioManager)
                scope.launch { SettingsStore.setActiveAudioDevice(activeDevice.key) }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                activeDevice = resolveActiveOutput(audioManager)
                scope.launch { SettingsStore.setActiveAudioDevice(activeDevice.key) }
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
                    "PlaybackSettings",
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
                "PlaybackSettings",
                "broadcast blocked by system restriction, trying fallbacks",
                e
            )
            openOutputSwitcherFallback()
        } catch (e: Exception) {
            Log.e(
                "PlaybackSettings",
                "broadcast failed: ${e::class.simpleName}: ${e.message}",
                e
            )
        }
    }

    BaseScreen(
        title = "Playback",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(24.dp, 16.dp)
        ) {
            Text(
                text = "Audio Output",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsActionRow(
                title = activeDevice.label,
                subtitle = "Output Type: ${activeDevice.type}",
                actionLabel = "CHANGE",
                onActionClick = { openOutputSwitcher() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Bluetooth",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggleRow(
                title = "Auto-resume",
                subtitle = "Resume on Bluetooth connect",
                checked = resumeOnBt,
                onCheckedChange = { value ->
                    scope.launch {
                        SettingsStore.setResumeOnBluetoothReconnect(value)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Audio Transitions",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggleRow(
                title = "Fade In / Out",
                subtitle = "Smoothly fade audio",
                checked = fadeEnabled,
                onCheckedChange = { value ->
                    scope.launch {
                        SettingsStore.setFadeEnabled(value)
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = if (checked) "I" else "O",
            fontSize = 16.sp,
            color = if (checked) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .background(if (checked) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                .border(1.dp, MaterialTheme.colorScheme.onBackground)
                .clickable(
                    onClick = { onCheckedChange(!checked) },
                    indication = null,
                    interactionSource = null
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = actionLabel,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.onBackground)
                .clickable(
                    onClick = onActionClick,
                    indication = null,
                    interactionSource = null
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
