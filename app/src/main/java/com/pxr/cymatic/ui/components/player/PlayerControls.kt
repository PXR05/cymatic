package com.pxr.cymatic.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.pxr.cymatic.R

@Composable
fun Controls(
    isPlaying: Boolean,
    isShuffling: Boolean,
    repeatMode: Int,
    onClick: Map<String, () -> Unit>,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.onBackground
    val inactiveColor = MaterialTheme.colorScheme.secondary
    val shuffleColor = if (isShuffling) activeColor else inactiveColor
    val repeatColor = if (repeatMode == Player.REPEAT_MODE_OFF) inactiveColor else activeColor
    val fontFamily = FontFamily(Font(R.font.pixel))
    val subscript = SpanStyle(
        fontSize = 10.sp
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(16.dp)
                .clickable(
                    onClick = onClick["shuffle"] ?: {},
                    indication = null,
                    interactionSource = null
                ),
            text = "S",
            color = shuffleColor,
            fontSize = 16.sp,
            fontFamily = fontFamily,
        )

        Icon(
            painter = painterResource(R.drawable.ic_pixel_previous),
            contentDescription = "Previous",
            tint = activeColor,
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    onClick = onClick["previous"] ?: {},
                    indication = null,
                    interactionSource = null
                )
        )

        Icon(
            painter = painterResource(if (isPlaying) R.drawable.ic_pixel_pause else R.drawable.ic_pixel_play),
            contentDescription = "Play/Pause",
            tint = activeColor,
            modifier = Modifier
                .size(42.dp)
                .clickable(
                    onClick = onClick["play_pause"] ?: {},
                    indication = null,
                    interactionSource = null
                )
        )

        Icon(
            painter = painterResource(R.drawable.ic_pixel_next),
            contentDescription = "Next",
            tint = activeColor,
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    onClick = onClick["next"] ?: {},
                    indication = null,
                    interactionSource = null
                )
        )
        Text(
            modifier = Modifier
                .padding(16.dp)
                .clickable(
                    onClick = onClick["repeat"] ?: {},
                    indication = null,
                    interactionSource = null
                ),
            text = buildAnnotatedString {
                append("R")
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> withStyle(subscript) { append("1") }
                    Player.REPEAT_MODE_ALL -> withStyle(subscript) { append("A") }
                    else -> withStyle(subscript) { append("0") }
                }
            },
            color = repeatColor,
            fontSize = 16.sp,
            fontFamily = fontFamily,
        )
    }
}


