package com.pxr.cymatic.data.eq

import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.store.EqSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class EqPresetRepository {
    val enabled = EqSettingsStore.eqGlobalEnabledFlow
    val presets = EqSettingsStore.eqPresetsFlow
    val selectedName = EqSettingsStore.eqSelectedPresetFlow
    val activeDevice = EqSettingsStore.activeAudioDeviceFlow
    val devicePresets = EqSettingsStore.eqDevicePresetsFlow

    val currentEnabled
        get() = EqSettingsStore.currentEqGlobalEnabled

    val currentPresets
        get() = EqSettingsStore.currentEqPresets

    val currentSelectedName
        get() = EqSettingsStore.currentEqSelectedPreset

    val currentActiveDevice
        get() = EqSettingsStore.currentActiveAudioDevice

    val currentDevicePresets
        get() = EqSettingsStore.currentEqDevicePresets

    suspend fun setEnabled(enabled: Boolean) = EqSettingsStore.setEqGlobalEnabled(enabled)

    suspend fun select(name: String) = EqSettingsStore.setEqSelectedPresetForActiveDevice(name)

    suspend fun add(name: String) = mutation.withLock {
        if (name.isBlank()) return@withLock
        val current = presets.first()
        if (current.any { it.name == name }) return@withLock
        EqSettingsStore.setEqPresets(current + EqPreset.defaultPreset(name))
        select(name)
    }

    suspend fun saveCopy(preset: EqPreset): EqPreset = mutation.withLock {
        val current = presets.first()
        val base = preset.name.trim()
        var name = base
        var suffix = 2
        while (current.any { it.name == name }) name = "$base (${suffix++})"
        preset.copy(name = name).also { EqSettingsStore.setEqPresets(current + it) }
    }

    suspend fun rename(oldName: String, newName: String) = mutation.withLock {
        if (newName.isBlank() || oldName == newName) return@withLock
        val current = presets.first()
        if (current.none { it.name == oldName } || current.any { it.name == newName })
            return@withLock
        EqSettingsStore.setEqPresets(
            current.map { if (it.name == oldName) it.copy(name = newName) else it }
        )
        if (selectedName.first() == oldName) EqSettingsStore.setEqSelectedPreset(newName)
        devicePresets
            .first()
            .filterValues { it == oldName }
            .keys
            .forEach {
                EqSettingsStore.setEqDevicePreset(it, newName)
            }
    }

    suspend fun delete(name: String) = mutation.withLock {
        val current = presets.first()
        val remaining = current.filter { it.name != name }
        if (remaining.isEmpty() || remaining.size == current.size) return@withLock
        EqSettingsStore.setEqPresets(remaining)
        if (EqSettingsStore.effectiveEqSelectedPresetFlow.first() == name)
            select(remaining.first().name)
        if (selectedName.first() == name)
            EqSettingsStore.setEqSelectedPreset(remaining.first().name)
        devicePresets
            .first()
            .filterValues { it == name }
            .keys
            .forEach {
                EqSettingsStore.removeEqDevicePreset(it)
            }
    }

    suspend fun update(preset: EqPreset) = mutation.withLock {
        EqSettingsStore.setEqPresets(
            presets.first().map { if (it.name == preset.name) preset else it }
        )
    }

    private companion object {
        val mutation = Mutex()
    }
}
