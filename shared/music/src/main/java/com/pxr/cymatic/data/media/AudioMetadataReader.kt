package com.pxr.cymatic.data.media

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.inspector.MetadataRetriever
import com.pxr.cymatic.audio.usb.UsbExtractorsFactory
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withTimeoutOrNull

@UnstableApi
internal suspend fun enrichAudioMetadata(
    context: Context,
    records: List<AudioEntity>,
): List<AudioEntity> =
    records.chunked(4).flatMap { chunk ->
        coroutineScope {
            chunk
                .map { record ->
                    async {
                        if (!record.needsTechnicalMetadata()) return@async record
                        val metadata =
                            readAudioTechnicalMetadata(context, record.uri.toUri())
                                ?: return@async record
                        record.copy(
                            bitRate = metadata.bitRate ?: record.bitRate,
                            sampleRate = metadata.sampleRate ?: record.sampleRate,
                            format = metadata.codec ?: record.format,
                        )
                    }
                }
                .awaitAll()
        }
    }

@UnstableApi
internal suspend fun readAudioTechnicalMetadata(
    context: Context,
    uri: Uri,
): AudioTechnicalMetadata? =
    try {
        withTimeoutOrNull(3000L) {
            MetadataRetriever.Builder(context, MediaItem.fromUri(uri))
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        context,
                        UsbExtractorsFactory { false },
                    )
                )
                .build()
                .use { retriever ->
                    val groups = retriever.retrieveTrackGroups().await()
                    val formats =
                        (0 until groups.length).flatMap { index ->
                            val group = groups[index]
                            if (group.type == C.TRACK_TYPE_AUDIO)
                                (0 until group.length).map(group::getFormat)
                            else emptyList()
                        }
                    val format =
                        formats.firstOrNull { it.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 }
                            ?: formats.firstOrNull()
                    format?.audioTechnicalMetadata()
                }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

private fun AudioEntity.needsTechnicalMetadata(): Boolean =
    format?.lowercase(Locale.ROOT) in
        setOf(
            "audio/alac",
            "audio/x-alac",
            "audio/mp4",
            "audio/x-m4a",
            "audio/m4a",
            "audio/mp4a-latm",
            "audio/mpeg",
            "audio/mp3",
            "audio/ogg",
            "application/ogg",
            "audio/vorbis",
            "audio/opus",
            "audio/x-ogg",
        ) || bitRate == null || sampleRate == null
