package com.pxr.cymatic.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceVolumePreferencesTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            return transform(data.value).also { data.value = it }
        }
    }

    @Test
    fun reconnectAndModeSwitchRestoreIndependentVolumes() = runBlocking {
        val store = MemoryStore()
        val preferences = DeviceVolumePreferences(store)
        preferences.save("dac-a", direct = false, 600)
        preferences.save("dac-a", direct = true, 74)
        preferences.save("dac-b", direct = true, 12)
        preferences.save("dac-b", direct = false, 200)
        val reopened = DeviceVolumePreferences(store)
        assertEquals(600, reopened.get("dac-a", direct = false))
        assertEquals(74, reopened.get("dac-a", direct = true))
        assertEquals(12, reopened.get("dac-b", direct = true))
        assertEquals(200, reopened.get("dac-b", direct = false))
        assertNull(reopened.get("new-dac", direct = true))
    }

    @Test
    fun muteAndMaximumVolumesSurviveReconnect() = runBlocking {
        val preferences = DeviceVolumePreferences(MemoryStore())
        preferences.save("dac", direct = true, 0)
        assertEquals(0, preferences.get("dac", direct = true))
        preferences.save("dac", direct = true, 200)
        assertEquals(100, preferences.get("dac", direct = true))
        preferences.save("dac", direct = false, 1200)
        assertEquals(1000, preferences.get("dac", direct = false))
    }
}
