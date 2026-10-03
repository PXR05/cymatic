package com.pxr.cymatic.audio.usb

import java.util.concurrent.atomic.AtomicReference

internal object UsbOutputOwnership {
    private val owner = AtomicReference<Any?>(null)
    val inUse: Boolean
        get() = owner.get() != null

    fun acquire(token: Any) {
        check(
            owner.compareAndSet(
                null,
                token,
            )
        ) {
            "DAC is in use; stop direct USB playback before probing"
        }
    }

    fun release(token: Any) {
        owner.compareAndSet(token, null)
    }
}
