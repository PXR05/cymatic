package com.pxr.cymatic.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.model.FilterType
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EqUiState(
    val eqEnabled: Boolean = false,
    val usbExclusiveEnabled: Boolean = false,
    val presets: List<EqPreset> = emptyList(),
    val selectedPresetName: String = "Flat",
    val activePreset: EqPreset = EqPreset.defaultPreset(),
    val activeDeviceKey: String = "",
    val usesDevicePreset: Boolean = false
)

class EqViewModel : ViewModel() {

    private val initialDeviceSelectedName = SettingsStore.currentEqDevicePresets[SettingsStore.currentActiveAudioDevice]
    private val initialSelectedName = initialDeviceSelectedName ?: SettingsStore.currentEqSelectedPreset
    private val initialActive = SettingsStore.currentEqPresets.firstOrNull { it.name == initialSelectedName }
        ?: SettingsStore.currentEqPresets.firstOrNull()
        ?: EqPreset.defaultPreset()

    val uiState: StateFlow<EqUiState> = combine(
        SettingsStore.eqGlobalEnabledFlow,
        SettingsStore.usbExclusiveFlow,
        SettingsStore.eqPresetsFlow,
        SettingsStore.eqSelectedPresetFlow,
        SettingsStore.activeAudioDeviceFlow,
        SettingsStore.eqDevicePresetsFlow
    ) { values ->
        val enabled = values[0] as Boolean
        val usbExclusiveEnabled = values[1] as Boolean
        val presets = values[2] as List<EqPreset>
        val globalSelectedName = values[3] as String
        val activeDeviceKey = values[4] as String
        val devicePresets = values[5] as Map<String, String>
        val deviceSelectedName = devicePresets[activeDeviceKey]
        val selectedName = deviceSelectedName ?: globalSelectedName
        val active = presets.firstOrNull { it.name == selectedName }
            ?: presets.firstOrNull()
            ?: EqPreset.defaultPreset()
        EqUiState(
            eqEnabled = enabled,
            usbExclusiveEnabled = usbExclusiveEnabled,
            presets = presets,
            selectedPresetName = active.name,
            activePreset = active,
            activeDeviceKey = activeDeviceKey,
            usesDevicePreset = deviceSelectedName != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EqUiState(
            eqEnabled = SettingsStore.currentEqGlobalEnabled,
            usbExclusiveEnabled = SettingsStore.currentUsbExclusive,
            presets = SettingsStore.currentEqPresets,
            selectedPresetName = initialActive.name,
            activePreset = initialActive,
            activeDeviceKey = SettingsStore.currentActiveAudioDevice,
            usesDevicePreset = initialDeviceSelectedName != null
        )
    )

    private var bandPersistJob: Job? = null

    private val _livePreset = MutableStateFlow<EqPreset?>(null)
    val livePreset: StateFlow<EqPreset?> = _livePreset

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setEqGlobalEnabled(enabled)
        }
    }

    fun setUsbExclusive(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setUsbExclusive(enabled)
        }
    }

    fun selectPreset(name: String) {
        viewModelScope.launch {
            SettingsStore.setEqSelectedPresetForActiveDevice(name)
            _livePreset.value = null
        }
    }

    fun addPreset(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val current = SettingsStore.eqPresetsFlow
                .stateIn(viewModelScope)
                .value
            if (current.any { it.name == name }) return@launch
            val newPreset = EqPreset.defaultPreset(name)
            SettingsStore.setEqPresets(current + newPreset)
            SettingsStore.setEqSelectedPresetForActiveDevice(name)
        }
    }

    fun renamePreset(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        viewModelScope.launch {
            val current = SettingsStore.eqPresetsFlow
                .stateIn(viewModelScope)
                .value
            if (current.any { it.name == newName }) return@launch
            val updated = current.map { if (it.name == oldName) it.copy(name = newName) else it }
            SettingsStore.setEqPresets(updated)
            val globalSelected = SettingsStore.eqSelectedPresetFlow
                .stateIn(viewModelScope)
                .value
            if (globalSelected == oldName) {
                SettingsStore.setEqSelectedPreset(newName)
            }
            val devicePresets = SettingsStore.eqDevicePresetsFlow
                .stateIn(viewModelScope)
                .value
            devicePresets
                .filterValues { it == oldName }
                .keys
                .forEach { deviceKey ->
                    SettingsStore.setEqDevicePreset(deviceKey, newName)
                }
        }
    }

    fun deletePreset(name: String) {
        viewModelScope.launch {
            val current = SettingsStore.eqPresetsFlow
                .stateIn(viewModelScope)
                .value
            if (current.size <= 1) return@launch
            val updated = current.filter { it.name != name }
            SettingsStore.setEqPresets(updated)
            val selected = SettingsStore.effectiveEqSelectedPresetFlow
                .stateIn(viewModelScope)
                .value
            if (selected == name) {
                SettingsStore.setEqSelectedPresetForActiveDevice(updated.first().name)
            }
            val globalSelected = SettingsStore.eqSelectedPresetFlow
                .stateIn(viewModelScope)
                .value
            if (globalSelected == name) {
                SettingsStore.setEqSelectedPreset(updated.first().name)
            }
            val devicePresets = SettingsStore.eqDevicePresetsFlow
                .stateIn(viewModelScope)
                .value
            devicePresets
                .filterValues { it == name }
                .keys
                .forEach { deviceKey ->
                    SettingsStore.removeEqDevicePreset(deviceKey)
                }
        }
    }

    fun updatePreamp(preamp: Float) {
        val current = uiState.value.activePreset
        val updated = current.copy(preamp = preamp)
        _livePreset.value = updated
        schedulePersist(updated)
    }

    fun updateBand(bandIndex: Int, band: EqBand) {
        val current = (_livePreset.value ?: uiState.value.activePreset)
        if (bandIndex !in current.bands.indices) return
        val newBands = current.bands.toMutableList()
        newBands[bandIndex] = band
        val updated = current.copy(bands = newBands)
        _livePreset.value = updated
        schedulePersist(updated)
    }

    fun addBand() {
        val current = (_livePreset.value ?: uiState.value.activePreset)
        if (current.bands.size >= MAX_BANDS) return
        val newId = (current.bands.maxOfOrNull { it.id } ?: -1) + 1
        val newBand = EqBand(id = newId, type = FilterType.PEAKING, frequency = 1000f, gain = 0f, q = 1f)
        val updated = current.copy(bands = current.bands + newBand)
        _livePreset.value = updated
        schedulePersist(updated)
    }

    fun removeBand(bandIndex: Int) {
        val current = (_livePreset.value ?: uiState.value.activePreset)
        if (current.bands.size <= 1) return
        val newBands = current.bands.toMutableList().also { it.removeAt(bandIndex) }
        val updated = current.copy(bands = newBands)
        _livePreset.value = updated
        schedulePersist(updated)
    }

    private fun schedulePersist(preset: EqPreset) {
        bandPersistJob?.cancel()
        bandPersistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistPreset(preset)
        }
    }

    private suspend fun persistPreset(preset: EqPreset) {
        val current = SettingsStore.eqPresetsFlow
            .stateIn(viewModelScope)
            .value
        val updated = current.map { if (it.name == preset.name) preset else it }
        SettingsStore.setEqPresets(updated)
        _livePreset.value = null
    }

    fun exportPreset(context: Context, presetName: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val preset = uiState.value.presets.firstOrNull { it.name == presetName }
                    ?: return@launch
                val apoText = with(EqPreset.Companion) { preset.toApoString() }
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(apoText)
                }
            } catch (e: Exception) {
                Log.e("EqViewModel", "Export failed", e)
            }
        }
    }

    fun importPreset(context: Context, uri: Uri, name: String) {
        viewModelScope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: return@launch

                val current = SettingsStore.eqPresetsFlow
                    .stateIn(viewModelScope)
                    .value

                val uniqueName = generateUniqueName(name, current.map { it.name })
                val imported = EqPreset.Companion.fromApoString(text, uniqueName)
                    ?: return@launch

                SettingsStore.setEqPresets(current + imported)
                SettingsStore.setEqSelectedPresetForActiveDevice(uniqueName)
            } catch (e: Exception) {
                Log.e("EqViewModel", "Import failed", e)
            }
        }
    }

    private fun generateUniqueName(base: String, existing: List<String>): String {
        if (base !in existing) return base
        var counter = 2
        while ("$base ($counter)" in existing) counter++
        return "$base ($counter)"
    }

    companion object {
        const val MAX_BANDS = 10
        private const val PERSIST_DEBOUNCE_MS = 150L
    }
}
