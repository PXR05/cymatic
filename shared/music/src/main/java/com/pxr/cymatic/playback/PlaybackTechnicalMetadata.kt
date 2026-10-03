package com.pxr.cymatic.playback

import android.os.Bundle
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.audioTechnicalMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@UnstableApi
internal class PlaybackTechnicalMetadata(
    private val player: Player,
    private val repository: AudioRepository,
    private val scope: CoroutineScope,
) : Player.Listener {
    override fun onTracksChanged(tracks: Tracks) {
        val format =
            tracks.groups
                .asSequence()
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .flatMap { group ->
                    (0 until group.length)
                        .asSequence()
                        .filter(group::isTrackSelected)
                        .map(group::getTrackFormat)
                }
                .firstOrNull() ?: return
        val technical = format.audioTechnicalMetadata()
        val item = player.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras
        val changed =
            (technical.bitRate != null && extras?.getLong("bit_rate") != technical.bitRate) ||
                    (technical.sampleRate != null &&
                            extras?.getLong("sample_rate") != technical.sampleRate) ||
                    (technical.codec != null && extras?.getString("format") != technical.codec)
        if (!changed) return
        val updated =
            Bundle(extras ?: Bundle()).apply {
                technical.bitRate?.let { putLong("bit_rate", it) }
                technical.sampleRate?.let { putLong("sample_rate", it) }
                technical.codec?.let { putString("format", it) }
            }
        val description =
            listOfNotNull(
                updated.getString("format")?.substringAfterLast('/')?.uppercase(),
                updated.getLong("sample_rate").takeIf { it > 0 }?.let(::formatSampleRate),
                updated.getLong("bit_rate").takeIf { it > 0 }?.let { "${it / 1000} kbps" },
            )
                .joinToString(" · ")
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            item
                .buildUpon()
                .setMediaMetadata(
                    item.mediaMetadata
                        .buildUpon()
                        .setExtras(updated)
                        .setDescription(description)
                        .build()
                )
                .build(),
        )
        val id = item.mediaId.toLongOrNull() ?: return
        scope.launch {
            try {
                repository.updateTechnicalMetadata(id, technical)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PlaybackMetadata", "Could not save track metadata", e)
            }
        }
    }
}
