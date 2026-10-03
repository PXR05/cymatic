package com.pxr.cymatic.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncProgress
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController

private enum class SyncPage {
    MUSIC,
    SCHEDULE,
    STATUS,
    HELP,
}

@Composable
fun LibrarySyncSettingsScreen(modifier: Modifier = Modifier) {
    val model: LibrarySyncSettingsViewModel = viewModel()
    val state by model.state.collectAsState()
    val nav = LocalNavController.current
    var page by remember { mutableStateOf<SyncPage?>(null) }
    var showConnection by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var refreshAfterSave by remember { mutableStateOf(false) }
    var shownError by remember { mutableStateOf<String?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var errorsInitialized by remember { mutableStateOf(false) }
    val error = state.message ?: (state.progress as? SyncProgress.Failed)?.message
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it?.let(model::setDirectory)
        }

    LaunchedEffect(refreshAfterSave, state.saving) {
        if (refreshAfterSave && !state.saving) {
            refreshAfterSave = false
            model.refreshPlaylists()
        }
    }

    LaunchedEffect(state.loading, error) {
        if (!state.loading) {
            if (errorsInitialized && error != null && error != lastError) shownError = error
            lastError = error
            errorsInitialized = true
        }
    }

    fun openMusic() {
        page = SyncPage.MUSIC
        if (state.contentMode == SyncContentMode.SELECTED_PLAYLISTS && state.catalog == null)
            model.refreshPlaylists()
    }

    fun chooseFolder() {
        folderPicker.launch(state.directory.takeIf { it.isNotBlank() }?.let(Uri::parse))
    }

    fun continueSetup() {
        when (state.nextStep) {
            SyncSetupStep.CONNECTION -> showConnection = true
            SyncSetupStep.FOLDER -> chooseFolder()
            SyncSetupStep.MUSIC -> openMusic()
            null -> model.startSync()
        }
    }

    BaseScreen(
        if (state.nextStep != null) "Set up sync" else "Library sync",
        { nav.popBackStack() },
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize()) {
            SyncSettingsList(
                if (state.loading) listOf(SyncSetting("Loading…", enabled = false))
                else if (state.nextStep != null)
                    syncSetupSettings(
                        state,
                        { showConnection = true },
                        ::chooseFolder,
                        ::openMusic,
                        ::continueSetup,
                        { page = SyncPage.HELP },
                    )
                else
                    buildList {
                        if (state.running) {
                            add(
                                SyncSetting(
                                    if (state.progress == SyncProgress.Cancelling) "Stopping…"
                                    else "Stop sync",
                                    state.statusLabel,
                                    enabled = state.progress != SyncProgress.Cancelling,
                                    onClick = model::cancel,
                                )
                            )
                        } else {
                            add(
                                SyncSetting(
                                    "Sync now",
                                    "Download ${state.musicLabel.lowercase()}",
                                    enabled = state.canEdit,
                                    onClick = model::startSync,
                                )
                            )
                        }
                        add(
                            SyncSetting(
                                "Status",
                                state.statusLabel,
                                enabled = !state.loading,
                                onClick = { page = SyncPage.STATUS },
                            )
                        )
                        add(
                            SyncSetting(
                                "Connection",
                                if (state.connected)
                                    "${state.username} · ${syncHostLabel(state.url)}"
                                else "AudioStream account required",
                                enabled = state.canEdit && !state.running,
                                onClick = { showConnection = true },
                            )
                        )
                        add(
                            SyncSetting(
                                "Offline music",
                                state.musicLabel,
                                enabled = state.canEdit,
                                onClick = ::openMusic,
                            )
                        )
                        add(
                            SyncSetting(
                                "Download folder",
                                when {
                                    state.directory.isBlank() -> "Choose a folder on this device"
                                    !state.folderAccessible ->
                                        "Access lost · choose the folder again"
                                    else -> syncFolderLabel(state.directory)
                                },
                                enabled = state.canEdit,
                                onClick = ::chooseFolder,
                            )
                        )
                        add(
                            SyncSetting(
                                "Automatic sync",
                                syncNetworkLabel(state.network),
                                enabled = state.canEdit,
                                onClick = { page = SyncPage.SCHEDULE },
                            )
                        )
                        add(
                            SyncSetting(
                                "Help",
                                enabled = !state.loading,
                                onClick = {
                                    page = SyncPage.HELP
                                },
                            )
                        )
                    },
                Modifier.weight(1f),
            )
        }
    }

    when (page) {
        SyncPage.MUSIC ->
            SyncMusicPage(
                state,
                { page = null },
                onMode = { mode ->
                    model.setContentMode(mode)
                    if (mode == SyncContentMode.SELECTED_PLAYLISTS && state.catalog == null)
                        refreshAfterSave = true
                },
                onPlaylists = {
                    showPlaylists = true
                    if (state.catalog == null) model.refreshPlaylists()
                },
                onConnect = { showConnection = true },
                onRefresh = model::refreshPlaylists,
                onLayout = model::setLayout,
            )
        SyncPage.SCHEDULE ->
            SyncSchedulePage(
                state,
                { page = null },
                ::continueSetup,
                model::setNetwork,
                model::setInterval,
            )
        SyncPage.STATUS -> SyncStatusPage(state, { page = null })
        SyncPage.HELP -> SyncHelpPage { page = null }
        null -> Unit
    }
    if (showConnection)
        SyncConnectionPage(
            state,
            onDismiss = { if (!state.saving) showConnection = false },
            onSave = { url, username, password ->
                model.saveConnection(url, username, password) {
                    showConnection = false
                    refreshAfterSave = true
                }
            },
            onCheck = model::refreshPlaylists,
        )
    if (showPlaylists)
        SyncPlaylistsPage(
            state,
            onDismiss = { if (!state.saving) showPlaylists = false },
            onChange = model::setPlaylists,
            onRefresh = model::refreshPlaylists,
        )
    shownError?.let { SyncErrorPage(it) { shownError = null } }
}
