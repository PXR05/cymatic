package com.pxr.cymatic.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.model.AudioMetadata

@Composable
fun FileInfo(
    metadata: AudioMetadata,
    modifier: Modifier = Modifier,
) {
    val fontFamily = FontFamily(Font(R.font.pixel))

    fun parseBitRate(bitRate: Long) = when {
        bitRate >= 1_000 -> "${bitRate / 1_000}K"
        else -> "$bitRate"
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = metadata.format?.split("/")?.lastOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.background,
            fontSize = 14.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.onBackground)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )

        Text(
            text = metadata.bitRate?.let { parseBitRate(it) } ?: "?",
            color = MaterialTheme.colorScheme.background,
            fontSize = 14.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.onBackground)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}