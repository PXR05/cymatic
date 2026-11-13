package com.pxr.cymatic.ui.screens.settings.eq

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.eq.EqPresetDocuments
import com.pxr.cymatic.data.eq.EqPresetRepository
import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.model.FilterType
import com.pxr.cymatic.data.model.selectedOrDefault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EqViewModel : ViewModel() {
    private val repository = EqPresetRepository()
    private var bandPersistJob: Job? = null
    private val mutableLivePreset = MutableStateFlow<EqPreset?>(null)
    val livePreset = mutableLivePreset.asStateFlow()

    val uiState: StateFlow<EqUiState> =
        combine(
                repository.enabled,
                repository.presets,
                repository.selectedName,
                repository.activeDevice,
                repository.devicePresets,
                ::buildState,
            )
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                buildState(
                    repository.currentEnabled,
                    repository.currentPresets,
                    repository.currentSelectedName,
                    repository.currentActiveDevice,
                    repository.currentDevicePresets,
                ),
            )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun selectPreset(name: String) {
        viewModelScope.launch {
            repository.select(name)
            mutableLivePreset.value = null
        }
    }

    fun addPreset(name: String) {
        viewModelScope.launch { repository.add(name) }
    }

    fun renamePreset(oldName: String, newName: String) {
        viewModelScope.launch { repository.rename(oldName, newName) }
    }

    fun deletePreset(name: String) {
        viewModelScope.launch { repository.delete(name) }
    }

    fun updatePreamp(preamp: Float) = edit { it.copy(preamp = preamp) }

    fun updateBand(bandIndex: Int, band: EqBand) = edit { preset ->
        if (bandIndex !in preset.bands.indices) preset
        else
            preset.copy(
                bands =
                    preset.bands.mapIndexed { index, current ->
                        if (index == bandIndex) band else current
                    }
            )
    }

    fun addBand() = edit { preset ->
        if (preset.bands.size >= MAX_BANDS) preset
        else
            preset.copy(
                bands =
                    preset.bands +
                        EqBand(
                            id = (preset.bands.maxOfOrNull { it.id } ?: -1) + 1,
                            type = FilterType.PEAKING,
                            frequency = 1000f,
                            gain = 0f,
                            q = 1f,
                        )
            )
    }

    fun removeBand(bandIndex: Int) = edit { preset ->
        if (preset.bands.size <= 1 || bandIndex !in preset.bands.indices) preset
        else preset.copy(bands = preset.bands.filterIndexed { index, _ -> index != bandIndex })
    }

    private fun edit(transform: (EqPreset) -> EqPreset) {
        val current = mutableLivePreset.value ?: uiState.value.activePreset
        val updated = transform(current)
        if (updated == current) return
        mutableLivePreset.value = updated
        bandPersistJob?.cancel()
        bandPersistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            repository.update(updated)
            if (mutableLivePreset.value == updated) mutableLivePreset.value = null
        }
    }

    fun exportPreset(context: Context, presetName: String, uri: Uri) {
        documentOperation("Export") {
            val preset =
                uiState.value.presets.firstOrNull { it.name == presetName }
                    ?: return@documentOperation
            EqPresetDocuments(context.contentResolver).write(uri, preset)
        }
    }

    fun importPreset(context: Context, uri: Uri, name: String) {
        documentOperation("Import") {
            val preset =
                EqPresetDocuments(context.contentResolver).read(uri, name)
                    ?: return@documentOperation
            val saved = repository.saveCopy(preset)
            repository.select(saved.name)
        }
    }

    private fun documentOperation(action: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("EqViewModel", "$action failed", e)
            }
        }
    }

    private fun buildState(
        enabled: Boolean,
        presets: List<EqPreset>,
        selected: String,
        device: String,
        devicePresets: Map<String, String>,
    ): EqUiState {
        val devicePreset = devicePresets[device]
        val active = presets.selectedOrDefault(devicePreset ?: selected)
        return EqUiState(enabled, presets, active.name, active, device, devicePreset != null)
    }

    companion object {
        const val MAX_BANDS = 10
        private const val PERSIST_DEBOUNCE_MS = 150L
    }
}
