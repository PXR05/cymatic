package com.pxr.cymatic.audio.usb

internal fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xff

internal fun ByteArray.le16(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)

internal fun ByteArray.le24(offset: Int): Int = le16(offset) or (u8(offset + 2) shl 16)

internal fun ByteArray.le32(offset: Int): Long =
    (0..3).fold(0L) { value, index ->
        value or (u8(offset + index).toLong() shl (index * 8))
    }
