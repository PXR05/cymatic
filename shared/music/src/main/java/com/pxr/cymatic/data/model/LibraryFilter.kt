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

sealed interface LibrarySourceFilter {
    val value: String

    fun matches(sourceId: String?): Boolean

    data object All : LibrarySourceFilter {
        override val value = "all"
        override fun matches(sourceId: String?) = true
    }

    data object Local : LibrarySourceFilter {
        override val value = "local"
        override fun matches(sourceId: String?) = sourceId == null
    }

    data class Source(val id: String) : LibrarySourceFilter {
        override val value = "source:$id"
        override fun matches(sourceId: String?) = sourceId == id
    }

    companion object {
        fun from(raw: String?): LibrarySourceFilter =
            when {
                raw.isNullOrBlank() || raw == "all" -> All
                raw == "local" -> Local
                raw.startsWith("source:") -> {
                    val id = raw.removePrefix("source:")
                    if (id.isNotBlank()) Source(id) else All
                }

                else -> All
            }
    }
}
