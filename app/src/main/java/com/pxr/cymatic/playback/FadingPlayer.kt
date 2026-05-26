package com.pxr.cymatic.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.pxr.cymatic.data.store.SettingsStore

@UnstableApi
class FadingPlayer(
    private val player: Player,
    private val scope: CoroutineScope
) : ForwardingPlayer(player) {

    private var fadeJob: Job? = null
    private val fadeDurationMs = 300L
    private val fadeIntervalMs = 15L

    override fun play() {
        setPlayWhenReady(true)
    }

    override fun pause() {
        setPlayWhenReady(false)
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        fadeJob?.cancel()
        if (!SettingsStore.currentFadeEnabled) {
            player.volume = 1.0f
            super.setPlayWhenReady(playWhenReady)
            return
        }
        if (playWhenReady) {
            // Fade-in
            if (!player.playWhenReady) {
                player.volume = 0.0f
                super.setPlayWhenReady(true)
            }
            val startVolume = player.volume
            fadeJob = scope.launch(Dispatchers.Main) {
                val steps = (fadeDurationMs / fadeIntervalMs).toInt()
                for (i in 1..steps) {
                    delay(fadeIntervalMs)
                    val newVolume = startVolume + (1.0f - startVolume) * (i.toFloat() / steps)
                    player.volume = newVolume.coerceIn(0.0f, 1.0f)
                }
                player.volume = 1.0f
            }
        } else {
            // Fade-out
            if (player.playWhenReady) {
                val startVolume = player.volume
                fadeJob = scope.launch(Dispatchers.Main) {
                    val steps = (fadeDurationMs / fadeIntervalMs).toInt()
                    for (i in 1..steps) {
                        delay(fadeIntervalMs)
                        val newVolume = startVolume - startVolume * (i.toFloat() / steps)
                        player.volume = newVolume.coerceIn(0.0f, 1.0f)
                    }
                    player.volume = 0.0f
                    super.setPlayWhenReady(false)
                    player.volume = 1.0f
                }
            } else {
                super.setPlayWhenReady(false)
            }
        }
    }

    override fun release() {
        fadeJob?.cancel()
        super.release()
    }
}
