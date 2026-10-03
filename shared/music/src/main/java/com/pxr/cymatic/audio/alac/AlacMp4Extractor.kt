package com.pxr.cymatic.audio.alac

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import com.pxr.cymatic.audio.pcm.BoundedPacketTrack

@UnstableApi
internal class AlacMp4Extractor(
    private val usbOnly: Boolean,
    private val delegate: Mp4Extractor = Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED),
) : Extractor by delegate {
    private val tracks = mutableMapOf<Int, BoundedPacketTrack>()

    override fun init(output: ExtractorOutput) {
        delegate.init(
            object : ExtractorOutput by output {
                override fun track(id: Int, type: Int): TrackOutput {
                    if (usbOnly && type != C.TRACK_TYPE_AUDIO) return DiscardingTrackOutput()
                    return tracks.getOrPut(id) {
                        BoundedPacketTrack(output.track(id, type)) { format ->
                            val metadata = format.integerAlacMetadata()
                            if (usbOnly && metadata == null)
                                throw ParserException.createForUnsupportedContainerFeature(
                                    "Direct USB requires unencrypted mono/stereo ALAC in M4A"
                                )
                            if (format.sampleMimeType == MimeTypes.AUDIO_ALAC)
                                metadata?.maxPacketBytes
                            else null
                        }
                    }
                }
            }
        )
    }

    override fun seek(position: Long, timeUs: Long) {
        tracks.values.forEach { it.reset() }
        delegate.seek(position, timeUs)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val result = delegate.read(input, seekPosition)
        if (result == Extractor.RESULT_END_OF_INPUT) tracks.values.forEach { it.verifyEnd() }
        return result
    }
}
