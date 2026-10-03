package com.pxr.cymatic.data.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

object UsbPlaybackSettings {
    private val enabledKey = booleanPreferencesKey("DIRECT_USB_ENABLED")
    private val volumeKey = intPreferencesKey("DIRECT_USB_VOLUME_PERCENT")
    val enabledFlow
        get() = SettingsStore.store.data.map { it[enabledKey] ?: false }.distinctUntilChanged()

    val volumePercentFlow
        get() =
            SettingsStore.store.data
                .map {
                    (it[volumeKey] ?: 25).coerceIn(
                        0,
                        100,
                    )
                }
                .distinctUntilChanged()

    val enabled
        get() = SettingsStore.currentPreferences?.get(enabledKey) ?: false

    suspend fun setEnabled(value: Boolean) {
        SettingsStore.store.edit { it[enabledKey] = value }
    }

    suspend fun setVolumePercent(value: Int) {
        SettingsStore.store.edit { it[volumeKey] = value.coerceIn(0, 100) }
    }
}
