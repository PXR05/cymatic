package com.pxr.cymatic.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncProgress
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController

private enum class SyncPage { LIBRARY, MUSIC, ADVANCED, SCHEDULE, STATUS, HELP }

@Composable
fun LibrarySyncSettingsScreen(modifier: Modifier = Modifier) {
    val model: LibrarySyncSettingsViewModel = viewModel()
    val state by model.state.collectAsState()
    val nav = LocalNavController.current
    var page by remember { mutableStateOf<SyncPage?>(null) }
    var scheduleReturnPage by remember { mutableStateOf(SyncPage.ADVANCED) }
    var showConnection by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var refreshAfterSave by remember { mutableStateOf(false) }
    var shownError by remember { mutableStateOf<String?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    val error = state.message ?: (state.progress as? SyncProgress.Failed)?.message
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(model::setDirectory)
    }
    LaunchedEffect(refreshAfterSave, state.saving) {
        if (refreshAfterSave && !state.saving) {
            refreshAfterSave = false
            model.refreshLibrary()
        }
    }
    LaunchedEffect(state.loading, error) {
        if (!state.loading) {
            if (error != null && error != lastError) shownError = error
            lastError = error
        }
    }
    fun chooseFolder() {
        folderPicker.launch(state.directory.takeIf { it.isNotBlank() }?.let(Uri::parse))
    }
    fun openMusic() {
        page = SyncPage.MUSIC
        if (state.catalog == null) model.refreshLibrary()
    }
    fun download() {
        when (state.nextStep) {
            SyncSetupStep.CONNECTION -> showConnection = true
            SyncSetupStep.FOLDER -> chooseFolder()
            SyncSetupStep.MUSIC -> openMusic()
            null -> model.startSync()
        }
    }

    BaseScreen("Remote library", { nav.popBackStack() }, modifier = modifier) {
        SyncSettingsList(
            buildList {
                if (state.loading) add(SyncSetting("Loading…", enabled = false))
                else {
                    add(SyncSetting(
                        if (state.connected) "Connection" else "Connect to AudioStream",
                        if (state.connected) "${state.username} · ${syncHostLabel(state.url)}"
                        else "Sign in to load your library",
                        enabled = state.canEdit,
                        onClick = { showConnection = true },
                    ))
                    if (state.connected) {
                        add(SyncSetting(
                            "Library",
                            if (state.refreshingCatalog) "Refreshing…"
                            else state.catalog?.let { "${it.tracks.size} songs · ${it.playlists.size} playlists" }
                                ?: "Load songs and playlists",
                            enabled = !state.saving,
                            onClick = {
                                page = SyncPage.LIBRARY
                                if (state.catalog == null) model.refreshLibrary()
                            },
                        ))
                        add(SyncSetting("Keep offline", state.musicLabel, state.canEdit, onClick = ::openMusic))
                        if (state.running || state.contentMode != SyncContentMode.NONE || state.lastTime > 0) {
                            add(SyncSetting(
                                if (state.running) "Progress" else "Downloads",
                                state.statusSummary,
                                onClick = { page = SyncPage.STATUS },
                            ))
                        }
                        add(SyncSetting("Advanced", "Storage and automatic downloads", state.canEdit,
                            onClick = { page = SyncPage.ADVANCED }))
                    }
                    add(SyncSetting("Help", onClick = { page = SyncPage.HELP }))
                }
            },
        )
    }
    when (page) {
        SyncPage.LIBRARY -> SyncRemoteLibraryPage(state, { page = null }, model::refreshLibrary, model::cancel)
        SyncPage.MUSIC -> SyncMusicPage(
            state, { page = null },
            onMode = { mode ->
                model.setContentMode(mode)
                if (mode == SyncContentMode.SELECTED_PLAYLISTS && state.catalog == null)
                    refreshAfterSave = true
            },
            onPlaylists = {
                showPlaylists = true
                if (state.catalog == null) model.refreshLibrary()
            },
            onDownload = ::download,
            onSchedule = {
                scheduleReturnPage = SyncPage.MUSIC
                page = SyncPage.SCHEDULE
            },
        )
        SyncPage.ADVANCED -> SyncAdvancedPage(
            state, { page = null }, ::chooseFolder, model::useManagedStorage,
            model::setLayout, {
                scheduleReturnPage = SyncPage.ADVANCED
                page = SyncPage.SCHEDULE
            },
        )
        SyncPage.SCHEDULE -> SyncSchedulePage(
            state, { page = scheduleReturnPage }, ::download, model::setNetwork, model::setInterval,
        )
        SyncPage.STATUS -> SyncStatusPage(state, { page = null }, ::download, model::cancel)
        SyncPage.HELP -> SyncHelpPage { page = null }
        null -> Unit
    }
    if (showConnection) SyncConnectionPage(
        state,
        onDismiss = { if (!state.saving) showConnection = false },
        onSave = { url, username, password ->
            model.saveConnection(url, username, password) {
                showConnection = false
                refreshAfterSave = true
            }
        },
        onCheck = model::refreshLibrary,
    )
    if (showPlaylists) SyncPlaylistsPage(
        state,
        onDismiss = {
            if (!state.saving) {
                showPlaylists = false
                model.commitPlaylists()
            }
        },
        onChange = model::setPlaylists,
        onRefresh = model::refreshLibrary,
    )
    shownError?.let { SyncErrorPage(it) { shownError = null } }
}
