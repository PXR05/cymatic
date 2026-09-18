package com.pxr.cymatic.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally

object CymaticMotion {
    private const val PageExitMillis = 90
    private const val PageEnterMillis = 220

    fun <T> spatial(): TweenSpec<T> = tween(260, easing = FastOutSlowInEasing)
    fun <T> enter(): TweenSpec<T> = tween(220, easing = LinearOutSlowInEasing)
    fun <T> exit(): TweenSpec<T> = tween(160, easing = FastOutLinearInEasing)

    // Transparent destinations must not reveal the outgoing page's content.
    // Its fade finishes before the next destination starts becoming visible.
    fun pageEnter(distancePx: Int = 0): EnterTransition =
        fadeIn(tween(PageEnterMillis, delayMillis = PageExitMillis, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(
                initialOffsetX = { distancePx },
                animationSpec = tween(PageEnterMillis, delayMillis = PageExitMillis, easing = LinearOutSlowInEasing),
            )

    fun pageExit(): ExitTransition =
        fadeOut(tween(PageExitMillis, easing = FastOutLinearInEasing))
}
