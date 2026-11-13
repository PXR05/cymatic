package com.pxr.cymatic.ui.components.eq

import java.util.Locale

internal fun formatEqGain(value: Float) = String.format(Locale.US, "%.1f dB", value)
