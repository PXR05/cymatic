package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenu
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenuItem

@Composable
fun AppActionPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onAppInfo: () -> Unit,
    modifier: Modifier = Modifier,
    onPin: (() -> Unit)? = null,
    onUnpin: (() -> Unit)? = null,
    onUninstall: (() -> Unit)? = null
) {
    CymaticDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        if (onPin != null) {
            CymaticDropdownMenuItem(
                text = "Pin to Home",
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_pixel_plus),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    onDismissRequest()
                    onPin()
                }
            )
        }
        if (onUnpin != null) {
            CymaticDropdownMenuItem(
                text = "Unpin from Home",
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_pixel_trash),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    onDismissRequest()
                    onUnpin()
                }
            )
        }
        CymaticDropdownMenuItem(
            text = "App Info",
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_pixel_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(16.dp)
                )
            },
            onClick = {
                onDismissRequest()
                onAppInfo()
            }
        )
        if (onUninstall != null) {
            CymaticDropdownMenuItem(
                text = "Uninstall",
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_pixel_trash),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    onDismissRequest()
                    onUninstall()
                }
            )
        }
    }
}
