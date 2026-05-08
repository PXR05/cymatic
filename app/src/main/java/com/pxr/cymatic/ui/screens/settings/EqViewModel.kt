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
    val eqEnabled: Boolean = true,
    val presets: List<EqPreset> = emptyList(),
    val selectedPresetName: String = "Flat",
    val activePreset: EqPreset = EqPreset.defaultPreset()
)

class EqViewModel : ViewModel() {

    val uiState: StateFlow<EqUiState> = combine(
        SettingsStore.eqGlobalEnabledFlow,
        SettingsStore.eqPresetsFlow,
        SettingsStore.eqSelectedPresetFlow
    ) { enabled, presets, selectedName ->
        val active = presets.firstOrNull { it.name == selectedName }
            ?: presets.firstOrNull()
            ?: EqPreset.defaultPreset()
        EqUiState(
            eqEnabled = enabled,
            presets = presets,
            selectedPresetName = active.name,
            activePreset = active
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EqUiState()
    )

    private var bandPersistJob: Job? = null

    private val _livePreset = MutableStateFlow<EqPreset?>(null)
    val livePreset: StateFlow<EqPreset?> = _livePreset

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setEqGlobalEnabled(enabled)
        }
    }

    fun selectPreset(name: String) {
        viewModelScope.launch {
            SettingsStore.setEqSelectedPreset(name)
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
            SettingsStore.setEqSelectedPreset(name)
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
            val selected = SettingsStore.eqSelectedPresetFlow
                .stateIn(viewModelScope)
                .value
            if (selected == oldName) {
                SettingsStore.setEqSelectedPreset(newName)
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
            val selected = SettingsStore.eqSelectedPresetFlow
                .stateIn(viewModelScope)
                .value
            if (selected == name) {
                SettingsStore.setEqSelectedPreset(updated.first().name)
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
                SettingsStore.setEqSelectedPreset(uniqueName)
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
