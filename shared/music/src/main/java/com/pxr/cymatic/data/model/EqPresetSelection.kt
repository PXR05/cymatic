package com.pxr.cymatic.data.model

internal fun List<EqPreset>.selectedOrDefault(name: String): EqPreset =
    firstOrNull { it.name == name } ?: firstOrNull() ?: EqPreset.defaultPreset()
