package com.pxr.cymatic.ui.components.wheel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.pxr.cymatic.data.store.ScreenAwakeMode

@Composable
internal fun WheelScreenAwake(mode: ScreenAwakeMode, isPlaying: Boolean) {
    val view = LocalView.current
    val enabled = when (mode) {
        ScreenAwakeMode.NEVER -> false
        ScreenAwakeMode.DURING_PLAYBACK -> isPlaying
        ScreenAwakeMode.ALWAYS -> true
    }
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = previous }
    }
}
