package com.pxr.cymatic.ui.components.storage

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.media.syncAudioFilesToDb
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("WrongConstant")
@Composable
fun DirectoryActions(
    modifier: Modifier = Modifier,
    directories: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRescanning by remember { mutableStateOf(false) }
    val scanAllMedia by SettingsStore.scanAllMediaFlow.collectAsState(initial = SettingsStore.currentScanAllMedia)
    val fontFamily = FontFamily(Font(R.font.pixel))

    fun rescanDirectories() {
        if (isRescanning) return
        scope.launch {
            isRescanning = true
            val start = System.currentTimeMillis()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    syncAudioFilesToDb(
                        context,
                        directories,
                        scanAllMedia
                    )
                }
            }
            val end = System.currentTimeMillis()
            if (result.isSuccess) {
                val files = result.getOrThrow()
                SettingsStore.setLastScanTimeMs(end)
                SettingsStore.setLastScanCount(files.size.toLong())
                SettingsStore.setLastScanDurationMs(end - start)
            }
            isRescanning = false
        }
    }

    Text(
        text = "Rescan",
        fontFamily = fontFamily,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        color = if (!isRescanning) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(
                enabled = !isRescanning,
                onClick = { rescanDirectories() }
            ),
    )
}