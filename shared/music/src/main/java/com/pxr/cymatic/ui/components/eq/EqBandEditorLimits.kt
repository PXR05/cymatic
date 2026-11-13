package com.pxr.cymatic.ui.components.eq

import com.pxr.cymatic.data.model.FilterType

data class EqBandEditorLimits(
    val frequency: ClosedFloatingPointRange<Float> = 20f..20000f,
    val gain: ClosedFloatingPointRange<Float> = -12f..12f,
    val q: ClosedFloatingPointRange<Float> = 0.1f..2f,
    val types: List<FilterType> = FilterType.entries,
    val gainStep: Float = 0.1f,
    val showGain: Boolean = true,
)
