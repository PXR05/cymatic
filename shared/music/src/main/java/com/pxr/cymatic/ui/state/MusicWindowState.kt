package com.pxr.cymatic.ui.state

import android.content.res.Configuration
import android.view.Window
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.locals.LocalMediaController

data class MusicWindowState(val hasPlayback: Boolean, val isDocked: Boolean, val isExpanded: Boolean)

@Composable
fun rememberMusicWindowState(window: Window): MusicWindowState {
    val mediaController = LocalMediaController.current
    val locked by SettingsStore.lockedFlow.collectAsState(initial = SettingsStore.currentLocked)
    val playbackState = rememberPlaybackState(mediaController)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hasPlayback = playbackState.currentMediaId != null && playbackState.totalTracks > 0
    val isDocked = isLandscape && hasPlayback
    val hideSystemBars = locked || isDocked
    LaunchedEffect(hideSystemBars) {
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        if (hideSystemBars) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    return MusicWindowState(hasPlayback, isDocked, hideSystemBars)
}
