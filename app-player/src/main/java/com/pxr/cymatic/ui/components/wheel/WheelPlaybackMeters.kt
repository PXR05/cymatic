package com.pxr.cymatic.ui.components.wheel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.ui.components.PixelMeter
import com.pxr.cymatic.ui.state.PlaybackState
import kotlin.math.roundToLong
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.audio.usb.UsbVolumeState
import java.util.Locale

@Composable
internal fun WheelPlaybackMeters(
    playback: PlaybackState,
    volume: Int,
    maxVolume: Int,
    gap: Dp,
    onSeek: (Long) -> Unit
) {
    val directUsb by UsbPlaybackState.active.collectAsState()
    val usbVolume by UsbVolumeState.level.collectAsState()
    val requestedVolume by UsbVolumeState.requestedPercent.collectAsState()
    val duration = (playback.durationMs ?: 0L).coerceAtLeast(0L)
    val position = playback.currentPositionMs.coerceIn(0L, duration)
    val volumeLimit = maxVolume.coerceAtLeast(1)
    Column(
        Modifier.fillMaxWidth(),
        Arrangement.spacedBy(gap * 2)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "VOL",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.width(52.dp)
            )
            PixelMeter(
                if (directUsb) (usbVolume.percent ?: requestedVolume) / 100f
                else volume.toFloat() / volumeLimit,
                if (directUsb) usbVolume.message else "Volume",
                Modifier.weight(1f)
            )
            Text(
                if (directUsb)
                    when {
                        usbVolume.muted -> "MUTE"
                        usbVolume.decibels != null ->
                            String.format(Locale.ROOT, "%.0f dB", usbVolume.decibels)
                        else -> "$requestedVolume%"
                    }
                else "${volume * 100 / volumeLimit}%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(52.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                playbackTime(position),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.widthIn(min = 52.dp)
            )
            PixelMeter(
                if (duration > 0) position.toFloat() / duration else 0f,
                "Playback position",
                Modifier.weight(1f),
                onFractionChange = if (duration > 0) {
                    { fraction -> onSeek((fraction * duration).roundToLong()) }
                } else {
                    null
                }
            )
            Text(
                playbackTime(duration),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 52.dp)
            )
        }
    }
}

private fun playbackTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0L) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
