// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.usb

import android.os.SystemClock
import io.github.chronosauros.contour.core.WalkPlay
import io.github.chronosauros.contour.core.native.NativeHidReports
import io.github.chronosauros.contour.core.native.NativePort

internal class WalkPlayPort(
    private val t: HidIo,
    device: DacHandle,
    private val reportId: Int = WalkPlay.REPORT_ID,
    private val tick: () -> Unit = {},
) : NativePort {
    override val deviceKey = device.key

    var firstSendMs = 0L
        private set

    var lastSendEndMs = 0L
        private set

    override fun guard() = t.checkSession()

    override fun payloadSize(id: Int, kind: NativeHidReports.Kind): Int =
        t.nativePayloadSize(id, kind)

    override fun pause(ms: Long) {
        t.checkSession()
        Thread.sleep(ms)
        t.checkSession()
        lastSendEndMs = SystemClock.elapsedRealtime()
    }

    override fun send(id: Int, kind: NativeHidReports.Kind, payload: ByteArray) {
        require(id == reportId && kind == NativeHidReports.Kind.OUTPUT) {
            "Only output report 0x%02x".format(reportId)
        }
        if (firstSendMs == 0L) firstSendMs = SystemClock.elapsedRealtime()
        if (t.isNative) t.sendNative(id, kind, payload)
        else t.send(byteArrayOf(id.toByte()) + payload)
        lastSendEndMs = SystemClock.elapsedRealtime()
        tick()
    }

    override fun request(id: Int, payload: ByteArray, matches: (ByteArray) -> Boolean): ByteArray {
        require(id == reportId) { "Only report 0x%02x".format(reportId) }
        val reply =
            if (t.isNative) t.requestNative(id, payload, matches)
            else
                t.requestOnce(byteArrayOf(id.toByte()) + payload) {
                        it.size > 1 && it[0] == id.toByte() && matches(it.copyOfRange(1, it.size))
                    }
                    .let { it.copyOfRange(1, it.size) }
        tick()
        return reply
    }

    override fun log(message: String) = UsbLog.line(message)
}
