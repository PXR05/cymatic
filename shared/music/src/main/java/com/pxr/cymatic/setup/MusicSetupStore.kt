package com.pxr.cymatic.setup

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.sync.RemoteLibraries
import com.pxr.cymatic.sync.SyncCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class SetupDraft(
    val complete: Boolean, val step: String, val address: String, val username: String,
    val navigationProgress: Int, val tutorialFolder: Int, val backPracticed: Boolean,
)

internal object MusicSetupStore {
    private val complete = booleanPreferencesKey("MUSIC_SETUP_COMPLETE")
    private val step = stringPreferencesKey("MUSIC_SETUP_STEP")
    private val address = stringPreferencesKey("MUSIC_SETUP_ADDRESS")
    private val username = stringPreferencesKey("MUSIC_SETUP_USERNAME")
    private val navigationProgress = intPreferencesKey("MUSIC_SETUP_WHEEL_PROGRESS")
    private val tutorialFolder = intPreferencesKey("MUSIC_SETUP_FOLDER")
    private val backPracticed = booleanPreferencesKey("MUSIC_SETUP_BACK_PRACTICED")

    suspend fun load(context: Context): SetupDraft = withContext(Dispatchers.IO) {
        var prefs = SettingsStore.store.data.first()
        if (!prefs.contains(complete) && !prefs.contains(step)) {
            val source = RemoteLibraries.currentSource()
            val existing = prefs.contains(stringPreferencesKey("WHEEL_LAST_PANEL")) ||
                    SettingsStore.getLastScanTimeMs() > 0 || SettingsStore.getScanDirectories()
                .isNotEmpty() ||
                    SyncCredentialStore.hasPassword(context, source.id)
            if (!existing) SettingsStore.setSyncConnection("", "", resetSelection = true)
            SettingsStore.store.edit {
                it[complete] = existing
                it[step] = "navigate"
            }
            prefs = SettingsStore.store.data.first()
        }
        SetupDraft(
            prefs[complete] == true,
            prefs[step] ?: "navigate",
            prefs[address].orEmpty(),
            prefs[username].orEmpty(),
            (prefs[navigationProgress] ?: 0).coerceIn(0, 100),
            (prefs[tutorialFolder] ?: 0).coerceIn(MusicSetupTutorial.folders.indices),
            prefs[backPracticed] == true
        )
    }

    suspend fun saveStep(
        value: String,
        progress: Int? = null,
        folder: Int? = null,
        practiced: Boolean? = null
    ) = SettingsStore.store.edit {
        it[step] = value
        progress?.let { progress -> it[navigationProgress] = progress }
        folder?.let { folder -> it[tutorialFolder] = folder }
        practiced?.let { practiced -> it[backPracticed] = practiced }
    }

    suspend fun saveDraft(url: String, account: String) = SettingsStore.store.edit {
        it[address] = url
        it[username] = account
    }

    suspend fun finish() = SettingsStore.store.edit {
        it[complete] = true
        it.remove(address)
        it.remove(username)
        it.remove(step)
        it.remove(navigationProgress)
        it.remove(tutorialFolder)
        it.remove(backPracticed)
    }
}
