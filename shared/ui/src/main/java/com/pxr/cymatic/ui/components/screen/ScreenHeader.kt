package com.pxr.cymatic.ui.components.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    title: String,
    onBackClick: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val cjkRegex = Regex("[\\u4E00-\\u9FFF|\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]")
    val isTitleCJK = title.contains(cjkRegex)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "<",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(
                    onClick = onBackClick,
                    indication = null,
                    interactionSource = null
                )
                .padding(24.dp, 16.dp)
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (isTitleCJK) 2.sp else 0.sp,
            modifier = Modifier
                .weight(1f)
                .padding(
                    vertical = 16.dp
                )
                .then(
                    if (onTitleClick != null) {
                        Modifier.clickable(
                            onClick = onTitleClick,
                            indication = null,
                            interactionSource = null
                        )
                    } else {
                        Modifier
                    }
                )
        )
        actions()
    }
}