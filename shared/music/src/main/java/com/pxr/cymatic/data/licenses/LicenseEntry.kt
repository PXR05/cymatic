package com.pxr.cymatic.data.licenses

internal data class LicenseEntry(
    val title: String,
    val assets: List<String>,
    val summary: String = "",
    val licenseNames: Set<String> = emptySet(),
)
