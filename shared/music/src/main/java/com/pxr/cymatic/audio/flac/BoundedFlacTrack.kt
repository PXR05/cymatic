package com.pxr.cymatic.audio.flac

import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.FlacFrameReader
import androidx.media3.extractor.FlacStreamMetadata
import androidx.media3.extractor.TrackOutput
import com.pxr.cymatic.flac.FlacFrameDecoder

@UnstableApi
internal class BoundedFlacTrack(private val target: TrackOutput) : TrackOutput by target {
    private var metadata: FlacStreamMetadata? = null
    private val prefix = ByteArray(32)
    private var prefixSize = 0
    private var frameBytes = 0
    private var nextSample = 0L

    override fun format(format: Format) {
        metadata =
            format.integerFlacMetadata()
                ?: throw ParserException.createForUnsupportedContainerFeature(
                    "Direct USB requires 16-, 20-, 24- or 32-bit mono/stereo FLAC with valid STREAMINFO"
                )
        target.format(format)
    }

    override fun sampleData(data: ParsableByteArray, length: Int) =
        sampleData(data, length, TrackOutput.SAMPLE_DATA_PART_MAIN)

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (
            length < 0 ||
            length > FlacFrameDecoder.MAX_FRAME_BYTES - frameBytes ||
            sampleDataPart != TrackOutput.SAMPLE_DATA_PART_MAIN
        )
            malformed("FLAC frame exceeds the input limit")
        val capture = minOf(length, prefix.size - prefixSize)
        data.data.copyInto(prefix, prefixSize, data.position, data.position + capture)
        prefixSize += capture
        frameBytes += length
        target.sampleData(data, length, sampleDataPart)
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean): Int =
        sampleData(input, length, allowEndOfInput, TrackOutput.SAMPLE_DATA_PART_MAIN)

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int,
    ): Int =
        throw ParserException.createForUnsupportedContainerFeature(
            "Unexpected FLAC extraction input"
        )

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) {
        val info = metadata ?: malformed("FLAC STREAMINFO is missing")
        if (
            size <= 0 || size != frameBytes || offset != 0 || cryptoData != null || prefixSize < 6
        ) {
            malformed("Incomplete FLAC frame")
        }
        try {
            val header = ParsableByteArray(prefix, prefixSize)
            val marker = ((prefix[0].toInt() and 0xff) shl 8) or (prefix[1].toInt() and 0xff)
            val sample = FlacFrameReader.SampleNumberHolder()
            if (
                (marker and 0xfffc) != 0xfff8 ||
                !FlacFrameReader.checkAndReadFrameHeader(header, info, marker, sample)
            ) {
                malformed("Invalid FLAC frame header")
            }
            header.position = 4
            header.readUtf8EncodedLong()
            val blockSize =
                FlacFrameReader.readFrameBlockSizeSamplesFromKey(
                    header,
                    (prefix[2].toInt() and 0xff) shr 4,
                )
            if (
                blockSize !in 1..info.maxBlockSizeSamples ||
                (nextSample >= 0 && sample.sampleNumber != nextSample)
            ) {
                malformed("Missing or overlapping FLAC frames")
            }
            nextSample = sample.sampleNumber + blockSize
            if (info.totalSamples > 0 && nextSample > info.totalSamples)
                malformed("FLAC frame exceeds the declared duration")
        } catch (e: ParserException) {
            throw e
        } catch (e: RuntimeException) {
            throw ParserException.createForMalformedContainer("Invalid FLAC frame header", e)
        }
        target.sampleMetadata(timeUs, flags, size, offset, cryptoData)
        frameBytes = 0
        prefixSize = 0
    }

    fun reset(fromStart: Boolean) {
        frameBytes = 0
        prefixSize = 0
        nextSample = if (fromStart) 0 else -1
    }

    fun verifyEnd() {
        val total = metadata?.totalSamples ?: 0
        if (frameBytes != 0 || (total > 0 && nextSample >= 0 && nextSample != total))
            malformed("Truncated FLAC stream")
    }

    private fun malformed(message: String): Nothing =
        throw ParserException.createForMalformedContainer(message, null)
}
