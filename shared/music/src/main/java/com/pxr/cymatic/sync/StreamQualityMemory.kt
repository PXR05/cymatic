package com.pxr.cymatic.sync

import java.util.Collections

internal object StreamQualityMemory {
    private const val MAX_ENTRIES = 64
    private val map: MutableMap<Long, StreamQuality> =
        Collections.synchronizedMap(
            object : LinkedHashMap<Long, StreamQuality>(MAX_ENTRIES, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, StreamQuality>?): Boolean =
                    size > MAX_ENTRIES
            }
        )

    fun put(audioId: Long, quality: StreamQuality) {
        map[audioId] = quality
    }

    fun get(audioId: Long): StreamQuality? = map[audioId]

    fun clear() {
        map.clear()
    }
}
