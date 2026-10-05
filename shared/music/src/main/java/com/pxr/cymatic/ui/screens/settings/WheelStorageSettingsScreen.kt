package com.pxr.cymatic.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pxr.cymatic.data.media.syncAudioFilesToDb
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.common.WheelReadingPage
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.storage.StatusBento
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WheelStorageSettingsScreen(
    directories: List<String>,
    hasPermission: Boolean,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    val scanAll by
        SettingsStore.scanAllMediaFlow.collectAsState(initial = SettingsStore.currentScanAllMedia)
    var scanning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var removeDirectory by remember { mutableStateOf<String?>(null) }
    var showStatus by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null)
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    scope.launch { SettingsStore.addScanDirectory(uri.toString()) }
                }
                    .onFailure { result = "Could not access that folder" }
        }
    BaseScreen(title = "Storage", onBackClick = { nav.popBackStack() }, modifier = modifier) {
        NavigationList(
            buildList {
                add(
                    NavigationItem(
                        "Scan status",
                        result ?: if (scanning) "Scanning" else "Last scan details",
                    ) {
                        showStatus = true
                    }
                )
                if (!hasPermission)
                    add(
                        NavigationItem(
                            "Grant permission",
                            "Storage access required",
                            onClick = onGrantPermission,
                        )
                    )
                add(
                    NavigationItem(
                        "Scan all media",
                        if (scanAll) "On" else "Off",
                        enabled = !scanning,
                        checked = scanAll,
                    ) {
                        scope.launch { SettingsStore.setScanAllMedia(!scanAll) }
                    }
                )
                add(
                    NavigationItem(
                        "Rescan",
                        if (scanning) "Scanning" else "Refresh local music",
                        enabled = !scanning,
                    ) {
                        scanning = true
                        scope.launch {
                            try {
                                val start = System.currentTimeMillis()
                                val files =
                                    withContext(Dispatchers.IO) {
                                        syncAudioFilesToDb(context, directories, scanAll)
                                    }
                                val end = System.currentTimeMillis()
                                SettingsStore.setLastScanTimeMs(end)
                                SettingsStore.setLastScanCount(files.size.toLong())
                                SettingsStore.setLastScanDurationMs(end - start)
                                result = "${files.size} files scanned"
                            } catch (e: Exception) {
                                result = "Scan failed: ${e.message}"
                            } finally {
                                scanning = false
                            }
                        }
                    }
                )
                add(NavigationItem("Add directory", enabled = !scanning) { picker.launch(null) })
                directories.forEach { directory ->
                    add(
                        NavigationItem(
                            Uri.parse(directory).lastPathSegment?.substringAfterLast(':')
                                ?: directory,
                            "Select to remove",
                            enabled = !scanning,
                            key = directory,
                        ) {
                            removeDirectory = directory
                        }
                    )
                }
            }
        )
    }
    if (showStatus) WheelReadingPage("Scan status", { showStatus = false }) { StatusBento() }
    removeDirectory?.let { directory ->
        WheelContextMenu(
            "Remove directory?",
            listOf(
                NavigationItem("Remove") {
                    scope.launch { SettingsStore.removeScanDirectory(directory) }
                    removeDirectory = null
                }
            ),
            { removeDirectory = null },
        )
    }
}
