package com.pxr.cymatic.ui.components.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    label: String,
    labelStyle: TextStyle = TextStyle.Default,
    subLabel: String? = null,
    subLabelStyle: TextStyle = TextStyle.Default,
    trailing: String? = null,
    trailingStyle: TextStyle = TextStyle.Default,
    isActive: Boolean = false,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = { },
    onLongClick: () -> Unit = { }
) {
    val fontFamily = FontFamily(Font(R.font.pixel))
    val cjkFontFamily = FontFamily(Font(R.font.pixel_cjk))
    val cjkRegex = Regex("[\\u4E00-\\u9FFF|\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]")
    val isLabelCJK = label.contains(cjkRegex)
    val isSubLabelCJK = subLabel?.contains(cjkRegex) ?: false
    val labelColor =
        if (isActive) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.onBackground
        }
    val secondaryLabelColor =
        if (isActive) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.secondary
        }
    val labelFontStyle = TextStyle(
        fontSize = 20.sp,
        letterSpacing = if (isLabelCJK) 2.sp else 0.sp,
        fontFamily = if (isLabelCJK) cjkFontFamily else fontFamily
    ).merge(labelStyle)
    val subLabelFontStyle = TextStyle(
        fontSize = 14.sp,
        letterSpacing = if (isSubLabelCJK) 1.5.sp else 0.sp,
        fontFamily = if (isSubLabelCJK) cjkFontFamily else fontFamily
    ).merge(subLabelStyle)
    val trailingFontStyle = TextStyle(
        color = secondaryLabelColor,
        fontSize = 14.sp,
        fontFamily = fontFamily
    ).merge(trailingStyle)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isActive) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    Color.Transparent
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
                interactionSource = null
            )
            .padding(24.dp, 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (icon != null) {
            Box(modifier = Modifier.padding(end = 16.dp)) {
                icon()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = labelColor,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = labelFontStyle,
                modifier = Modifier.padding(
                    top = if (isLabelCJK) 6.dp else 0.dp
                )
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = secondaryLabelColor,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = subLabelFontStyle,
                    modifier = Modifier.padding(
                        top = if (isSubLabelCJK) 2.dp else 0.dp,
                        bottom = if (isSubLabelCJK) 1.dp else 0.dp
                    )
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = trailingFontStyle
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ListItemPreview() {
    Column {
        ListItem(
            label = "Song Title",
            subLabel = "Artist Name",
            trailing = "3:30",
            isActive = true,
            onClick = { }
        )
        ListItem(
            label = "Song Title",
            subLabel = "Artist Name",
            trailing = "3:30",
            isActive = false,
            onClick = { }
        )
        ListItem(
            label = "Label",
            trailing = ">",
            isActive = true,
            onClick = { }
        )
        ListItem(
            label = "Label",
            trailing = ">",
            isActive = false,
            onClick = { }
        )
    }
}

