package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.ui.components.primitives.PixelDialog
import com.pxr.cymatic.ui.components.primitives.PixelDialogButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddToPlaylistDialog(
    audioId: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontFamily = FontFamily(Font(R.font.pixel))

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var memberIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(audioId) {
        withContext(Dispatchers.IO) {
            val repo = PlaylistRepository.getInstance(context)
            val all = repo.getPlaylists()
            val members = all.filter { playlist ->
                repo.getPlaylistAudio(playlist.id).any { it.id == audioId }
            }.map { it.id }.toSet()
            playlists = all
            memberIds = members
        }
    }

    PixelDialog(
        title = "Add to Playlist",
        onDismissRequest = onDismiss,
        maxHeightRatio = 0.7f,
        widthRatio = 0.8f,
        content = {
            if (playlists.isEmpty()) {
                Text(
                    text = "No playlists yet.",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp, 16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.secondary)
                ) {
                    playlists.forEachIndexed { index, playlist ->
                        val isMember = playlist.id in memberIds

                        PlaylistToggleRow(
                            playlistName = playlist.name,
                            isMember = isMember,
                            fontFamily = fontFamily,
                            onToggle = {
                                scope.launch {
                                    val repo = PlaylistRepository.getInstance(context)
                                    if (isMember) {
                                        withContext(Dispatchers.IO) {
                                            repo.removeAudioFromPlaylist(playlist.id, audioId)
                                        }
                                        memberIds = memberIds - playlist.id
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            repo.addAudioToPlaylist(playlist.id, audioId)
                                        }
                                        memberIds = memberIds + playlist.id
                                    }
                                }
                            }
                        )

                        if (index < playlists.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        buttons = {
            PixelDialogButton(
                text = "Done",
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun PlaylistToggleRow(
    playlistName: String,
    isMember: Boolean,
    fontFamily: FontFamily,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(
                onClick = onToggle,
                indication = null,
                interactionSource = null
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isMember) "I" else "O",
            fontFamily = fontFamily,
            fontSize = 16.sp,
            color = if (isMember) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .background(
                    if (isMember) MaterialTheme.colorScheme.onBackground
                    else Color.Transparent
                )
                .padding(horizontal = 28.dp, vertical = 20.dp)
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(64.dp)
                .background(MaterialTheme.colorScheme.secondary)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = playlistName,
            fontFamily = fontFamily,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
