package com.pxr.cymatic.ui.components.wheel

import androidx.media3.session.MediaController
import com.pxr.cymatic.ui.components.MusicVolumeState

internal class WheelPlayerControls(
    private val state: WheelPlayerState,
    private val controller: MediaController?,
    private val volume: MusicVolumeState,
    private val durationMs: Long?
) {
    fun rotate(steps: Int) {
        if (state.controlsPlayback) {
            volume.adjust(steps)
        } else {
            state.wheel.actions?.onRotate?.invoke(steps)
        }
    }

    fun press(button: WheelButton) {
        when (button) {
            WheelButton.MENU -> state.mainMenu()
            WheelButton.PREVIOUS -> if (state.controlsPlayback) {
                controller?.seekToPrevious()
            } else {
                state.back()
            }
            WheelButton.NEXT -> if (state.controlsPlayback) {
                controller?.seekToNext()
            } else {
                selectItem()
            }
            WheelButton.SELECT -> when {
                state.wheel.overlay != null -> selectItem()
                state.isNowPlaying -> state.browse()
                state.panel == WheelPanel.QUICK_SETTINGS -> state.back()
                else -> selectItem()
            }
            WheelButton.PLAY -> if (state.isNowPlaying) togglePlay() else state.nowPlaying()
        }
    }

    fun hold(button: WheelButton) {
        when (button) {
            WheelButton.MENU -> if (state.wheel.overlay == null) {
                if (state.panel == WheelPanel.QUICK_SETTINGS) state.back() else state.quickSettings()
            }
            WheelButton.SELECT -> if (state.controlsPlayback) {
                state.trackActions()
            } else {
                state.wheel.actions?.onContext?.invoke()
            }
            WheelButton.PREVIOUS, WheelButton.NEXT -> if (state.controlsPlayback) {
                seekBy(if (button == WheelButton.NEXT) 3000L else -3000L)
            }
            WheelButton.PLAY -> {
                controller?.stop()
                state.showBrowser()
            }
        }
    }

    private fun selectItem() {
        state.wheel.actions?.onSelect?.invoke()
    }

    private fun togglePlay() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.prepare()
                it.play()
            }
        }
    }

    private fun seekBy(deltaMs: Long) {
        controller?.let {
            it.seekTo((it.currentPosition + deltaMs).coerceIn(0L, durationMs ?: Long.MAX_VALUE))
        }
    }
}
