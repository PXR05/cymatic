package com.pxr.cymatic.audio.usb

import androidx.media3.common.Format
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.wav.WavExtractor

@UnstableApi
internal class StrictPcmWavExtractor(private val delegate: WavExtractor = WavExtractor()) :
    Extractor by delegate {
    private var layout: UsbPcmLayout? = null

    override fun init(output: ExtractorOutput) {
        delegate.init(
            object : ExtractorOutput by output {
                override fun track(id: Int, type: Int): TrackOutput {
                    val target = output.track(id, type)
                    return object : TrackOutput by target {
                        override fun format(format: Format) {
                            target.format(
                                format.buildUpon().setCustomData(checkNotNull(layout)).build()
                            )
                        }
                    }
                }
            }
        )
    }

    override fun sniff(input: ExtractorInput): Boolean {
        if (!delegate.sniff(input)) return false
        input.resetPeekPosition()
        try {
            val file = ByteArray(12)
            input.peekFully(file, 0, file.size)
            var inspected = 12L
            while (inspected < 16 * 1024 * 1024) {
                val chunk = ByteArray(8)
                input.peekFully(chunk, 0, chunk.size)
                val size = chunk.le32(4)
                if (chunk.copyOfRange(0, 4).contentEquals(byteArrayOf(102, 109, 116, 32))) {
                    if (size !in 16..65536) unsupported("Invalid WAV format chunk")
                    val format = ByteArray(size.toInt())
                    input.peekFully(format, 0, format.size)
                    val type = format.le16(0)
                    val channels = format.le16(2)
                    val rate = format.le32(4)
                    val bits = format.le16(14)
                    val frameBytes = bits / 8 * channels
                    if (
                        channels !in 1..2 ||
                        bits !in listOf(16, 24, 32) ||
                        format.le16(12) != frameBytes ||
                        rate !in 8000..384000 ||
                        format.le32(8) != rate * frameBytes
                    ) {
                        unsupported(
                            "Direct USB requires mono/stereo integer PCM WAV in 16-, 24- or 32-bit containers"
                        )
                    }
                    var validBits = bits
                    if (type == 0xfffe) {
                        val pcmGuid =
                            byteArrayOf(1, 0, 0, 0, 0, 0, 16, 0, -128, 0, 0, -86, 0, 56, -101, 113)
                        if (
                            format.size < 40 ||
                            format.le16(16) < 22 ||
                            format.le16(18) !in listOf(16, 20, 24, 32) ||
                            format.le16(18) > bits ||
                            format.le32(20) !in listOf(0L, if (channels == 1) 4L else 3L) ||
                            !format.copyOfRange(24, 40).contentEquals(pcmGuid)
                        ) {
                            unsupported("Unsupported PCM precision or channel layout")
                        }
                        validBits = format.le16(18)
                    } else if (type != 1)
                        unsupported(
                            "Compressed and floating-point WAV are unsupported in direct USB mode"
                        )
                    layout = UsbPcmLayout(channels, bits / 8, validBits)
                    return true
                }
                val padded = size + (size and 1)
                inspected += 8 + padded
                if (inspected > 16 * 1024 * 1024)
                    unsupported("WAV format header exceeds the inspection limit")
                input.advancePeekPosition(padded.toInt())
            }
            unsupported("WAV format header was not found")
        } finally {
            input.resetPeekPosition()
        }
    }

    private fun unsupported(message: String): Nothing =
        throw ParserException.createForUnsupportedContainerFeature(message)
}
