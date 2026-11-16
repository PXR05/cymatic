package com.pxr.cymatic.ui.screens.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.playback.formatSampleRate
import com.pxr.cymatic.ui.components.common.ReadingScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SongInfoScreen(mediaId: Long) {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val repository = remember(context) { AudioRepository.getInstance(context) }
    val cached = remember(repository, mediaId) {
        repository.getCachedAudio()?.firstOrNull { it.id == mediaId }
    }
    var audioFile by remember(mediaId) { mutableStateOf(cached) }
    var loaded by remember(mediaId) { mutableStateOf(cached != null) }

    LaunchedEffect(mediaId) {
        audioFile =
            withContext(Dispatchers.IO) {
                repository.getAudioByIds(listOf(mediaId)).firstOrNull()
            }
        loaded = true
    }

    ReadingScreen("Song Information", { nav.popBackStack() }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loaded && audioFile == null) Text("This song is no longer available.")
            else SongInformation(audioFile)
        }
    }
}

@Composable
private fun SongInformation(audio: AudioFile?) {
    if (audio == null) {
        Text("Loading...", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
        return
    }
    val metadata = audio.metadata
    InfoItem("Title", metadata.title ?: "Unknown")
    InfoItem("Artist", metadata.artist ?: "Unknown")
    InfoItem("Album", metadata.album ?: "Unknown")
    InfoItem("Duration", metadata.duration?.let(::formatDuration) ?: "Unknown")
    InfoItem("Format", metadata.format ?: "Unknown")
    InfoItem("Bit Rate", metadata.bitRate?.let { "${it / 1000} kbps" } ?: "Unknown")
    InfoItem(
        "Sample Rate",
        metadata.sampleRate?.takeIf { it > 0 }?.let(::formatSampleRate) ?: "Unknown",
    )
    InfoItem("File Size", formatFileSize(audio.size.toLong()))
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
) {
    val cjkRegex = Regex("[\\u4E00-\\u9FFF|\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]")
    val letterSpacing = if (value.contains(cjkRegex)) 1.5.sp else 0.sp

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 14.sp,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            letterSpacing = letterSpacing,
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024
    val sizes = arrayOf("B", "KB", "MB", "GB")
    val i = (ln(bytes.toDouble()) / ln(k.toDouble())).toInt()
    return String.format(Locale.US, "%.2f %s", bytes / k.toDouble().pow(i.toDouble()), sizes[i])
}
