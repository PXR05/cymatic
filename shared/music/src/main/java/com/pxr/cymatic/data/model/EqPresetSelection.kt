package com.pxr.cymatic.data.model

fun List<EqPreset>.selectedOrDefault(name: String): EqPreset =
    firstOrNull { it.name == name } ?: firstOrNull() ?: EqPreset.defaultPreset()
