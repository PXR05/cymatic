package com.pxr.cymatic.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.audio.usb.UsbVolumeState

internal class MusicVolumeState(private val audio: AudioManager) {
    var volume by mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        private set
    val maxVolume: Int get() =
            if (UsbPlaybackState.active.value) 100
            else audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun refresh() {
        volume =
            if (UsbPlaybackState.active.value) UsbVolumeState.requestedPercent.value
            else audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun adjust(steps: Int) {
        if (UsbPlaybackState.active.value) {
            UsbVolumeState.adjust(steps * 2)
            refresh()
            return
        }
        val next = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, maxVolume)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        volume = next
    }
}

@Composable
internal fun rememberMusicVolumeState(poll: Boolean): MusicVolumeState {
    val context = LocalContext.current
    val state = remember(context) {
        MusicVolumeState(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    }
    LaunchedEffect(poll, state) {
        if (poll) {
            while (isActive) {
                state.refresh()
                delay(500L)
            }
        }
    }
    return state
}
