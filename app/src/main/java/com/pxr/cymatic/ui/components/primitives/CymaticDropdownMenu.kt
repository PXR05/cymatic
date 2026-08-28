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

@Composable
fun CymaticDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val secondaryColor = MaterialTheme.colorScheme.secondary

    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(0.dp),
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp),
            extraLarge = RoundedCornerShape(0.dp)
        )
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier
                .background(backgroundColor)
                .border(1.dp, secondaryColor),
            content = content
        )
    }
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
