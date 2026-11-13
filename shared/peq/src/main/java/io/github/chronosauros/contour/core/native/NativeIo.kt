// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

data class WriteOutcome<S>(
    val verified: Boolean,
    val pending: Boolean,
    val reason: String?,
    val readback: S?,
)

internal class NativeIo(val port: NativePort) {
    fun <T> guarded(op: () -> T): T {
        port.guard()
        return op().also { port.guard() }
    }

    fun pause(ms: Long) {
        if (ms > 0) guarded { port.pause(ms) }
    }

    fun send(id: Int, payload: ByteArray) {
        guarded { port.send(id, NativeHidReports.Kind.OUTPUT, payload) }
    }

    fun request(id: Int, payload: ByteArray, check: (ByteArray) -> Boolean): ByteArray = guarded {
        port.request(id, payload, check).also { require(check(it)) { "Uncorrelated native reply" } }
    }

    fun isReplyTimeout(e: java.io.IOException) =
        e.message?.startsWith("Native request timed out") == true

    fun <T> retryTimeouts(attempts: Int = 8, gapMs: Long = 400, op: () -> T): T {
        var last: java.io.IOException? = null
        repeat(attempts) { i ->
            try {
                return op()
            } catch (e: java.io.IOException) {
                if (!isReplyTimeout(e)) throw e
                last = e
                if (i < attempts - 1) pause(gapMs)
            }
        }
        throw requireNotNull(last)
    }
}
