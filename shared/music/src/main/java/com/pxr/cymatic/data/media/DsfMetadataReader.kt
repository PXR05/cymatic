package com.pxr.cymatic.data.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.Id3Decoder
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.pxr.cymatic.audio.dsd.DSD_MIME_TYPE
import com.pxr.cymatic.audio.dsd.DSF_HEADER_BYTES
import com.pxr.cymatic.audio.dsd.DsfHeader
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

@UnstableApi
internal data class DsfMetadata(
    val header: DsfHeader,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
) {
    fun technical() =
        AudioTechnicalMetadata(
            header.sampleRate.toLong() * header.channels,
            header.sampleRate.toLong(),
            DSD_MIME_TYPE,
        )
}

@UnstableApi
internal fun readDsfMetadata(context: Context, uri: Uri): DsfMetadata? {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
    val length = descriptor.statSize
    return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
        val header = DsfHeader.parse(input.readExactly(DSF_HEADER_BYTES), length)
        val metadata = runCatching {
            if (header.metadataOffset == 0L) return@runCatching null
            input.channel.position(header.metadataOffset)
            val prefix = input.readExactly(10)
            if (String(prefix, 0, 3, Charsets.US_ASCII) != "ID3") return@runCatching null
            require((6..9).all { prefix[it].toInt() and 0x80 == 0 })
            val bytes = (6..9).fold(0) { size, index -> (size shl 7) or prefix[index].toInt() }
            require(
                bytes in 0..2_097_152 &&
                    bytes.toLong() + 10 <= header.fileSize - header.metadataOffset
            )
            val tag = prefix + input.readExactly(bytes)
            Id3Decoder().decode(tag, tag.size)
        }
            .getOrNull()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var picture: ApicFrame? = null
        for (index in 0 until (metadata?.length() ?: 0)) {
            when (val frame = metadata!![index]) {
                is TextInformationFrame -> {
                    val value = frame.values.firstOrNull()?.takeIf { it.isNotBlank() }
                    when (frame.id) {
                        "TIT2",
                        "TT2" -> title = value
                        "TPE1",
                        "TP1" -> artist = value
                        "TALB",
                        "TAL" -> album = value
                    }
                }
                is ApicFrame -> if (picture == null || frame.pictureType == 3) picture = frame
            }
        }
        val artwork = picture?.let { image ->
            runCatching {
                val directory = File(context.filesDir, "dsf-artwork").apply { mkdirs() }
                val digest =
                    MessageDigest.getInstance("SHA-256").digest(image.pictureData).joinToString(
                        ""
                    ) {
                        "%02x".format(it.toInt() and 0xff)
                    }
                val file = File(directory, "$digest.img")
                if (!file.exists()) {
                    val temporary = File.createTempFile("dsf-", ".tmp", directory)
                    try {
                        temporary.writeBytes(image.pictureData)
                        check(temporary.renameTo(file) || file.exists())
                    } finally {
                        temporary.delete()
                    }
                }
                file.toUri().toString()
            }
                .getOrNull()
        }
        DsfMetadata(header, title, artist, album, artwork)
    }
}

internal fun InputStream.readExactly(length: Int): ByteArray {
    val bytes = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(bytes, offset, length - offset)
        if (read <= 0) throw java.io.EOFException("Truncated DSF file")
        offset += read
    }
    return bytes
}
