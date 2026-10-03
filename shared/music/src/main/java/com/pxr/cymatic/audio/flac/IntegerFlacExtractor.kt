package com.pxr.cymatic.audio.flac

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.flac.FlacExtractor

@UnstableApi
internal class IntegerFlacExtractor(private val delegate: FlacExtractor = FlacExtractor()) :
    Extractor by delegate {
    private var track: BoundedFlacTrack? = null

    override fun init(output: ExtractorOutput) {
        delegate.init(
            object : ExtractorOutput by output {
                override fun track(id: Int, type: Int): TrackOutput =
                    track ?: BoundedFlacTrack(output.track(id, type)).also { track = it }
            }
        )
    }

    override fun seek(position: Long, timeUs: Long) {
        track?.reset(position == 0L)
        delegate.seek(position, timeUs)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val result = delegate.read(input, seekPosition)
        if (result == Extractor.RESULT_END_OF_INPUT) track?.verifyEnd()
        return result
    }
}
