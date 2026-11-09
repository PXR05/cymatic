package com.pxr.cymatic.data.model

enum class LibraryFilter(val value: String, val label: String) {
    ALL("all", "All music"),
    OFFLINE("offline", "Offline only"),
    NOT_OFFLINE("not_offline", "Not offline");

    fun matches(availability: LibraryAvailability): Boolean = when (this) {
        ALL -> true
        OFFLINE -> availability == LibraryAvailability.OFFLINE
        NOT_OFFLINE -> availability != LibraryAvailability.OFFLINE
    }

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: ALL
    }
}
