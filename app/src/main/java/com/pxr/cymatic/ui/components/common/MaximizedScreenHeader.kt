package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun MaximizedScreenHeader(
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val fontFamily = FontFamily(Font(R.font.pixel))

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Cymatic",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "LOCKED",
            color = MaterialTheme.colorScheme.background,
            fontSize = 14.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.onBackground)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clickable(
                    onClick = {
                        scope.launch {
                            SettingsStore.setLocked(!SettingsStore.isLocked())
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                    },
                    indication = null,
                    interactionSource = null
                )
        )
    }
}