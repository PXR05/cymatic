package com.pxr.cymatic.data.launcher

import android.annotation.SuppressLint
import android.content.Context

object SystemShadeHelper {

    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method = statusBarManagerClass.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
        } catch (_: Exception) {
            try {
                val statusBarService = context.getSystemService("statusbar")
                val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
                val method = statusBarManagerClass.getMethod("expand")
                method.invoke(statusBarService)
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("WrongConstant")
    fun expandQuickSettings(context: Context) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method = statusBarManagerClass.getMethod("expandSettingsPanel")
            method.invoke(statusBarService)
        } catch (_: Exception) {
            expandNotifications(context)
        }
    }
}
