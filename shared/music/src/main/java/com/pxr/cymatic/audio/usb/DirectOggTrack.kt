package com.pxr.cymatic.audio.usb

import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.TrackOutput
import com.pxr.cymatic.audio.flac.BoundedFlacTrack
import com.pxr.cymatic.audio.pcm.BoundedPacketTrack

@UnstableApi
internal class DirectOggTrack(private val target: TrackOutput) : TrackOutput {
    private var flac: BoundedFlacTrack? = null
    private var compressed: BoundedPacketTrack? = null
    private lateinit var selected: TrackOutput

    override fun format(format: Format) {
        flac = null
        compressed = null
        selected =
            if (format.sampleMimeType == MimeTypes.AUDIO_FLAC) {
                BoundedFlacTrack(target).also { flac = it }
            } else {
                BoundedPacketTrack(target) { source ->
                    if (!DirectUsbSourcePolicy.canDecodeWithPlatform(source)) {
                        throw ParserException.createForUnsupportedContainerFeature(
                            "Unsupported direct USB Ogg track"
                        )
                    }
                    MAX_PACKET_BYTES
                }
                    .also { compressed = it }
            }
        selected.format(format)
    }

    override fun sampleData(data: ParsableByteArray, length: Int) =
        selected.sampleData(data, length)

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) =
        selected.sampleData(data, length, sampleDataPart)

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean): Int =
        selected.sampleData(input, length, allowEndOfInput)

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int,
    ): Int = selected.sampleData(input, length, allowEndOfInput, sampleDataPart)

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) = selected.sampleMetadata(timeUs, flags, size, offset, cryptoData)

    fun reset(fromStart: Boolean) {
        flac?.reset(fromStart)
        compressed?.reset()
    }

    fun verifyEnd() {
        flac?.verifyEnd()
        compressed?.verifyEnd()
    }

    private companion object {
        const val MAX_PACKET_BYTES = 1_048_576
    }
}
