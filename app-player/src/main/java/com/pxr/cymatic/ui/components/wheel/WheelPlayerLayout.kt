package com.pxr.cymatic.ui.components.wheel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ScreenMargin = 18.dp
private val ScreenShape = RoundedCornerShape(22.dp)
private val MaxWheelSize = 288.dp

@Composable
internal fun WheelPlayerLayout(
    controls: WheelPlayerControls,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Vertical))
            .imePadding()
    ) {
        val landscape = maxWidth > maxHeight
        val wheelAreaSize = if (landscape) maxHeight else maxWidth
        val wheelSize = minOf(MaxWheelSize, wheelAreaSize * 0.72f)
        val screenWidth = (
            if (landscape) maxWidth - wheelAreaSize - ScreenMargin else maxWidth - ScreenMargin * 2
        ).coerceAtLeast(1.dp)
        val availableScreenHeight = (
            if (landscape) maxHeight - ScreenMargin * 2 else maxHeight - wheelAreaSize - ScreenMargin
        ).coerceAtLeast(1.dp)
        val screenHeight = minOf(screenWidth, availableScreenHeight)

        if (landscape) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                PlayerDisplay(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = ScreenMargin, top = ScreenMargin, bottom = ScreenMargin)
                        .height(screenHeight),
                    content
                )
                PlayerWheel(Modifier.size(wheelAreaSize), wheelSize, controls)
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerDisplay(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = ScreenMargin, end = ScreenMargin, top = ScreenMargin)
                        .height(screenHeight),
                    content
                )
                PlayerWheel(Modifier.weight(1f).fillMaxWidth(), wheelSize, controls)
            }
        }
    }
}

@Composable
private fun PlayerDisplay(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(ScreenShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ScreenShape),
        content = content
    )
}

@Composable
private fun PlayerWheel(modifier: Modifier, diameter: Dp, controls: WheelPlayerControls) {
    Box(modifier, contentAlignment = Alignment.Center) {
        ClickWheel(diameter, controls::rotate, controls::press, controls::hold)
    }
}
