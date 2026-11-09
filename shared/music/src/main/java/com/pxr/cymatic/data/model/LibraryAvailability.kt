package com.pxr.cymatic.data.model

enum class LibraryAvailability(val label: String) {
    OFFLINE("Offline"),
    PARTIAL("Partially offline"),
    STREAM("Stream only");

    companion object {
        fun fromCounts(offline: Int, total: Int, remote: Boolean = true): LibraryAvailability =
            when {
                total == 0 -> if (remote) STREAM else OFFLINE
                offline >= total -> OFFLINE
                offline > 0 -> PARTIAL
                else -> STREAM
            }
    }
}
