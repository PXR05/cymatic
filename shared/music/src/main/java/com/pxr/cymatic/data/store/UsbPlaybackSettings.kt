package com.pxr.cymatic.data.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

object UsbPlaybackSettings {
    private val enabledKey = booleanPreferencesKey("DIRECT_USB_ENABLED")
    val enabledFlow
        get() = SettingsStore.store.data.map { it[enabledKey] ?: false }.distinctUntilChanged()

    val enabled
        get() = SettingsStore.currentPreferences?.get(enabledKey) ?: false

    suspend fun setEnabled(value: Boolean) {
        SettingsStore.store.edit { it[enabledKey] = value }
    }
}
