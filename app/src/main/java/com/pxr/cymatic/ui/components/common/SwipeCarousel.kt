package com.pxr.cymatic.ui.components.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeCarousel(
    modifier: Modifier = Modifier,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
    prevContent: @Composable () -> Unit,
    nextContent: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(false) }

    fun commit(direction: Int, widthPx: Float) {
        animating = true
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        scope.launch {
            dragX.animateTo(direction * widthPx, tween(durationMillis = 140))
            if (direction < 0) onNext() else onPrev()
            dragX.snapTo(0f)
            animating = false
        }
    }

    fun settle(widthPx: Float) {
        if (animating) return
        val threshold = widthPx * 0.25f
        when {
            dragX.value <= -threshold && hasNext -> commit(-1, widthPx)
            dragX.value >= threshold && hasPrev -> commit(1, widthPx)
            else -> scope.launch { dragX.animateTo(0f, tween(durationMillis = 140)) }
        }
    }

    val density = LocalDensity.current
    val edgeResistance = with(density) { 24.dp.toPx() }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(onTap, onLongPress) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = {
                        if (onLongPress != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    }
                )
            }
            .pointerInput(hasPrev, hasNext) {
                var accumulatedDrag = 0f
                val widthPx = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulatedDrag = 0f
                        if (!animating) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (!animating) {
                            accumulatedDrag += dragAmount
                            val edgeLimited =
                                (accumulatedDrag < 0f && !hasNext) ||
                                    (accumulatedDrag > 0f && !hasPrev)
                            val limit = if (edgeLimited) edgeResistance else widthPx
                            val target = accumulatedDrag.coerceIn(-limit, limit)
                            scope.launch { dragX.snapTo(target) }
                        }
                    },
                    onDragEnd = { settle(widthPx) },
                    onDragCancel = {
                        scope.launch { dragX.animateTo(0f, tween(durationMillis = 140)) }
                    }
                )
            }
    ) {
        Layout(
            content = {
                Box { prevContent() }
                Box { content() }
                Box { nextContent() }
            }
        ) { measurables, constraints ->
            val slotWidth = constraints.maxWidth
            val childConstraints = Constraints(
                minWidth = slotWidth,
                maxWidth = slotWidth
            )
            val placeables = measurables.map { it.measure(childConstraints) }
            val height = placeables.maxOfOrNull { it.height } ?: 0

            layout(slotWidth, height) {
                val x = dragX.value.roundToInt()
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(x + (index - 1) * slotWidth, 0)
                }
            }
        }
    }
}
