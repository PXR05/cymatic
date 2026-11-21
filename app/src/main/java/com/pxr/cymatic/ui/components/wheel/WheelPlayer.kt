package com.pxr.cymatic.ui.components.wheel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.data.store.currentInterfaceSettings
import com.pxr.cymatic.data.store.interfaceSettingsFlow
import com.pxr.cymatic.setup.MusicSetupScreen
import com.pxr.cymatic.setup.MusicSetupStep
import com.pxr.cymatic.setup.MusicSetupViewModel
import com.pxr.cymatic.ui.components.BatteryIndicator
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.LocalWheelOverlay
import com.pxr.cymatic.ui.components.common.WheelNavigation
import com.pxr.cymatic.ui.components.rememberBatteryState
import com.pxr.cymatic.ui.components.rememberMusicVolumeState
import com.pxr.cymatic.ui.components.screen.LocalScreenHeaderStatus
import com.pxr.cymatic.ui.locals.LocalInterfaceSettings
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.state.PlaybackState
import com.pxr.cymatic.ui.state.rememberPlaybackState

@Composable
fun WheelPlayer(
    onUpdateScreenBounds: (Rect?) -> Unit = {},
    onUpdateWheelBounds: (Offset?, Float?) -> Unit = { _, _ -> },
) {
    val setup: MusicSetupViewModel = viewModel()
    val setupState by setup.state.collectAsState()
    val settingUp = setupState.step != MusicSetupStep.COMPLETE
    val controller = LocalMediaController.current
    val playback = rememberPlaybackState(controller)
    val settings by
    SettingsStore.interfaceSettingsFlow.collectAsState(
        initial = SettingsStore.currentInterfaceSettings
    )
    WheelScreenAwake(settings.screenAwakeMode, playback.isPlaying)
    val battery = rememberBatteryState()
    val wheel = remember { WheelNavigation() }
    val state =
        rememberWheelPlayerState(LocalNavController.current, wheel, settings.coverVisibleByDefault)
    val volume = rememberMusicVolumeState(poll = state.controlsPlayback)
    val controls =
        WheelPlayerControls(
            state,
            controller,
            volume,
            playback.durationMs,
            setupBack = if (settingUp) setup::back else null,
        )
    LaunchedEffect(setupState.step) {
        if (setupState.step == MusicSetupStep.COMPLETE && setupState.openLibrary) {
            state.open(Screen.AllSongs.createRoute())
            setup.libraryOpened()
        }
    }

    SideEffect {
        wheel.onPlaybackRequested = state::nowPlaying
        wheel.onBackRequested = if (settingUp) setup::back else state::back
    }

    Surface(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalWheelNavigation provides wheel,
            LocalInterfaceSettings provides settings,
            LocalScreenHeaderStatus provides { BatteryIndicator(battery) },
        ) {
            WheelPlayerLayout(
                controls,
                overlay = {},
                onUpdateScreenBounds = onUpdateScreenBounds,
                onUpdateWheelBounds = onUpdateWheelBounds,
            ) {
                if (settingUp) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        MusicSetupScreen(setup, Modifier.fillMaxSize())
                        WheelOverlays(wheel)
                    }
                } else {
                    PlayerScreen(
                        state = state,
                        playback = playback,
                        volume = volume.volume,
                        maxVolume = volume.maxVolume,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    state: WheelPlayerState,
    playback: PlaybackState,
    volume: Int,
    maxVolume: Int,
    modifier: Modifier,
) {
    Box(modifier) {
        WheelBrowser(state, playback, volume, maxVolume) {
            if (state.blocksBrowserInput) BrowserInputBarrier()
            WheelOverlays(state.wheel)
        }
    }
}

@Composable
private fun WheelOverlays(wheel: WheelNavigation) {
    val owner = LocalLifecycleOwner.current
    val overlays = wheel.overlays.filter { it.owner == null || it.owner === owner }
    if (overlays.isEmpty()) return
    BrowserInputBarrier()
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            overlays.forEach { overlay ->
                key(overlay) {
                    CompositionLocalProvider(LocalWheelOverlay provides overlay) {
                        Box(Modifier.fillMaxSize()) { overlay.content() }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val pages = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            pages.lastOrNull()?.placeRelative(0, 0)
        }
    }
}

@Composable
private fun BrowserInputBarrier() {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    )
}
