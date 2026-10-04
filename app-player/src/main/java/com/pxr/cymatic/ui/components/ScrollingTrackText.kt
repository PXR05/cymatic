package com.pxr.cymatic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.ui.locals.LocalInterfaceSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ScrollingTrackText(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    height: Dp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val density = LocalDensity.current
    val settings = LocalInterfaceSettings.current
    val measurer = rememberTextMeasurer()
    val style =
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            color = color,
        )
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(height).clipToBounds().clearAndSetSemantics {
            this.text = AnnotatedString(text)
        }
    ) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val layout =
            remember(text, style, density) {
                measurer.measure(
                    text = text,
                    style = style,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = Constraints.Infinity),
                )
            }
        val distance = (layout.size.width - widthPx).coerceAtLeast(0).toFloat()
        val offset = remember(text, widthPx) { Animatable(0f) }
        val speed = with(density) { settings.textScrollSpeed.dp.toPx() }
        LaunchedEffect(text, distance, speed, settings.textScrollDelayMs, offset) {
            offset.snapTo(0f)
            val duration = (distance / speed * 1000).roundToInt().coerceAtLeast(1)
            while (isActive && distance > 0f) {
                delay(settings.textScrollDelayMs)
                offset.animateTo(
                    distance,
                    tween(duration, easing = LinearEasing),
                )
                delay(settings.textScrollDelayMs)
                offset.animateTo(0f, tween(duration, easing = LinearEasing))
            }
        }
        if (distance == 0f) {
            Text(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val ellipsis =
                remember(style, density) {
                    measurer.measure("\u2026", style = style, softWrap = false, maxLines = 1)
                }
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val position = offset.value.coerceIn(0f, distance)
                val leading = position > 0f
                val trailing = position < distance
                val ellipsisWidth = ellipsis.size.width.toFloat().coerceAtMost(size.width / 2)
                val left = if (leading) ellipsisWidth else 0f
                val right = size.width - if (trailing) ellipsisWidth else 0f
                clipRect(left = left, right = right) {
                    drawText(layout, topLeft = Offset(-position, 0f))
                }
                if (leading) drawText(ellipsis, topLeft = Offset.Zero)
                if (trailing) drawText(ellipsis, topLeft = Offset(size.width - ellipsisWidth, 0f))
            }
        }
    }
}

@Composable
fun trackTextHeight(
    fontSize: TextUnit,
    lineHeight: TextUnit,
    lines: Int,
    fontWeight: FontWeight = FontWeight.Normal,
): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style =
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
        )
    val height =
        remember(style, lines, density, measurer) {
            measurer.measure(List(lines) { "Mg" }.joinToString("\n"), style = style).size.height
        }
    return with(density) { height.toDp() }
}
