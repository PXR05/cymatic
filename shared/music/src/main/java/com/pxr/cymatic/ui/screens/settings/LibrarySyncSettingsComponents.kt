package com.pxr.cymatic.ui.screens.settings

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.pxr.cymatic.sync.SyncProgress
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.common.WheelReadingPage
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton
import com.pxr.cymatic.ui.components.screen.BaseScreen
import java.util.Date
import java.util.Locale

internal data class SyncSetting(
    val title: String,
    val detail: String? = null,
    val enabled: Boolean = true,
    val options: List<String> = emptyList(),
    val selectedOption: String? = detail,
    val onChoose: (String) -> Unit = {},
    val onClick: () -> Unit = {},
)

@Composable
internal fun SyncSettingsList(settings: List<SyncSetting>, modifier: Modifier = Modifier) {
    var choice by remember { mutableStateOf<String?>(null) }
    NavigationList(
        settings.map { setting ->
            NavigationItem(setting.title, setting.detail, enabled = setting.enabled) {
                if (setting.options.isEmpty()) setting.onClick() else choice = setting.title
            }
        },
        modifier,
    )
    settings
        .firstOrNull { it.title == choice }
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
            if (LocalWheelNavigation.current != null) {
                WheelContextMenu(setting.title, options, { choice = null })
            } else {
                CymaticDialog(
                    setting.title,
                    { choice = null },
                    content = {
                        NavigationList(options, Modifier.heightIn(max = 320.dp))
                    },
                    buttons = { CymaticDialogButton("Close", { choice = null }) },
                )
            }
        }
}

@Composable
internal fun SyncSettingsPage(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (LocalWheelNavigation.current != null) {
        WheelSettingsOverlay(title = title, onDismiss = onDismiss, content = content)
    } else {
        BackHandler(onBack = onDismiss)
        BaseScreen(title, onDismiss) { content() }
    }
}

@Composable
internal fun SyncReadingPage(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (LocalWheelNavigation.current != null) {
        WheelReadingPage(title, onDismiss, content)
    } else {
        SyncSettingsPage(title, onDismiss) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun SyncHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 12.sp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                    "AudioStream was not found. Check the server address in Connection."
                else -> error
            }
        Text(explanation)
        if (explanation != error) Text(error)
    }
}

@Composable
internal fun SyncStatusPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val isRunning = state.running
    val isCancelling = state.progress is SyncProgress.Cancelling

    val items = buildList {
        add(
            if (isRunning) {
                NavigationItem(
                    label = if (isCancelling) "Stopping…" else "Stop sync",
                    subLabel = state.statusLabel,
                    enabled = !isCancelling,
                    showIndicator = true,
                    key = "sync_action",
                    onClick = onCancel,
                )
            } else {
                NavigationItem(
                    label = "Sync now",
                    subLabel =
                        if (state.canEdit) "Download ${state.musicLabel.lowercase()}"
                        else "Sync not configured",
                    enabled = state.canEdit,
                    showIndicator = true,
                    key = "sync_action",
                    onClick = onStart,
                )
            }
        )

        add(
            NavigationItem(
                label = "Status",
                subLabel = state.statusSummary,
                enabled = false,
                showIndicator = false,
                key = "status",
            )
        )

        val progressText = when (val progress = state.progress) {
            is SyncProgress.Downloading -> {
                val fraction =
                    if (progress.totalBytes > 0) {
                        (progress.bytesDownloaded.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
                    } else if (progress.total > 0) {
                        (progress.current.toFloat() / progress.total).coerceIn(0f, 1f)
                    } else 0f
                val percent = (fraction * 100).toInt()
                buildString {
                    append("${progress.current} of ${progress.total} tracks ($percent%)")
                    if (progress.totalBytes > 0 || progress.bytesDownloaded > 0) {
                        append(" · ")
                        append(syncBytesLabel(progress.bytesDownloaded, progress.totalBytes))
                    }
                    if (progress.title.isNotBlank()) {
                        append("\n")
                        append(progress.title)
                    }
                }
            }
            is SyncProgress.Preparing -> progress.message
            is SyncProgress.Cancelling -> "Stopping sync and cleaning up temporary files…"
            else -> if (state.lastTime > 0) "Completed" else "No active transfer"
        }
        add(
            NavigationItem(
                label = "Progress",
                subLabel = progressText,
                subLabelMaxLines = 4,
                enabled = false,
                showIndicator = false,
                key = "progress",
            )
        )

        val errorMsg = (state.progress as? SyncProgress.Failed)?.message ?: state.message
        val lastRunText =
            if (state.lastTime > 0) {
                DateFormat.getMediumDateFormat(context).format(Date(state.lastTime)) + " " +
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
        }.ifBlank { "No logs recorded" }

        add(
            NavigationItem(
                label = "Logs",
                subLabel = logText,
                subLabelMaxLines = 6,
                enabled = false,
                showIndicator = false,
                key = "logs",
            )
        )
    }

    SyncSettingsPage("Sync status", onDismiss) {
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
