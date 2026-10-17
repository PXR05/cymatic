package com.pxr.cymatic.ui.components.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    title: String,
    onBackClick: (() -> Unit)?,
    onTitleClick: (() -> Unit)? = null,
    onMenuLongClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val compact = LocalWheelNavigation.current != null
    val cjkRegex = Regex("[\\u4E00-\\u9FFF|\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]")
    val isTitleCJK = title.contains(cjkRegex)

    if (compact) {
        WheelScreenHeader(
            modifier = modifier,
            onBackClick = onBackClick,
            onMenuLongClick = onMenuLongClick,
            actions = actions
        ) {
            val titleModifier = when {
                onTitleClick != null && onMenuLongClick != null -> {
                    Modifier.combinedClickable(
                        onClick = onTitleClick,
                        onLongClick = onMenuLongClick
                    )
                }
                onTitleClick != null -> Modifier.clickable(onClick = onTitleClick)
                onMenuLongClick != null -> Modifier.combinedClickable(
                    onClick = onMenuLongClick,
                    onLongClick = onMenuLongClick
                )
                else -> Modifier
            }
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = if (isTitleCJK) 2.sp else 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier
            )
        }
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        if (onBackClick != null) {
            val backModifier = if (onMenuLongClick != null) {
                Modifier.combinedClickable(
                    onClick = onBackClick,
                    onLongClick = onMenuLongClick,
                    indication = null,
                    interactionSource = null
                )
            } else {
                Modifier.clickable(
                    onClick = onBackClick,
                    indication = null,
                    interactionSource = null
                )
            }
            Text(
                text = "<",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .then(backModifier)
                    .padding(24.dp, 16.dp)
            )
        }
        val titleModifier = when {
            onTitleClick != null && onMenuLongClick != null -> {
                Modifier.combinedClickable(
                    onClick = onTitleClick,
                    onLongClick = onMenuLongClick,
                    indication = null,
                    interactionSource = null
                )
            }
            onTitleClick != null -> {
                Modifier.clickable(
                    onClick = onTitleClick,
                    indication = null,
                    interactionSource = null
                )
            }
            onMenuLongClick != null -> {
                Modifier.combinedClickable(
                    onClick = onMenuLongClick,
                    onLongClick = onMenuLongClick,
                    indication = null,
                    interactionSource = null
                )
            }
            else -> Modifier
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (isTitleCJK) 2.sp else 0.sp,
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = if (onBackClick != null) 0.dp else 24.dp,
                    top = 16.dp,
                    bottom = 16.dp
                )
                .then(titleModifier)
        )
        actions()
    }
}
