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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.locals.LocalInterfaceSettings

private val ScreenShape = RoundedCornerShape(22.dp)
private val MaxWheelSize = 288.dp

@Composable
internal fun WheelPlayerLayout(
    controls: WheelPlayerControls,
    overlay: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val settings = LocalInterfaceSettings.current
    val screenPadding = settings.screenPaddingDp.dp
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Vertical))
            .imePadding()
    ) {
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val landscape = maxWidth > maxHeight
        val wheelAreaSize = if (landscape) maxHeight else maxWidth
        val baseWheelSize = minOf(MaxWheelSize, wheelAreaSize * 0.72f)
        val screenWidth =
            (if (landscape) maxWidth - wheelAreaSize - screenPadding
                else maxWidth - screenPadding * 2)
                .coerceAtLeast(1.dp)
        val availableScreenHeight =
            (if (landscape) maxHeight - screenPadding * 2
                else maxHeight - wheelAreaSize - screenPadding)
                .coerceAtLeast(1.dp)
        val screenHeight = minOf(screenWidth, availableScreenHeight)
        val wheelHeight =
            if (landscape) maxHeight
            else (maxHeight - screenHeight - screenPadding).coerceAtLeast(1.dp)
        val wheelSize =
            minOf(
                baseWheelSize * (settings.wheelSizePercent / 100),
                minOf(wheelAreaSize, wheelHeight) * 0.9f,
            )
        val displayHeight = if (keyboardVisible) maxHeight - screenPadding else screenHeight

        if (landscape && !keyboardVisible) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                PlayerDisplay(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .padding(start = screenPadding, top = screenPadding, bottom = screenPadding)
                        .height(screenHeight),
                    content,
                    overlay,
                )
                PlayerWheel(Modifier.size(wheelAreaSize), wheelSize, controls)
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerDisplay(
                    Modifier.fillMaxWidth()
                        .padding(start = screenPadding, end = screenPadding, top = screenPadding)
                        .height(displayHeight),
                    content,
                    overlay,
                )
                if (!keyboardVisible)
                    PlayerWheel(
                        Modifier.weight(1f).fillMaxWidth(),
                        wheelSize,
                        controls,
                    )
            }
        }
    }
}

@Composable
private fun PlayerDisplay(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
    overlay: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(ScreenShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ScreenShape)
    ) {
        Column(Modifier.fillMaxSize(), content = content)
        overlay()
    }
}

@Composable
private fun PlayerWheel(modifier: Modifier, diameter: Dp, controls: WheelPlayerControls) {
    val settings = LocalInterfaceSettings.current
    val sensitivity by
        SettingsStore.wheelSensitivityFlow.collectAsState(
            initial = SettingsStore.currentWheelSensitivity
        )
    Box(modifier, contentAlignment = Alignment.Center) {
        ClickWheel(
            diameter,
            controls::rotate,
            controls::press,
            controls::hold,
            sensitivity = sensitivity,
            movementPauseMs = settings.gesturePauseMs,
            hapticsEnabled = settings.hapticsEnabled,
            onMovementStarted = controls::beginMovement,
        )
    }
}
