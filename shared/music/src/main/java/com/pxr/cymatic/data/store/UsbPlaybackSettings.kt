package com.pxr.cymatic.data.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class DsdUsbMode(val label: String) {
    AUTO("Automatic"),
    PCM("Convert to PCM"),
    DOP("DSD over PCM (DoP)"),
}

object UsbPlaybackSettings {
    @Volatile private var currentDsdModes: Map<String, String>? = null
    private val enabledKey = booleanPreferencesKey("DIRECT_USB_ENABLED")
    val enabledFlow
        get() = SettingsStore.store.data.map { it[enabledKey] ?: false }.distinctUntilChanged()

    val enabled
        get() = SettingsStore.currentPreferences?.get(enabledKey) ?: false

    suspend fun setEnabled(value: Boolean) {
        SettingsStore.store.edit { it[enabledKey] = value }
    }

    fun dsdMode(deviceKey: String): DsdUsbMode =
        DsdUsbMode.entries.firstOrNull {
            it.name ==
                (currentDsdModes?.get("USB_DSD_MODE_$deviceKey")
                    ?: SettingsStore.currentPreferences?.get(
                        stringPreferencesKey("USB_DSD_MODE_$deviceKey")
                    ))
        } ?: DsdUsbMode.AUTO

    val dsdModesFlow
        get() =
            SettingsStore.store.data
                .map { prefs ->
                    prefs
                        .asMap()
                        .filterKeys { it.name.startsWith("USB_DSD_MODE_") }
                        .mapNotNull { (key, value) -> (value as? String)?.let { key.name to it } }
                        .toMap()
                        .also { currentDsdModes = it }
                }
                .distinctUntilChanged()

    suspend fun setDsdMode(deviceKey: String, mode: DsdUsbMode) {
        SettingsStore.store.edit { it[stringPreferencesKey("USB_DSD_MODE_$deviceKey")] = mode.name }
    }
}
