package com.pxr.cymatic.ui.screens.settings.sync

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.sync.SyncProgress
import com.pxr.cymatic.ui.components.common.ReadingScreen
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.components.screen.BaseScreen
import java.util.Date
import java.util.Locale

internal data class SyncSetting(
    val title: String,
    val detail: String? = null,
    val enabled: Boolean = true,
    val options: List<String> = emptyList(),
    val selectedOption: String? = detail,
    val key: String = title,
    val icon: (@Composable () -> Unit)? = null,
    val onChoose: (String) -> Unit = {},
    val onClick: () -> Unit = {},
)

@Composable
internal fun SyncSettingsList(settings: List<SyncSetting>, modifier: Modifier = Modifier) {
    var choice by remember { mutableStateOf<String?>(null) }
    NavigationList(
        settings.map { setting ->
            NavigationItem(
                setting.title,
                setting.detail,
                icon = setting.icon,
                enabled = setting.enabled,
                showIndicator = setting.enabled,
                key = setting.key,
            ) {
                if (setting.options.isEmpty()) setting.onClick() else choice = setting.key
            }
        },
        modifier,
    )
    settings
        .firstOrNull { it.key == choice }
        ?.let { setting ->
            val options =
                setting.options.map { option ->
                    NavigationItem(
                        option,
                        if (option == setting.selectedOption) "Selected" else null,
                        checked = option == setting.selectedOption,
                    ) {
                        setting.onChoose(option)
                        choice = null
                    }
                }
            WheelContextMenu(setting.title, options, { choice = null })
        }
}

@Composable
internal fun SyncSettingsPage(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BaseScreen(title, onDismiss) { content() }
}

@Composable
internal fun SyncReadingPage(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val readingContent: @Composable () -> Unit = {
        ProvideTextStyle(
            MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                content()
            }
        }
    }
    ReadingScreen(title, onDismiss, readingContent)
}

@Composable
internal fun SyncHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 12.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SyncErrorPage(error: String, onDismiss: () -> Unit) {
    SyncReadingPage("Sync error", onDismiss) {
        val explanation =
            when {
                error.contains("(401)") || error.contains("(403)") ->
                    "Could not sign in. Check your username and password in Connection."

                error.contains("(404)") ->
                    "The server was not found. Check the server address in Connection."

                else -> error
            }
        Text(explanation)
        if (explanation != error) Text(error)
        Text(
            "Press Center or Back to go back.",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
internal fun SyncStatusPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onOfflineLibrary: () -> Unit,
) {
    val context = LocalContext.current
    val isRunning = state.running
    val isCancelling = state.progress is SyncProgress.Cancelling

    val items = buildList {
        add(
            if (isRunning) {
                NavigationItem(
                    label =
                        if (isCancelling) "Stopping…"
                        else if (state.refreshingCatalog) "Stop refresh" else "Stop downloads",
                    subLabel = state.statusLabel,
                    icon = pixelIcon(R.drawable.ic_pixel_close),
                    enabled = !isCancelling,
                    showIndicator = true,
                    key = "sync_action",
                    onClick = onCancel,
                )
            } else {
                NavigationItem(
                    label = "Sync now",
                    subLabel =
                        when {
                            !state.hasOfflineSelection -> "No music selected to keep offline"
                            state.nextStep == SyncSetupStep.FOLDER ->
                                "Restore access to your custom folder"

                            else -> "Download selected offline music"
                        },
                    icon = pixelIcon(R.drawable.ic_pixel_download),
                    enabled = state.canEdit && state.hasOfflineSelection,
                    showIndicator = true,
                    key = "sync_action",
                    onClick = onStart,
                )
            }
        )

        add(
            NavigationItem(
                label = "Browse offline music",
                subLabel = "Music available without internet",
                icon = pixelIcon(R.drawable.ic_pixel_offline),
                key = "offline_library",
                onClick = onOfflineLibrary,
            )
        )

        add(
            NavigationItem(
                label = "Status",
                subLabel = state.statusSummary,
                icon = pixelIcon(R.drawable.ic_pixel_info),
                enabled = false,
                showIndicator = false,
                key = "status",
            )
        )

        val progressText =
            if (state.refreshingCatalog) "Loading songs and playlists…"
            else
                when (val progress = state.progress) {
                    is SyncProgress.Downloading -> {
                        val fraction =
                            if (progress.totalBytes > 0) {
                                (progress.bytesDownloaded.toFloat() / progress.totalBytes).coerceIn(
                                    0f,
                                    1f,
                                )
                            } else if (progress.total > 0) {
                                (progress.current.toFloat() / progress.total).coerceIn(0f, 1f)
                            } else 0f
                        val percent = (fraction * 100).toInt()
                        buildString {
                            progress.sourceName?.let {
                                append(it)
                                append(" · ")
                            }
                            append("${progress.current} of ${progress.total} tracks ($percent%)")
                            if (progress.totalBytes > 0 || progress.bytesDownloaded > 0) {
                                append(" · ")
                                append(
                                    syncBytesLabel(progress.bytesDownloaded, progress.totalBytes)
                                )
                            }
                            if (progress.title.isNotBlank()) {
                                append("\n")
                                append(progress.title)
                            }
                        }
                    }

                    is SyncProgress.Preparing ->
                        progress.sourceName?.let { "$it · ${progress.message}" } ?: progress.message

                    is SyncProgress.Cancelling -> "Stopping sync and cleaning up temporary files…"
                    else -> if (state.lastTime > 0) "Completed" else "No active transfer"
                }
        add(
            NavigationItem(
                label = "Progress",
                subLabel = progressText,
                icon = pixelIcon(R.drawable.ic_pixel_download),
                subLabelMaxLines = 4,
                enabled = false,
                showIndicator = false,
                key = "progress",
            )
        )

        val errorMsg = (state.progress as? SyncProgress.Failed)?.message ?: state.message
        val lastRunText =
            if (state.lastTime > 0) {
                DateFormat.getMediumDateFormat(context).format(Date(state.lastTime)) +
                        " " +
                        DateFormat.getTimeFormat(context).format(Date(state.lastTime))
            } else {
                null
            }

        val logText = buildString {
            if (!errorMsg.isNullOrBlank()) {
                append("Error: ")
                append(errorMsg)
                append("\n")
            }
            if (lastRunText != null) {
                append(lastRunText)
                if (state.lastResult.isNotBlank() && state.lastResult != "Not synced yet") {
                    append("\n")
                    append(state.lastResult)
                }
            } else {
                append(state.lastResult)
            }
        }
            .ifBlank { "No logs recorded" }

        add(
            NavigationItem(
                label = "Logs",
                subLabel = logText,
                icon = pixelIcon(R.drawable.ic_pixel_document),
                subLabelMaxLines = 6,
                enabled = false,
                showIndicator = false,
                key = "logs",
            )
        )
    }

    SyncSettingsPage(if (state.refreshingCatalog) "Library refresh" else "Downloads", onDismiss) {
        NavigationList(
            items = items,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun syncBytesLabel(current: Long, total: Long): String {
    fun Long.mb() = String.format(Locale.US, "%.1f MB", this / 1_048_576.0)
    return if (total > 0) "${current.mb()} / ${total.mb()}" else current.mb()
}
