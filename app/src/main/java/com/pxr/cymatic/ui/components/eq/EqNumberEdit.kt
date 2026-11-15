package com.pxr.cymatic.ui.components.eq

internal data class EqNumberEdit(
    val title: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val format: (Float) -> String,
    val inputToValue: (Float) -> Float = { it },
    val save: (Float) -> Unit,
)
