package com.pxr.cymatic.ui.components.storage

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.store.SettingsStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatusBento(
    modifier: Modifier = Modifier,
) {
    val fontFamily = FontFamily(Font(R.font.pixel))
    val lastScanTimeMs by SettingsStore.lastScanTimeMsFlow.collectAsState(initial = SettingsStore.currentLastScanTimeMs)
    val lastScanCount by SettingsStore.lastScanCountFlow.collectAsState(initial = SettingsStore.currentLastScanCount)
    val lastScanDurationMs by SettingsStore.lastScanDurationMsFlow.collectAsState(initial = SettingsStore.currentLastScanDurationMs)

    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.secondary)
            .padding(16.dp)
    ) {
        StatusRow(
            label = "Last Scan",
            value = formatScanTime(lastScanTimeMs),
            fontFamily = fontFamily
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusRow(
            label = "Files Scanned",
            value = formatScanCount(lastScanTimeMs, lastScanCount),
            fontFamily = fontFamily
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusRow(
            label = "Scan Duration",
            value = formatScanDuration(lastScanTimeMs, lastScanDurationMs),
            fontFamily = fontFamily
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String, fontFamily: FontFamily) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = fontFamily, fontSize = 14.sp),
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = value,
            style = TextStyle(fontFamily = fontFamily, fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun formatScanTime(timeMs: Long): String {
    if (timeMs <= 0L) return "Never"
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return Instant.ofEpochMilli(timeMs)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun formatScanCount(timeMs: Long, count: Long): String {
    return if (timeMs <= 0L) {
        "N/A"
    } else {
        "$count files"
    }
}

private fun formatScanDuration(timeMs: Long, durationMs: Long): String {
    if (timeMs <= 0L) return "N/A"
    return if (durationMs < 1000L) {
        "$durationMs ms"
    } else {
        String.format(Locale.US, "%.1f s", durationMs / 1000.0)
    }
}
