package com.pxr.cymatic.sync

import kotlinx.coroutines.channels.Channel

internal class CoalescingWorkQueue<T>(private val key: (T) -> String) {
    private val pending = mutableSetOf<String>()
    private val channel = Channel<T>(Channel.UNLIMITED)

    fun offer(value: T): Boolean = synchronized(pending) {
        val id = key(value)
        if (!pending.add(id)) return@synchronized false
        if (channel.trySend(value).isSuccess) true else {
            pending.remove(id)
            false
        }
    }

    suspend fun receive(): T {
        val value = channel.receive()
        synchronized(pending) { pending.remove(key(value)) }
        return value
    }
}
