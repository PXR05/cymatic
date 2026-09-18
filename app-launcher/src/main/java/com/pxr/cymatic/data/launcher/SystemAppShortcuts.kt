package com.pxr.cymatic.data.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import com.pxr.cymatic.launcher.R

object SystemAppShortcuts {
    fun openClock(context: Context) {
        openFirstAvailable(
            context,
            listOf(Intent(AlarmClock.ACTION_SHOW_ALARMS), Intent(AlarmClock.ACTION_SHOW_TIMERS)),
            R.string.clock_app_unavailable,
        )
    }

    fun openCalendar(context: Context) {
        val today = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build()
        openFirstAvailable(
            context,
            listOf(
                Intent(Intent.ACTION_VIEW, today),
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
            ),
            R.string.calendar_app_unavailable,
        )
    }

    private fun openFirstAvailable(context: Context, intents: List<Intent>, unavailableMessage: Int) {
        for (intent in intents) {
            try {
                // Launch directly: package visibility can hide handlers from resolveActivity().
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next standard intent.
            } catch (_: SecurityException) {
                // A device or work-profile policy may restrict this handler.
            }
        }
        Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
    }
}
