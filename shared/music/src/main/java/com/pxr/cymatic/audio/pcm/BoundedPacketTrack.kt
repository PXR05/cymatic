package com.pxr.cymatic.audio.pcm

import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.TrackOutput

@UnstableApi
internal class BoundedPacketTrack(
    private val target: TrackOutput,
    private val limitForFormat: (Format) -> Int?,
) : TrackOutput by target {
    private var packetLimit: Int? = null
    private var packetBytes = 0

    override fun format(format: Format) {
        packetLimit = limitForFormat(format)
        target.format(format)
    }

    override fun sampleData(data: ParsableByteArray, length: Int) =
        sampleData(data, length, TrackOutput.SAMPLE_DATA_PART_MAIN)

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        checkSize(length, sampleDataPart)
        target.sampleData(data, length, sampleDataPart)
        if (packetLimit != null) packetBytes += length
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean): Int =
        sampleData(input, length, allowEndOfInput, TrackOutput.SAMPLE_DATA_PART_MAIN)

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int,
    ): Int {
        checkSize(length, sampleDataPart)
        val read = target.sampleData(input, length, allowEndOfInput, sampleDataPart)
        if (packetLimit != null && read > 0) packetBytes += read
        return read
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) {
        if (packetLimit != null) {
            if (size <= 0 || size != packetBytes || offset != 0 || cryptoData != null)
                malformed("Incomplete audio packet")
            packetBytes = 0
        }
        target.sampleMetadata(timeUs, flags, size, offset, cryptoData)
    }

    private fun checkSize(length: Int, part: Int) {
        val limit = packetLimit ?: return
        if (
            length < 0 || length > limit - packetBytes || part != TrackOutput.SAMPLE_DATA_PART_MAIN
        ) {
            malformed("Audio packet exceeds the input limit")
        }
    }

    fun reset() {
        packetBytes = 0
    }

    fun verifyEnd() {
        if (packetBytes != 0) malformed("Truncated audio packet")
    }

    private fun malformed(message: String): Nothing =
        throw ParserException.createForMalformedContainer(message, null)
}
