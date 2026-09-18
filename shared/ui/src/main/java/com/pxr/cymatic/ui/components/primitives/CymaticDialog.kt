package com.pxr.cymatic.ui.components.primitives

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CymaticDialog(
    title: String,
    onDismissRequest: () -> Unit,
    maxHeightRatio: Float = 0.9f,
    widthRatio: Float = 0.8f,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    val dialogWidth = with(window) { (containerSize.width * widthRatio) }
    val dialogWidthDp = with(density) { dialogWidth.toDp() }
    val dialogHeight = with(window) { (containerSize.height * maxHeightRatio) }
    val dialogHeightDp = with(density) { dialogHeight.toDp() }
    val dialogShape = RoundedCornerShape(18.dp)

    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    val dismissWithAnimation: () -> Unit = {
        visibleState.targetState = false
    }

    BackHandler(enabled = visibleState.targetState) {
        dismissWithAnimation()
    }

    LaunchedEffect(visibleState.currentState, visibleState.targetState) {
        if (!visibleState.currentState && !visibleState.targetState) {
            onDismissRequest()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissWithAnimation() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(initialScale = 0.90f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)) +
                                fadeIn(animationSpec = tween(180)),
                        exit = scaleOut(targetScale = 0.92f, animationSpec = tween(140)) +
                               fadeOut(animationSpec = tween(140))
                    )
                    .width(dialogWidthDp)
                    .heightIn(max = dialogHeightDp)
                    .clip(dialogShape)
                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), dialogShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                content()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    buttons()
                }
            }
        }
    }
}

@Composable
fun RowScope.CymaticDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    weight: Float = 1f
) {
    Text(
        text = text,
        color = color ?: MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .weight(weight)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = null
            )
    )
}

@Composable
fun CymaticDialogDivider() {
    Text(
        text = "|",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp
    )
}
