package com.pxr.cymatic.ui.screens.settings

internal fun syncSetupSettings(
    state: LibrarySyncSettingsState,
    onConnect: () -> Unit,
    onFolder: () -> Unit,
    onMusic: () -> Unit,
    onContinue: () -> Unit,
    onHelp: () -> Unit,
): List<SyncSetting> =
    listOf(
        SyncSetting(
            "1. Connection",
            if (state.connected) state.username else "Enter your AudioStream login",
            enabled = state.canEdit && !state.running,
            onClick = onConnect,
        ),
        SyncSetting(
            "2. Download folder",
            if (state.folderAccessible) syncFolderLabel(state.directory)
            else "Choose a folder on this device",
            enabled = state.canEdit && state.connected,
            onClick = onFolder,
        ),
        SyncSetting(
            "3. Offline music",
            if (state.musicConfigured) state.musicLabel else "Choose all music or playlists",
            enabled = state.canEdit && state.connected && state.folderAccessible,
            onClick = onMusic,
        ),
        SyncSetting(
            "Continue",
            when (state.nextStep) {
                SyncSetupStep.CONNECTION -> "Connect your account"
                SyncSetupStep.FOLDER -> "Choose a download folder"
                else -> "Choose offline music"
            },
            enabled = state.canEdit && !state.running,
            onClick = onContinue,
        ),
        SyncSetting("Help", onClick = onHelp),
    )
