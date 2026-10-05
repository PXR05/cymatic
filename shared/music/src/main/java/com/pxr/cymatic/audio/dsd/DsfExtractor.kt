package com.pxr.cymatic.audio.dsd

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.io.EOFException

@UnstableApi
internal class DsfExtractor : Extractor {
    private lateinit var output: ExtractorOutput
    private lateinit var track: TrackOutput
    private var header: DsfHeader? = null
    private var block = 0L
    private var packet = ByteArray(0)

    override fun sniff(input: ExtractorInput): Boolean {
        val signature = ByteArray(12)
        return try {
            input.peekFully(signature, 0, signature.size, true) &&
                signature.take(4) == listOf(68.toByte(), 83.toByte(), 68.toByte(), 32.toByte()) &&
                signature[4] == 28.toByte() &&
                signature.drop(5).all { it == 0.toByte() }
        } catch (_: EOFException) {
            false
        } finally {
            input.resetPeekPosition()
        }
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        track = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (header == null) {
            val bytes = ByteArray(DSF_HEADER_BYTES)
            input.readFully(bytes, 0, bytes.size)
            val info =
                try {
                    DsfHeader.parse(bytes, input.length)
                } catch (e: IllegalArgumentException) {
                    throw ParserException.createForMalformedContainer(e.message, e)
                }
            header = info
            packet = ByteArray(info.blockBytes + 4)
            track.format(
                Format.Builder()
                    .setSampleMimeType(DSD_MIME_TYPE)
                    .setChannelCount(info.channels)
                    .setSampleRate(info.sampleRate)
                    .setAverageBitrate(info.sampleRate * info.channels)
                    .setMaxInputSize(packet.size)
                    .setInitializationData(listOf(bytes))
                    .build()
            )
            output.seekMap(DsfSeekMap(info))
        }
        val info = checkNotNull(header)
        if (block >= info.blockCount) return Extractor.RESULT_END_OF_INPUT
        val position = info.position(block)
        if (input.position != position) {
            seekPosition.position = position
            return Extractor.RESULT_SEEK
        }
        val samples =
            minOf(info.blockSamples.toLong(), info.sampleCount - block * info.blockSamples).toInt()
        repeat(4) { packet[it] = (samples ushr (it * 8)).toByte() }
        input.readFully(packet, 4, info.blockBytes)
        track.sampleData(ParsableByteArray(packet), packet.size)
        track.sampleMetadata(info.timeUs(block), C.BUFFER_FLAG_KEY_FRAME, packet.size, 0, null)
        block++
        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        block =
            header?.let {
                ((position - DSF_HEADER_BYTES).coerceAtLeast(0) / it.blockBytes).coerceAtMost(
                    it.blockCount - 1
                )
            } ?: 0
    }

    override fun release() = Unit
}

@UnstableApi
private class DsfSeekMap(private val header: DsfHeader) : SeekMap {
    override fun isSeekable() = true

    override fun getDurationUs() = header.durationUs

    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        val sample = timeUs.coerceIn(0, header.durationUs) * header.sampleRate / 1_000_000
        val block = (sample / header.blockSamples - 1).coerceIn(0, header.blockCount - 1)
        return SeekMap.SeekPoints(SeekPoint(header.timeUs(block), header.position(block)))
    }
}
