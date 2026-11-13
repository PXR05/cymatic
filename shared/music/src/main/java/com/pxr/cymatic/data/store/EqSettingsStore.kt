package com.pxr.cymatic.data.store

import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pxr.cymatic.data.model.EqPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

object EqSettingsStore {
    private val EQ_PRESETS_KEY = stringPreferencesKey("EQ_PRESETS")
    private val EQ_SELECTED_PRESET_KEY = stringPreferencesKey("EQ_SELECTED_PRESET")
    private val EQ_GLOBAL_ENABLED_KEY = booleanPreferencesKey("EQ_GLOBAL_ENABLED")
    private val EQ_ACTIVE_AUDIO_DEVICE_KEY = stringPreferencesKey("EQ_ACTIVE_AUDIO_DEVICE")
    private val EQ_DEVICE_PRESETS_KEY = stringPreferencesKey("EQ_DEVICE_PRESETS")

    private fun getEqPresetsList(prefs: Preferences): List<EqPreset> {
        val jsonStr = prefs[EQ_PRESETS_KEY] ?: "[]"
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<EqPreset>()
            for (i in 0 until array.length()) {
                EqPreset.fromJson(array.getJSONObject(i).toString())?.let { list.add(it) }
            }
            if (list.isEmpty()) listOf(EqPreset.defaultPreset()) else list
        } catch (e: Exception) {
            Log.e("SettingsStore", "Failed to parse EQ presets: ${e.message}")
            listOf(EqPreset.defaultPreset())
        }
    }

    val eqPresetsFlow: Flow<List<EqPreset>>
        get() = SettingsStore.store.data.map { prefs -> getEqPresetsList(prefs) }

    val currentEqPresets: List<EqPreset>
        get() =
            SettingsStore.currentPreferences?.let { getEqPresetsList(it) }
                ?: listOf(EqPreset.defaultPreset())

    val eqSelectedPresetFlow: Flow<String>
        get() =
            SettingsStore.store.data.map { prefs ->
                prefs[EQ_SELECTED_PRESET_KEY] ?: "Flat"
            }

    val currentEqSelectedPreset: String
        get() = SettingsStore.currentPreferences?.get(EQ_SELECTED_PRESET_KEY) ?: "Flat"

    val eqGlobalEnabledFlow: Flow<Boolean>
        get() =
            SettingsStore.store.data.map { prefs ->
                prefs[EQ_GLOBAL_ENABLED_KEY] ?: true
            }

    val currentEqGlobalEnabled: Boolean
        get() = SettingsStore.currentPreferences?.get(EQ_GLOBAL_ENABLED_KEY) ?: true

    val activeAudioDeviceFlow: Flow<String>
        get() =
            SettingsStore.store.data.map { prefs ->
                prefs[EQ_ACTIVE_AUDIO_DEVICE_KEY] ?: ""
            }

    val currentActiveAudioDevice: String
        get() = SettingsStore.currentPreferences?.get(EQ_ACTIVE_AUDIO_DEVICE_KEY) ?: ""

    val eqDevicePresetsFlow: Flow<Map<String, String>>
        get() =
            SettingsStore.store.data.map { prefs ->
                parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY])
            }

    val currentEqDevicePresets: Map<String, String>
        get() = parseStringMap(SettingsStore.currentPreferences?.get(EQ_DEVICE_PRESETS_KEY))

    val effectiveEqSelectedPresetFlow: Flow<String>
        get() =
            SettingsStore.store.data.map { prefs ->
                val globalPreset = prefs[EQ_SELECTED_PRESET_KEY] ?: "Flat"
                val activeDevice = prefs[EQ_ACTIVE_AUDIO_DEVICE_KEY].orEmpty()
                val devicePresets = parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY])
                devicePresets[activeDevice] ?: globalPreset
            }

    suspend fun setEqPresets(presets: List<EqPreset>) {
        SettingsStore.store.edit { prefs ->
            val array = JSONArray()
            presets.forEach { array.put(it.toJson()) }
            prefs[EQ_PRESETS_KEY] = array.toString()
        }
    }

    suspend fun setEqSelectedPreset(name: String) {
        SettingsStore.store.edit { prefs ->
            prefs[EQ_SELECTED_PRESET_KEY] = name
        }
    }

    suspend fun setEqSelectedPresetForActiveDevice(name: String) {
        SettingsStore.store.edit { prefs ->
            val activeDevice = prefs[EQ_ACTIVE_AUDIO_DEVICE_KEY].orEmpty()
            if (activeDevice.isBlank()) {
                prefs[EQ_SELECTED_PRESET_KEY] = name
                return@edit
            }
            val devicePresets = parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY]).toMutableMap()
            devicePresets[activeDevice] = name
            prefs[EQ_DEVICE_PRESETS_KEY] = encodeStringMap(devicePresets)
        }
    }

    suspend fun setEqDevicePreset(deviceKey: String, name: String) {
        if (deviceKey.isBlank()) return
        SettingsStore.store.edit { prefs ->
            val devicePresets = parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY]).toMutableMap()
            devicePresets[deviceKey] = name
            prefs[EQ_DEVICE_PRESETS_KEY] = encodeStringMap(devicePresets)
        }
    }

    suspend fun setActiveAudioDevice(deviceKey: String) {
        SettingsStore.store.edit { prefs ->
            prefs[EQ_ACTIVE_AUDIO_DEVICE_KEY] = deviceKey
        }
    }

    suspend fun removeEqDevicePreset(deviceKey: String) {
        SettingsStore.store.edit { prefs ->
            val devicePresets = parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY]).toMutableMap()
            devicePresets.remove(deviceKey)
            prefs[EQ_DEVICE_PRESETS_KEY] = encodeStringMap(devicePresets)
        }
    }

    suspend fun setEqGlobalEnabled(enabled: Boolean) {
        SettingsStore.store.edit { prefs ->
            prefs[EQ_GLOBAL_ENABLED_KEY] = enabled
        }
    }

    private fun parseStringMap(jsonStr: String?): Map<String, String> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val json = org.json.JSONObject(jsonStr)
            json
                .keys()
                .asSequence()
                .associateWith { key -> json.optString(key) }
                .filterValues { it.isNotBlank() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeStringMap(values: Map<String, String>): String {
        val json = org.json.JSONObject()
        values.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                json.put(key, value)
            }
        }
        return json.toString()
    }
}
