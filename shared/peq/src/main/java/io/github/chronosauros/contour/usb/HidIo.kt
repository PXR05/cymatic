// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.usb

import io.github.chronosauros.contour.core.native.NativeHidReports
import java.io.Closeable

interface HidIo : Closeable {

    val isNative: Boolean

    fun nativePayloadSize(id: Int, kind: NativeHidReports.Kind): Int

    fun checkSession()

    fun send(report: ByteArray)

    fun receive(timeoutMs: Int): ByteArray?

    fun requestOnce(report: ByteArray, matches: (ByteArray) -> Boolean): ByteArray

    fun sendNative(id: Int, kind: NativeHidReports.Kind, payload: ByteArray)

    fun requestNative(id: Int, payload: ByteArray, matches: (ByteArray) -> Boolean): ByteArray
}
