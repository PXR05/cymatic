package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pxr.cymatic.R
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun SongInfoDialog(
    modifier: Modifier = Modifier,
    showDialog: Boolean = false,
    mediaId: Long,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    val fontFamily = FontFamily(Font(R.font.pixel))
    val dialogWidth =
        with(window) { (containerSize.width * 0.8f) }
    val dialogWidthDp = with(density) { dialogWidth.toDp() }

    if (!showDialog) return

    var audioFile by remember { mutableStateOf<AudioFile?>(null) }

    LaunchedEffect(mediaId) {
        audioFile = withContext(Dispatchers.IO) {
            AudioRepository.getInstance(context).getAudioByIds(listOf(mediaId)).firstOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = modifier
                .border(1.dp, MaterialTheme.colorScheme.secondary)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .width(dialogWidthDp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Song Information",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontFamily = fontFamily,
            )

            if (audioFile != null) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InfoItem(
                        label = "Title",
                        value = audioFile?.metadata?.title ?: "Unknown",
                        fontFamily = fontFamily
                    )
                    InfoItem(
                        label = "Artist",
                        value = audioFile?.metadata?.artist ?: "Unknown",
                        fontFamily = fontFamily
                    )
                    InfoItem(
                        label = "Album",
                        value = audioFile?.metadata?.album ?: "Unknown",
                        fontFamily = fontFamily
                    )
                    InfoItem(
                        label = "Duration",
                        value = audioFile?.metadata?.duration?.let { formatDuration(it) }
                            ?: "Unknown",
                        fontFamily = fontFamily
                    )
                    InfoItem(
                        label = "Format",
                        value = audioFile?.metadata?.format ?: "Unknown",
                        fontFamily = fontFamily
                    )
                    InfoItem(
                        label = "Bit Rate",
                        value = audioFile?.metadata?.bitRate?.let { "${it / 1000} kbps" }
                            ?: "Unknown",
                        fontFamily = fontFamily
                    )
                    InfoItem(
                        label = "Sample Rate",
                        value = audioFile?.metadata?.sampleRate?.let { "${it / 1000} kHz" }
                            ?: "Unknown",
                        fontFamily = fontFamily
                    )
                    InfoItem(
                        label = "File Size",
                        value = audioFile?.let { formatFileSize(it.size.toLong()) } ?: "Unknown",
                        fontFamily = fontFamily
                    )
                }
            } else {
                Text(
                    text = "Loading...",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontFamily = fontFamily,
                )
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    fontFamily: FontFamily,
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 14.sp,
            fontFamily = fontFamily,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontFamily = fontFamily,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
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
