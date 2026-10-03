package com.pxr.cymatic.ui.components.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.design.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

internal enum class WheelButton {
    MENU,
    PREVIOUS,
    NEXT,
    PLAY,
    SELECT,
}

private const val RotationStep = 0.20f
private const val MinimumMotionAngle = 0.005f

@Composable
internal fun ClickWheel(
    diameter: Dp,
    onRotate: (Int) -> Unit,
    onPress: (WheelButton) -> Unit,
    onHold: (WheelButton) -> Unit,
    sensitivity: Float = 1f,
    movementPauseMs: Long = 220L,
    hapticsEnabled: Boolean = true,
    onMovementStarted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rotate by rememberUpdatedState(onRotate)
    val press by rememberUpdatedState(onPress)
    val hold by rememberUpdatedState(onHold)
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val currentPauseMs by rememberUpdatedState(movementPauseMs)
    val feedbackEnabled by rememberUpdatedState(hapticsEnabled)
    val movementStarted by rememberUpdatedState(onMovementStarted)
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    var pressed by remember { mutableStateOf<WheelButton?>(null) }

    Box(
        modifier =
            modifier
                .size(diameter)
                .pointerInput(Unit) {
                    coroutineScope {
                        val gestureScope = this
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f
                            val start = down.position - center
                            if (start.getDistance() > radius) return@awaitEachGesture
                            val button =
                                when {
                                    start.getDistance() < radius * 0.34f -> WheelButton.SELECT
                                    abs(start.y) > abs(start.x) ->
                                        if (start.y < 0) WheelButton.MENU else WheelButton.PLAY

                                    start.x < 0 -> WheelButton.PREVIOUS
                                    else -> WheelButton.NEXT
                                }
                            pressed = button
                            var rotated = false
                            var held = false
                            var cancelled = false
                            var angle = atan2(start.y, start.x)
                            var accumulated = 0f
                            var lastMotionTime = down.uptimeMillis
                            val holdJob = gestureScope.launch {
                                delay(viewConfiguration.longPressTimeoutMillis)
                                if (!rotated) {
                                    held = true
                                    if (feedbackEnabled)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hold(button)
                                    if (button == WheelButton.PREVIOUS || button == WheelButton.NEXT) {
                                        while (true) {
                                            delay(150)
                                            hold(button)
                                        }
                                    }
                                }
                            }
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || change.isConsumed) {
                                        cancelled = true
                                        break
                                    }
                                    val position = change.position - center
                                    if (
                                        !held &&
                                        button != WheelButton.SELECT &&
                                        (rotated ||
                                                (change.position - down.position).getDistance() >
                                                viewConfiguration.touchSlop)
                                    ) {
                                        if (!rotated) {
                                            movementStarted()
                                            lastMotionTime = change.uptimeMillis
                                        }
                                        rotated = true
                                        pressed = null
                                        holdJob.cancel()
                                        if (position.getDistance() in radius * 0.34f..radius * 1.15f) {
                                            val nextAngle = atan2(position.y, position.x)
                                            var delta = nextAngle - angle
                                            if (delta > PI) delta -= (2 * PI).toFloat()
                                            if (delta < -PI) delta += (2 * PI).toFloat()
                                            if (abs(delta) >= MinimumMotionAngle) {
                                                if (
                                                    change.uptimeMillis - lastMotionTime >=
                                                    currentPauseMs
                                                ) {
                                                    accumulated = 0f
                                                    movementStarted()
                                                }
                                                lastMotionTime = change.uptimeMillis
                                                accumulated += delta
                                                val stepAngle =
                                                    RotationStep /
                                                            currentSensitivity.coerceIn(0.25f, 3f)
                                                val steps = (accumulated / stepAngle).toInt()
                                                if (steps != 0) {
                                                    rotate(steps)
                                                    accumulated -= steps * stepAngle
                                                    if (feedbackEnabled)
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove
                                                        )
                                                }
                                                angle = nextAngle
                                            }
                                        } else {
                                            angle = atan2(position.y, position.x)
                                            accumulated = 0f
                                        }
                                        change.consume()
                                    }
                                } while (event.changes.any { it.id == down.id && it.pressed })
                                if (!cancelled && !rotated && !held) {
                                    if (feedbackEnabled)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    press(button)
                                }
                            } finally {
                                holdJob.cancel()
                                pressed = null
                            }
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(colors.surfaceVariant, radius)
            drawCircle(colors.outlineVariant, radius - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            drawCircle(colors.background, radius * 0.34f)
            drawCircle(colors.outlineVariant, radius * 0.34f, style = Stroke(1.dp.toPx()))
            if (pressed == WheelButton.SELECT)
                drawCircle(
                    colors.onBackground.copy(alpha = 0.1f),
                    radius * 0.34f,
                )
        }
        val buttons =
            listOf(
                Triple(WheelButton.MENU, 0f, -0.33f),
                Triple(WheelButton.PREVIOUS, -0.33f, 0f),
                Triple(WheelButton.NEXT, 0.33f, 0f),
                Triple(WheelButton.PLAY, 0f, 0.33f),
                Triple(WheelButton.SELECT, 0f, 0f),
            )
        buttons.forEach { (button, x, y) ->
            Box(
                Modifier
                    .offset(diameter * x, diameter * y)
                    .size(diameter * 0.24f)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = button.name.lowercase()
                        onClick {
                            press(button)
                            true
                        }
                        onLongClick {
                            hold(button)
                            true
                        }
                    }
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) {
                when (button) {
                    WheelButton.MENU -> Text(
                        "MENU",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground
                    )

                    WheelButton.PREVIOUS ->
                        Icon(
                            painterResource(R.drawable.ic_pixel_previous),
                            null,
                            Modifier.size(24.dp),
                            tint = colors.onBackground,
                        )

                    WheelButton.NEXT ->
                        Icon(
                            painterResource(R.drawable.ic_pixel_next),
                            null,
                            Modifier.size(24.dp),
                            tint = colors.onBackground,
                        )

                    WheelButton.PLAY ->
                        Icon(
                            painterResource(R.drawable.ic_pixel_play_pause),
                            null,
                            Modifier.size(width = 28.dp, height = 21.dp),
                            tint = colors.onBackground,
                        )

                    WheelButton.SELECT -> Unit
                }
            }
        }
    }
}
