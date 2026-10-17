package com.pxr.cymatic.ui.components.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalScreenHeaderStatus = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelScreenHeader(
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onMenuLongClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: (@Composable () -> Unit)? = null
) {
    val status = LocalScreenHeaderStatus.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(44.dp)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (content != null) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .padding(start = 24.dp, end = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        content()
                    }
                } else if (onBackClick != null) {
                    val backModifier = if (onMenuLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onBackClick,
                            onLongClick = onMenuLongClick
                        )
                    } else {
                        Modifier.clickable(onClick = onBackClick)
                    }
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .then(backModifier)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            "<",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    val homeModifier = if (onMenuLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onMenuLongClick
                        )
                    } else {
                        Modifier
                    }
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .then(homeModifier)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            "Cymatic",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            Spacer(Modifier.width(64.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                actions()
                if (status != null) {
                    Box(Modifier.padding(start = 8.dp, end = 24.dp)) { status() }
                }
            }
        }
        HeaderClock(modifier = Modifier.align(Alignment.Center))
    }
}
