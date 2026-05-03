package com.pxr.cymatic.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.core.net.toUri
import com.pxr.cymatic.R
import com.pxr.cymatic.data.store.SettingsStore

@Composable
fun DirectoryBox(
    directories: List<String>,
    onRemove: (String) -> Unit,
    onToggleScanAll: (Boolean) -> Unit
) {
    val scanAllMedia by SettingsStore.scanAllMediaFlow.collectAsState(initial = true)

    Column(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.secondary)
    ) {
        DirectoryRow(
            title = "Scan all media",
            subtitle = "Include all audio files on device",
            actionLabel = if (scanAllMedia) "Disable" else "Enable",
            onAction = { onToggleScanAll(!scanAllMedia) },
        )

        DirectoryDivider()

        if (directories.isEmpty()) {
            DirectoryRow(
                title = "No directories selected",
                subtitle = "Use + to choose folders",
                actionLabel = "",
                onAction = null,
            )
        } else {
            directories.forEachIndexed { index, uri ->
                if (index > 0) {
                    DirectoryDivider()
                }
                DirectoryRow(
                    title = formatDirectoryLabel(uri),
                    subtitle = uri.replace("content://", ""),
                    actionLabel = "Remove",
                    onAction = { onRemove(uri) },
                )
            }
        }
    }
}

@Composable
private fun DirectoryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.secondary)
    )
}

@Composable
private fun DirectoryRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: (() -> Unit)?,
) {
    val fontFamily = FontFamily(Font(R.font.pixel))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(fontFamily = fontFamily, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = TextStyle(fontFamily = fontFamily, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (onAction != null) {
            Text(
                text = actionLabel,
                style = TextStyle(fontFamily = fontFamily, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .border(1.dp, MaterialTheme.colorScheme.secondary)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

private fun formatDirectoryLabel(uriString: String): String {
    val uri = uriString.toUri()
    val segment = uri.lastPathSegment ?: uriString
    val cleaned = segment.substringAfterLast(':', segment)
    return cleaned.ifBlank { segment }
}
