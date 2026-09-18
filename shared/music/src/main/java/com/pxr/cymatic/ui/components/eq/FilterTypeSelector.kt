package com.pxr.cymatic.ui.components.eq

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenu
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.model.FilterType

@Composable
fun FilterTypeSelector(
    selected: FilterType,
    onSelect: (FilterType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = selected.displayName,
            fontSize = 13.sp,
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.secondary)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(
                    onClick = { expanded = true },
                    indication = null,
                    interactionSource = null
                )
        )

        CymaticDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FilterType.entries.forEach { type ->
                CymaticDropdownMenuItem(
                    text = type.displayName,
                    onClick = { onSelect(type); expanded = false }
                )
            }
        }
    }
}
