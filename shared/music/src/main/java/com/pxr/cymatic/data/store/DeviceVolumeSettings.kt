package com.pxr.cymatic.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

/** Android stream volumes use thousandths of the route's maximum; USB uses percent. */
object DeviceVolumeSettings {
    suspend fun get(device: String, direct: Boolean): Int? =
        DeviceVolumePreferences(SettingsStore.store).get(device, direct)

    suspend fun save(device: String, direct: Boolean, volume: Int) =
        DeviceVolumePreferences(SettingsStore.store).save(device, direct, volume)
}

internal class DeviceVolumePreferences(private val store: DataStore<Preferences>) {
    private fun key(device: String, direct: Boolean) =
        intPreferencesKey("DEVICE_VOLUME_${if (direct) "DIRECT" else "ANDROID"}_$device")

    suspend fun get(device: String, direct: Boolean): Int? = store.data.first()[key(device, direct)]

    suspend fun save(device: String, direct: Boolean, volume: Int) {
        store.edit {
            it[key(device, direct)] = volume.coerceIn(0, if (direct) 100 else 1000)
        }
    }
}
