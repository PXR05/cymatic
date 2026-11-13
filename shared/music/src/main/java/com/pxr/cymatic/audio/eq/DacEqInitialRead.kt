package com.pxr.cymatic.audio.eq

internal class DacEqInitialRead {
    private val attempted = mutableSetOf<String>()

    fun retainConnections(keys: Set<String>) {
        attempted.retainAll(keys)
    }

    fun shouldRead(key: String?, permission: Boolean, busy: Boolean, pending: Boolean): Boolean =
        key != null && permission && !busy && !pending && attempted.add(key)
}
