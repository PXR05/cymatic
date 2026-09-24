package com.pxr.cymatic.ui.screens.settings

import android.content.Intent
import android.provider.DocumentsContract
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.sync.LibrarySyncJobService
import com.pxr.cymatic.sync.LibrarySyncManager
import com.pxr.cymatic.sync.SyncCredentialStore
import com.pxr.cymatic.sync.SyncCatalogStore
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.RemotePlaylist
import com.pxr.cymatic.sync.SyncLayout
import com.pxr.cymatic.sync.SyncNetwork
import com.pxr.cymatic.sync.SyncProgress
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton
import com.pxr.cymatic.ui.components.primitives.CymaticDialogDivider
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenu
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenuItem
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

private val syncIntervals = linkedMapOf(
    1L to "Every hour",
    6L to "Every 6 hours",
    12L to "Every 12 hours",
    24L to "Daily",
    72L to "Every 3 days",
    168L to "Weekly",
)

@Composable
fun LibrarySyncSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(SettingsStore.currentSyncUrl) }
    var username by remember { mutableStateOf(SettingsStore.currentSyncUsername) }
    var directory by remember { mutableStateOf(SettingsStore.currentSyncDirectory) }
    var network by remember { mutableStateOf(SyncNetwork.from(SettingsStore.currentSyncNetwork)) }
    var layout by remember { mutableStateOf(SyncLayout.from(SettingsStore.currentSyncLayout)) }
    var interval by remember { mutableStateOf(SettingsStore.currentSyncIntervalHours) }
    var contentMode by remember { mutableStateOf(SyncContentMode.from(SettingsStore.currentSyncContentMode)) }
    var selectedPlaylists by remember { mutableStateOf(SettingsStore.currentSyncSelectedPlaylists) }
    var availablePlaylists by remember { mutableStateOf(emptyList<RemotePlaylist>()) }
    var hasPassword by remember { mutableStateOf(SyncCredentialStore.hasPassword(context)) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val progress by LibrarySyncManager.progress.collectAsState()
    val lastTime by SettingsStore.syncLastTimeFlow.collectAsState(initial = 0L)
    val lastResult by SettingsStore.syncLastResultFlow.collectAsState(initial = "Not synced yet")
    val isRunning = progress is SyncProgress.Preparing ||
        progress is SyncProgress.Downloading || progress is SyncProgress.Cancelling

    LaunchedEffect(Unit) {
        url = SettingsStore.getSyncUrl()
        username = SettingsStore.getSyncUsername()
        directory = SettingsStore.getSyncDirectory()
        network = SyncNetwork.from(SettingsStore.getSyncNetwork())
        layout = SyncLayout.from(SettingsStore.getSyncLayout())
        interval = SettingsStore.getSyncIntervalHours()
        contentMode = SyncContentMode.from(SettingsStore.getSyncContentMode())
        selectedPlaylists = SettingsStore.getSyncSelectedPlaylists()
        availablePlaylists = SyncCatalogStore.read(context)?.playlists.orEmpty()
        hasPassword = SyncCredentialStore.hasPassword(context)
    }

    LaunchedEffect(progress) {
        if (progress is SyncProgress.Complete) {
            availablePlaylists = SyncCatalogStore.read(context)?.playlists.orEmpty()
        }
    }

    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            directory = uri.toString()
            scope.launch {
                SettingsStore.setSyncDirectory(directory)
                LibrarySyncJobService.reschedule(context)
            }
        }.onFailure { message = "Could not access that folder" }
    }

    BaseScreen(
        title = "Library sync",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            SectionLabel("Status")
            Spacer(Modifier.height(8.dp))
            SyncStatusCard(
                progress = progress,
                lastResult = message ?: lastResult,
                lastTime = lastTime,
                ready = hasPassword && (directory.isNotBlank() ||
                    (contentMode == SyncContentMode.SELECTED_PLAYLISTS && selectedPlaylists.isEmpty())),
                onStart = {
                    message = null
                    if (!LibrarySyncManager.start(context)) message = "A sync is already running"
                },
                onCancel = LibrarySyncManager::cancel,
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Connection")
            SettingsGroup {
                SettingsRow(
                    title = "AudioStream",
                    subtitle = if (hasPassword) "${hostLabel(url)}  •  $username" else "Password required",
                    value = "EDIT",
                    enabled = !isRunning,
                    onClick = { showSourceDialog = true },
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Schedule")
            SettingsGroup {
                PickerRow(
                    title = "Automatic sync",
                    subtitle = "Allowed network",
                    value = network.label,
                    options = SyncNetwork.entries.map { it.label },
                    enabled = !isRunning,
                ) { selected ->
                    network = SyncNetwork.entries.first { it.label == selected }
                    scope.launch {
                        SettingsStore.setSyncNetwork(network.value)
                        LibrarySyncJobService.reschedule(context)
                    }
                }
                GroupDivider()
                PickerRow(
                    title = "Check for changes",
                    subtitle = if (network == SyncNetwork.NEVER) "Used when automatic sync is enabled" else "Next checks follow this interval",
                    value = syncIntervals[interval] ?: "Every $interval hours",
                    options = syncIntervals.values.toList(),
                    enabled = !isRunning,
                ) { selected ->
                    interval = syncIntervals.entries.first { it.value == selected }.key
                    scope.launch {
                        SettingsStore.setSyncIntervalHours(interval)
                        LibrarySyncJobService.reschedule(context)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Content")
            SettingsGroup {
                PickerRow(
                    title = "Sync content",
                    subtitle = if (contentMode == SyncContentMode.ALL) {
                        "Download the complete remote library"
                    } else {
                        "Refresh metadata, then download your choices"
                    },
                    value = contentMode.label,
                    options = SyncContentMode.entries.map { it.label },
                    enabled = !isRunning,
                ) { selected ->
                    contentMode = SyncContentMode.entries.first { it.label == selected }
                    scope.launch { SettingsStore.setSyncContentMode(contentMode.value) }
                }
                if (contentMode == SyncContentMode.SELECTED_PLAYLISTS) {
                    GroupDivider()
                    SettingsRow(
                        title = "Choose playlists",
                        subtitle = when {
                            availablePlaylists.isEmpty() -> "Refresh the playlist list first"
                            selectedPlaylists.isEmpty() -> "Metadata only — nothing selected"
                            else -> "${selectedPlaylists.size} of ${availablePlaylists.size} selected"
                        },
                        value = "CHOOSE",
                        enabled = !isRunning && availablePlaylists.isNotEmpty(),
                        onClick = { showPlaylistDialog = true },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "Refresh playlist list",
                        subtitle = "Downloads names and track metadata only",
                        value = "REFRESH",
                        enabled = !isRunning && hasPassword,
                        onClick = {
                            message = null
                            if (!LibrarySyncManager.refreshMetadata(context)) message = "A sync is already running"
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Files")
            SettingsGroup {
                SettingsRow(
                    title = "Download folder",
                    subtitle = if (directory.isBlank()) "Choose a folder on this device" else folderLabel(directory),
                    value = if (directory.isBlank()) "CHOOSE" else "CHANGE",
                    enabled = !isRunning,
                    onClick = { directoryPicker.launch(null) },
                )
                GroupDivider()
                PickerRow(
                    title = "Folder layout",
                    subtitle = layoutExample(layout),
                    value = layout.label,
                    options = SyncLayout.entries.map { it.label },
                    enabled = !isRunning,
                ) { selected ->
                    layout = SyncLayout.entries.first { it.label == selected }
                    scope.launch { SettingsStore.setSyncLayout(layout.value) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSourceDialog) {
        SourceDialog(
            initialUrl = url,
            initialUsername = username,
            hasSavedPassword = hasPassword,
            onDismiss = { showSourceDialog = false },
            onSave = { newUrl, newUsername, newPassword ->
                scope.launch {
                    runCatching {
                        SettingsStore.setSyncUrl(newUrl)
                        SettingsStore.setSyncUsername(newUsername)
                        SettingsStore.setSyncAdapter("audiostream")
                        if (newPassword.isNotEmpty()) SyncCredentialStore.savePassword(context, newPassword)
                        LibrarySyncJobService.reschedule(context)
                    }.onSuccess {
                        url = newUrl.trim()
                        username = newUsername.trim()
                        hasPassword = SyncCredentialStore.hasPassword(context)
                        showSourceDialog = false
                        message = "Connection settings saved"
                    }.onFailure { message = it.message ?: "Could not save connection" }
                }
            },
        )
    }
    if (showPlaylistDialog) {
        PlaylistSelectionDialog(
            playlists = availablePlaylists,
            initialSelection = selectedPlaylists,
            onDismiss = { showPlaylistDialog = false },
            onSave = { selection ->
                selectedPlaylists = selection
                scope.launch { SettingsStore.setSyncSelectedPlaylists(selection) }
                showPlaylistDialog = false
            },
        )
    }
}

@Composable
private fun SyncStatusCard(
    progress: SyncProgress,
    lastResult: String,
    lastTime: Long,
    ready: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary)
            .padding(16.dp)
    ) {
        when (progress) {
            is SyncProgress.Preparing -> {
                Text("SYNCING", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Text(progress.message, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                StatusAction("CANCEL", onCancel)
            }
            is SyncProgress.Downloading -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SYNCING ${progress.current} OF ${progress.total}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Text(bytesLabel(progress.bytesDownloaded, progress.totalBytes), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(8.dp))
                Text(progress.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (progress.totalBytes > 0) (progress.bytesDownloaded.toFloat() / progress.totalBytes).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                StatusAction("STOP SYNC", onCancel)
            }
            SyncProgress.Cancelling -> {
                Text("STOPPING SYNC…", fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            else -> {
                Text("LIBRARY", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(6.dp))
                Text(statusHeadline(progress), fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(4.dp))
                Text(statusDetail(progress, lastResult), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                if (lastTime > 0) {
                    Text(
                        "Last run ${DateFormat.getMediumDateFormat(context).format(Date(lastTime))} ${DateFormat.getTimeFormat(context).format(Date(lastTime))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                StatusAction(if (ready) "SYNC NOW" else "FINISH SETUP TO SYNC", onStart, ready)
            }
        }
    }
}

private fun statusHeadline(progress: SyncProgress): String = when (progress) {
    is SyncProgress.Complete -> "Up to date"
    is SyncProgress.Failed -> "Sync needs attention"
    SyncProgress.Cancelled -> "Sync stopped"
    else -> "Ready to sync"
}

private fun statusDetail(progress: SyncProgress, fallback: String): String = when (progress) {
    is SyncProgress.Complete -> with(progress.summary) {
        "$downloaded downloaded, $unchanged unchanged, $playlists playlists"
    }
    is SyncProgress.Failed -> progress.message
    SyncProgress.Cancelled -> "The partial download was removed"
    else -> fallback
}

@Composable
private fun StatusAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        label,
        fontSize = 13.sp,
        color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onBackground)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        SettingsPill(value, enabled, onClick)
    }
}

@Composable
private fun SettingsText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsPill(
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        value,
        fontSize = 12.sp,
        color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary,
                RoundedCornerShape(8.dp),
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Box {
            SettingsPill(value, enabled) { expanded = true }
            CymaticDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    CymaticDropdownMenuItem(
                        text = if (option == value) "•  $option" else option,
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSelectionDialog(
    playlists: List<RemotePlaylist>,
    initialSelection: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selection by remember(playlists, initialSelection) {
        mutableStateOf(initialSelection.intersect(playlists.mapTo(mutableSetOf()) { it.id }))
    }
    CymaticDialog(
        title = "Playlists to keep offline",
        onDismissRequest = onDismiss,
        maxHeightRatio = 0.92f,
        widthRatio = 0.92f,
        content = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        selection = if (selection.size == playlists.size) emptySet()
                        else playlists.mapTo(mutableSetOf()) { it.id }
                    }
                    .padding(horizontal = 24.dp, vertical = 6.dp),
            ) {
                Text(if (selection.size == playlists.size) "Clear all" else "Select all", color = MaterialTheme.colorScheme.secondary)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                playlists.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selection = if (playlist.id in selection) selection - playlist.id else selection + playlist.id
                            }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = playlist.id in selection,
                            onCheckedChange = {
                                selection = if (playlist.id in selection) selection - playlist.id else selection + playlist.id
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.onBackground,
                                checkmarkColor = MaterialTheme.colorScheme.background,
                                uncheckedColor = MaterialTheme.colorScheme.secondary,
                            ),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                playlist.name.ifBlank { "Unnamed playlist" },
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("${playlist.trackIds.size} tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        },
        buttons = {
            CymaticDialogButton("Cancel", onDismiss)
            CymaticDialogDivider()
            CymaticDialogButton("Save", { onSave(selection) })
        },
    )
}

@Composable
private fun GroupDivider() {
    // Rows are separated with whitespace to match the other settings screens.
}

@Composable
private fun SectionLabel(value: String) {
    Text(value, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun SourceDialog(
    initialUrl: String,
    initialUsername: String,
    hasSavedPassword: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    CymaticDialog(
        title = "AudioStream connection",
        onDismissRequest = onDismiss,
        widthRatio = 0.9f,
        content = {
            Text(
                "Adapter: AudioStream",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            DialogField("Server URL", url, { url = it }, KeyboardType.Uri)
            DialogField("Username", username, { username = it })
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (hasSavedPassword) "New password (optional)" else "Password") },
                supportingText = if (hasSavedPassword) ({ Text("Leave blank to keep the saved password") }) else null,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = dialogFieldColors(),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        },
        buttons = {
            CymaticDialogButton("Cancel", onDismiss)
            CymaticDialogDivider()
            CymaticDialogButton("Save", { onSave(url, username, password) })
        },
    )
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = dialogFieldColors(),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.secondary,
    unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
)

private fun hostLabel(url: String): String = runCatching { java.net.URI(url).host }.getOrNull() ?: url

private fun folderLabel(uri: String): String = runCatching {
    DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uri)).substringAfter(':').ifBlank { "Selected folder" }
}.getOrDefault("Selected folder")

private fun layoutExample(layout: SyncLayout): String = when (layout) {
    SyncLayout.FLAT -> "Artist - Track.ext"
    SyncLayout.ARTIST_ALBUM_TRACKS -> "Artist / Album / Track.ext"
    SyncLayout.ALBUM_TRACKS -> "Album / Track.ext"
    SyncLayout.PLAYLIST_TRACKS -> "Playlist / Track.ext"
}

private fun bytesLabel(current: Long, total: Long): String {
    fun Long.mb() = String.format(Locale.US, "%.1f MB", this / 1_048_576.0)
    return if (total > 0) "${current.mb()} / ${total.mb()}" else current.mb()
}
