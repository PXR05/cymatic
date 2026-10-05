package com.pxr.cymatic.audio.usb

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import com.pxr.cymatic.audio.alac.AlacMp4Extractor
import com.pxr.cymatic.audio.dsd.DsfExtractor
import com.pxr.cymatic.audio.flac.IntegerFlacExtractor

@UnstableApi
internal class UsbExtractorsFactory(
    private val directUsb: () -> Boolean = { UsbPlaybackState.routeToUsb }
) : ExtractorsFactory {
    private val normal = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> =
        if (directUsb()) directExtractors() else wrapMp4(normal.createExtractors())

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> =
        if (directUsb()) directExtractors()
        else wrapMp4(normal.createExtractors(uri, responseHeaders))

    private fun directExtractors(): Array<Extractor> =
        arrayOf(
            DsfExtractor(),
            StrictPcmWavExtractor(),
            IntegerFlacExtractor(),
            AlacMp4Extractor(usbOnly = true),
            Mp3Extractor(),
            DirectOggExtractor(),
        )

    private fun wrapMp4(extractors: Array<Extractor>): Array<Extractor> =
        (listOf(DsfExtractor()) + extractors)
            .map {
                if (it is Mp4Extractor) AlacMp4Extractor(usbOnly = false, delegate = it) else it
            }
            .toTypedArray()
}
