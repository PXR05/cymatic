package com.pxr.cymatic.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource

@Composable
fun CymaticDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val menuShape = RoundedCornerShape(12.dp)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = menuShape,
        containerColor = backgroundColor.copy(alpha = 0.95f),
        modifier = modifier
            .border(1.dp, secondaryColor.copy(alpha = 0.4f), menuShape),
        content = content
    )
}

@Composable
fun CymaticDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        leadingIcon = leadingIcon,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun CymaticDropdownMenuItem(
    text: String,
    leadingIcon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CymaticDropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}
