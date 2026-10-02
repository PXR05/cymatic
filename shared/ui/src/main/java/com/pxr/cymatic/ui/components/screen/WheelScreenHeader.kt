package com.pxr.cymatic.ui.components.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalScreenHeaderStatus = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun WheelScreenHeader(
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val status = LocalScreenHeaderStatus.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(56.dp)
            .clipToBounds(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick != null) {
            Box(
                Modifier
                    .padding(start = 16.dp)
                    .width(44.dp)
                    .fillMaxHeight()
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Text("<", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .padding(start = if (onBackClick == null) 24.dp else 0.dp)
        ) {
            content()
        }
        actions()
        if (status != null) {
            Box(Modifier.padding(start = 8.dp, end = 24.dp)) { status() }
        }
    }
}
