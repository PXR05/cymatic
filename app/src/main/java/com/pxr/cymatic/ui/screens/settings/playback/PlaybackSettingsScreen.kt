package com.pxr.cymatic.ui.screens.settings.playback

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pxr.cymatic.audio.resolveActiveOutput
import com.pxr.cymatic.data.store.EqSettingsStore
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun PlaybackSettingsScreen(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsAllowed = it
        }
    var notificationAsked by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                notificationsAllowed =
                    Build.VERSION.SDK_INT < 33 ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed && !notificationAsked) {
            notificationAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    context.packageName,
                )
        )
    }

    val resumeOnBt by
    SettingsStore.resumeOnBluetoothReconnectFlow.collectAsState(
        initial = SettingsStore.currentResumeOnBluetoothReconnect
    )
    val fadeEnabled by
    SettingsStore.fadeEnabledFlow.collectAsState(initial = SettingsStore.currentFadeEnabled)

    val audioManager =
        remember(context) {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
    var activeDevice by remember {
        mutableStateOf(resolveActiveOutput(audioManager))
    }

    DisposableEffect(audioManager) {
        val callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    activeDevice = resolveActiveOutput(audioManager)
                    scope.launch { EqSettingsStore.setActiveAudioDevice(activeDevice.key) }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    activeDevice = resolveActiveOutput(audioManager)
                    scope.launch { EqSettingsStore.setActiveAudioDevice(activeDevice.key) }
                }
            }

        audioManager.registerAudioDeviceCallback(callback, null)
        activeDevice = resolveActiveOutput(audioManager)
        scope.launch { EqSettingsStore.setActiveAudioDevice(activeDevice.key) }

        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    fun openOutputSwitcherFallback() {
        listOf(
            Settings.Panel.ACTION_VOLUME,
            Settings.ACTION_SOUND_SETTINGS,
        )
            .forEach {
                try {
                    val intent = Intent(it)
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    Log.w(
                        "PlaybackSettings",
                        "Failed to open fallback activity for output switcher: $it",
                        e,
                    )
                }
            }
    }

    fun openOutputSwitcher() {
        val action = "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG"
        val sysPackage = "com.android.systemui"
        val receiver = "com.android.systemui.media.dialog.MediaOutputDialogReceiver"

        try {
            val intent =
                Intent(action).apply {
                    component = ComponentName(sysPackage, receiver)
                }
            context.sendBroadcast(intent)
        } catch (e: SecurityException) {
            Log.w(
                "PlaybackSettings",
                "broadcast blocked by system restriction, trying fallbacks",
                e,
            )
            openOutputSwitcherFallback()
        } catch (e: Exception) {
            Log.e(
                "PlaybackSettings",
                "broadcast failed: ${e::class.simpleName}: ${e.message}",
                e,
            )
        }
    }

    BaseScreen(
        title = "Playback",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
    ) {
        NavigationList(
            listOf(
                NavigationItem("Audio output", activeDevice.label) { openOutputSwitcher() },
                NavigationItem("USB audio") {
                    navController.navigate(Screen.UsbSettings.route)
                },
                NavigationItem(
                    "App notifications",
                    if (notificationsAllowed) "Allowed" else "Off",
                ) {
                    openNotificationSettings()
                },
                NavigationItem(
                    "Auto-resume",
                    if (resumeOnBt) "On" else "Off",
                    checked = resumeOnBt,
                ) {
                    scope.launch { SettingsStore.setResumeOnBluetoothReconnect(!resumeOnBt) }
                },
                NavigationItem(
                    "Fade in / out",
                    if (fadeEnabled) "On" else "Off",
                    checked = fadeEnabled,
                ) {
                    scope.launch { SettingsStore.setFadeEnabled(!fadeEnabled) }
                },
            )
        )
    }
}
