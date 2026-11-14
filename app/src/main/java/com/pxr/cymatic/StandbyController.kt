package com.pxr.cymatic

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup

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
    private var standbyEnteredTime = 0L
    var asleep = false
        private set

    private val sleep = Runnable {
        if (focused && timeoutMs > 0L) {
            enterStandby()
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
        curtain.keepScreenOn = focused && (timeoutMs > 0L || asleep)
        interact()
    }

    fun focus(active: Boolean) {
        focused = active
        curtain.keepScreenOn = active && (timeoutMs > 0L || asleep)
        interact()
    }

    fun enterStandby() {
        if (!focused || asleep) return
        handler.removeCallbacks(sleep)
        standbyEnteredTime = SystemClock.uptimeMillis()
        asleep = true
        curtain.setBackgroundColor(Color.BLACK)
        curtain.keepScreenOn = true
        val attributes = activity.window.attributes
        previousBrightness = attributes.screenBrightness
        previousButtonBrightness = attributes.buttonBrightness
        attributes.screenBrightness = 0f
        attributes.buttonBrightness = 0f
        activity.window.attributes = attributes
    }

    fun interact() {
        handler.removeCallbacks(sleep)
        if (asleep) {
            if (SystemClock.uptimeMillis() - standbyEnteredTime < 300L) {
                return
            }
            asleep = false
            curtain.setBackgroundColor(Color.TRANSPARENT)
            curtain.keepScreenOn = focused && timeoutMs > 0L
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
