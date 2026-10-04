package com.pxr.cymatic

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup

/** Blackens and dims the app while keeping input available for immediate wake. */
internal class StandbyController(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private val curtain =
        View(activity).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(Color.TRANSPARENT)
        }
    private var timeoutMs = 0L
    private var focused = false
    private var previousBrightness = -1f
    private var previousButtonBrightness = -1f
    var asleep = false
        private set

    private val sleep = Runnable {
        if (focused && timeoutMs > 0L) {
            asleep = true
            curtain.setBackgroundColor(Color.BLACK)
            val attributes = activity.window.attributes
            previousBrightness = attributes.screenBrightness
            previousButtonBrightness = attributes.buttonBrightness
            attributes.screenBrightness = 0f
            attributes.buttonBrightness = 0f
            activity.window.attributes = attributes
        }
    }

    init {
        (activity.window.decorView as ViewGroup).addView(
            curtain,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun configure(timeout: Long) {
        if (timeoutMs == timeout) return
        timeoutMs = timeout
        curtain.keepScreenOn = focused && timeoutMs > 0L
        interact()
    }

    fun focus(active: Boolean) {
        focused = active
        curtain.keepScreenOn = active && timeoutMs > 0L
        interact()
    }

    fun interact() {
        handler.removeCallbacks(sleep)
        if (asleep) {
            asleep = false
            curtain.setBackgroundColor(Color.TRANSPARENT)
            val attributes = activity.window.attributes
            attributes.screenBrightness = previousBrightness
            attributes.buttonBrightness = previousButtonBrightness
            activity.window.attributes = attributes
        }
        if (focused && timeoutMs > 0L) handler.postDelayed(sleep, timeoutMs)
    }

    fun close() {
        focus(false)
        (curtain.parent as? ViewGroup)?.removeView(curtain)
    }
}
