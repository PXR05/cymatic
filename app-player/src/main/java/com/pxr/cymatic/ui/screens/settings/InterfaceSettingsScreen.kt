package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.pxr.cymatic.data.store.ScreenAwakeMode
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.data.store.setCoverVisibleByDefault
import com.pxr.cymatic.data.store.setGesturePauseMs
import com.pxr.cymatic.data.store.setScreenAwakeMode
import com.pxr.cymatic.data.store.setScreenPaddingDp
import com.pxr.cymatic.data.store.setStandbyTimeoutMs
import com.pxr.cymatic.data.store.setTextScrollDelayMs
import com.pxr.cymatic.data.store.setTextScrollSpeed
import com.pxr.cymatic.data.store.setWheelHapticsEnabled
import com.pxr.cymatic.data.store.setWheelSizePercent
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.common.WheelNumberEditor
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalInterfaceSettings
import com.pxr.cymatic.ui.locals.LocalNavController
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

internal const val INTERFACE_SETTINGS_ROUTE = "interface_settings"

private data class InterfaceNumberEdit(
    val title: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val format: (Float) -> String,
    val save: suspend (Float) -> Unit,
)

private fun percent(value: Float) = "${value.roundToInt()}%"

private fun milliseconds(value: Float) = "${value.roundToInt()} ms"

private fun densityPixels(value: Float) = "${value.roundToInt()} dp"

private fun seconds(value: Float) = String.format(Locale.US, "%.1f s", value / 1000)

@Composable
internal fun InterfaceSettingsScreen() {
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    val settings = LocalInterfaceSettings.current
    val sensitivity by
        SettingsStore.wheelSensitivityFlow.collectAsState(
            initial = SettingsStore.currentWheelSensitivity
        )
    var number by remember { mutableStateOf<InterfaceNumberEdit?>(null) }
    var showTextScrolling by remember { mutableStateOf(false) }
    var showScreenAwake by remember { mutableStateOf(false) }
    var showStandby by remember { mutableStateOf(false) }

    BaseScreen(title = "Interface", onBackClick = { nav.popBackStack() }) {
        NavigationList(
            listOf(
                NavigationItem("Wheel sensitivity", percent(sensitivity * 100)) {
                    number =
                        InterfaceNumberEdit(
                            "Wheel sensitivity",
                            sensitivity * 100,
                            25f..300f,
                            5f,
                            ::percent,
                        ) {
                            SettingsStore.setWheelSensitivity(it / 100)
                        }
                },
                NavigationItem("Gesture pause", milliseconds(settings.gesturePauseMs.toFloat())) {
                    number =
                        InterfaceNumberEdit(
                            "Gesture pause",
                            settings.gesturePauseMs.toFloat(),
                            100f..1000f,
                            20f,
                            ::milliseconds,
                        ) {
                            SettingsStore.setGesturePauseMs(it.roundToLong())
                        }
                },
                NavigationItem("Haptic feedback", if (settings.hapticsEnabled) "On" else "Off") {
                    scope.launch { SettingsStore.setWheelHapticsEnabled(!settings.hapticsEnabled) }
                },
                NavigationItem("Wheel size", percent(settings.wheelSizePercent)) {
                    number =
                        InterfaceNumberEdit(
                            "Wheel size",
                            settings.wheelSizePercent,
                            60f..120f,
                            5f,
                            ::percent,
                        ) {
                            SettingsStore.setWheelSizePercent(it)
                        }
                },
                NavigationItem("Screen padding", densityPixels(settings.screenPaddingDp)) {
                    number =
                        InterfaceNumberEdit(
                            "Screen padding",
                            settings.screenPaddingDp,
                            0f..48f,
                            2f,
                            ::densityPixels,
                        ) {
                            SettingsStore.setScreenPaddingDp(it)
                        }
                },
                NavigationItem(
                    "Text scrolling",
                    "${percent(settings.textScrollSpeed / 24 * 100)} · ${seconds(settings.textScrollDelayMs.toFloat())}",
                ) {
                    showTextScrolling = true
                },
                NavigationItem(
                    "Cover art default",
                    if (settings.coverVisibleByDefault) "Shown" else "Hidden",
                ) {
                    scope.launch {
                        SettingsStore.setCoverVisibleByDefault(!settings.coverVisibleByDefault)
                    }
                },
                NavigationItem("Keep screen awake", settings.screenAwakeMode.label) {
                    showScreenAwake = true
                },
                NavigationItem(
                    "Standby",
                    if (settings.standbyTimeoutMs == 0L) "Off"
                    else "After ${settings.standbyTimeoutMs / 1000} s",
                ) {
                    showStandby = true
                },
            )
        )
    }

    if (showTextScrolling) {
        WheelSettingsOverlay("Text scrolling", { showTextScrolling = false }) {
            NavigationList(
                listOf(
                    NavigationItem("Speed", percent(settings.textScrollSpeed / 24 * 100)) {
                        number =
                            InterfaceNumberEdit(
                                "Scroll speed",
                                settings.textScrollSpeed / 24 * 100,
                                50f..400f,
                                25f,
                                ::percent,
                            ) {
                                SettingsStore.setTextScrollSpeed(24 * it / 100)
                            }
                    },
                    NavigationItem("Start delay", seconds(settings.textScrollDelayMs.toFloat())) {
                        number =
                            InterfaceNumberEdit(
                                "Start delay",
                                settings.textScrollDelayMs.toFloat(),
                                0f..5000f,
                                100f,
                                ::seconds,
                            ) {
                                SettingsStore.setTextScrollDelayMs(it.roundToLong())
                            }
                    },
                )
            )
        }
    }

    if (showScreenAwake) {
        WheelContextMenu(
            "Keep screen awake",
            ScreenAwakeMode.entries.map { mode ->
                NavigationItem(
                    mode.label,
                    if (mode == settings.screenAwakeMode) "Selected" else null,
                ) {
                    scope.launch { SettingsStore.setScreenAwakeMode(mode) }
                    showScreenAwake = false
                }
            },
            { showScreenAwake = false },
        )
    }

    if (showStandby) {
        WheelContextMenu(
            "Standby · touch to wake",
            listOf(0L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L).map { timeout ->
                NavigationItem(
                    if (timeout == 0L) "Off" else "After ${timeout / 1000} s",
                    if (timeout == settings.standbyTimeoutMs) "Selected" else null,
                ) {
                    scope.launch { SettingsStore.setStandbyTimeoutMs(timeout) }
                    showStandby = false
                }
            },
            { showStandby = false },
        )
    }

    number?.let { edit ->
        WheelNumberEditor(
            edit.title,
            edit.value,
            edit.range,
            edit.step,
            edit.format,
            onSave = edit.save,
            onDismiss = { number = null },
        )
    }
}
