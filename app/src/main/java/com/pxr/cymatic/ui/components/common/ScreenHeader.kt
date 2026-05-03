package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R

@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    title: String,
    onBackClick: () -> Unit
) {
    val fontFamily = FontFamily(Font(R.font.pixel))
    val cjkFontFamily = FontFamily(Font(R.font.pixel_cjk))
    val cjkRegex = Regex("[\\u4E00-\\u9FFF|\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]")
    val isTitleCJK = title.contains(cjkRegex)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = "<",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = fontFamily,
            modifier = Modifier
                .clickable(
                    onClick = onBackClick,
                    indication = null,
                    interactionSource = null
                )
                .padding(24.dp,  16.dp)
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isTitleCJK) cjkFontFamily else fontFamily,
            letterSpacing = if (isTitleCJK) 2.sp else 0.sp,
            modifier = Modifier.padding(
                bottom = 16.dp,
                top = if (isTitleCJK) 20.dp else 16.dp
            )
        )
    }
}


@Preview(showBackground = true)
@Composable
fun ScreenHeaderPreview() {
    Column {
        ScreenHeader(
            title = "Screen Title",
            onBackClick = {}
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        ScreenHeader(
            title = "恋を唄う",
            onBackClick = {}
        )
    }
}


