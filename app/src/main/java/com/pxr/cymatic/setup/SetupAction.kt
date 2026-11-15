package com.pxr.cymatic.setup

internal data class SetupAction(
    val label: String,
    val enabled: Boolean = true,
    val showButton: Boolean = true,
    val run: () -> Unit,
)
