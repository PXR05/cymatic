package com.pxr.cymatic.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.pxr.cymatic.R

@Composable
fun QueueSheetContent(
    mediaController: MediaController,
    currentIndex: Int,
    totalTracks: Int,
    listAlpha: Float,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pixel_arrow_up),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(12.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$totalTracks TRACKS IN QUEUE",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(listAlpha),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(count = mediaController.mediaItemCount, key = { it }) { index ->
                val item = mediaController.getMediaItemAt(index)
                val itemTitle = item.mediaMetadata.title?.toString() ?: "Unknown Title"
                val itemArtist = item.mediaMetadata.artist?.toString() ?: ""
                val isCurrent = index == currentIndex

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable(
                            onClick = { onSelect(index) },
                            indication = null,
                            interactionSource = null
                        )
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 11.sp,
                        modifier = Modifier.width(28.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = itemTitle,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (itemArtist.isNotBlank()) {
                            Text(
                                text = itemArtist,
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isCurrent) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pixel_play),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
