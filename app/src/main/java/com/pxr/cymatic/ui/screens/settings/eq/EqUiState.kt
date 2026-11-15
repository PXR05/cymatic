package com.pxr.cymatic.ui.screens.settings.eq

import com.pxr.cymatic.data.model.EqPreset

data class EqUiState(
    val eqEnabled: Boolean = false,
    val presets: List<EqPreset> = emptyList(),
    val selectedPresetName: String = "Flat",
    val activePreset: EqPreset = EqPreset.defaultPreset(),
    val activeDeviceKey: String = "",
    val usesDevicePreset: Boolean = false,
)
