package com.pxr.cymatic.ui.components.storage

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.pxr.cymatic.R
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.primitives.PixelConfirmDialog
import kotlinx.coroutines.launch

@SuppressLint("WrongConstant")
@Composable
fun DirectoryBox(
    directories: List<String>,
    onRemove: (String) -> Unit,
    onToggleScanAll: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontFamily = FontFamily(Font(R.font.pixel))
    val scanAllMedia by SettingsStore.scanAllMediaFlow.collectAsState(initial = SettingsStore.currentScanAllMedia)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteUri by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                }
                scope.launch {
                    SettingsStore.addScanDirectory(uri.toString())
                }
            }
        }
    )

    if (showDeleteConfirm && pendingDeleteUri != null) {
        PixelConfirmDialog(
            fontFamily = fontFamily,
            message = "Remove \"${formatDirectoryLabel(pendingDeleteUri ?: "")}\"?",
            onConfirm = {
                onRemove(pendingDeleteUri!!)
                pendingDeleteUri = null
                showDeleteConfirm = false
            },
            onDismiss = {
                pendingDeleteUri = null
                showDeleteConfirm = false
            }
        )
    }

    Column(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.secondary)
    ) {
        DirectoryRow(
            title = "Scan all media",
            subtitle = "Include all audio files on device",
            actionLabel = if (scanAllMedia) "I" else "O",
            isActive = scanAllMedia,
            onAction = { onToggleScanAll(!scanAllMedia) },
        )

        DirectoryDivider()

        directories.forEachIndexed { index, uri ->
            if (index > 0) {
                DirectoryDivider()
            }
            DirectoryRow(
                title = formatDirectoryLabel(uri),
                subtitle = uri.replace("content://com.android.externalstorage.", ""),
                actionLabel = "X",
                onAction = {
                    pendingDeleteUri = uri
                    showDeleteConfirm = true
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .border(1.dp, MaterialTheme.colorScheme.secondary)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(
                    onClick = { folderPicker.launch(null) },
                    indication = null,
                    interactionSource = null
                ),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "+ Add Directory",
                fontFamily = fontFamily,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary,
            )
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
    isActive: Boolean = false,
    onAction: (() -> Unit)?,
) {
    val fontFamily = FontFamily(Font(R.font.pixel))

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (actionLabel.isNotEmpty()) {
            Text(
                text = actionLabel,
                fontFamily = fontFamily,
                fontSize = 16.sp,
                color = if (isActive) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .background(if (isActive) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                    .padding(28.dp, 20.dp)
                    .clickable(
                        onClick = {
                            if (onAction != null) onAction()
                        },
                        indication = null,
                        interactionSource = null
                    )
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(64.dp)
                .border(1.dp, MaterialTheme.colorScheme.secondary)
        )

        Column(modifier = Modifier
            .weight(1f)
            .padding(horizontal = 16.dp)) {
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
    }
}

private fun formatDirectoryLabel(uriString: String): String {
    val uri = uriString.toUri()
    val segment = uri.lastPathSegment ?: uriString
    val cleaned = segment.substringAfterLast(':', segment)
    return cleaned.ifBlank { segment }
}
