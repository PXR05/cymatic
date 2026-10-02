package com.pxr.cymatic.data.store

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ScreenAwakeMode(val label: String) {
    NEVER("Never"),
    DURING_PLAYBACK("During playback"),
    ALWAYS("Always")
}

data class InterfaceSettings(
    val gesturePauseMs: Long = 220L,
    val hapticsEnabled: Boolean = true,
    val wheelSizePercent: Float = 100f,
    val textScrollSpeed: Float = 24f,
    val textScrollDelayMs: Long = 2200L,
    val coverVisibleByDefault: Boolean = true,
    val screenAwakeMode: ScreenAwakeMode = ScreenAwakeMode.NEVER
)

private val GesturePauseKey = longPreferencesKey("WHEEL_GESTURE_PAUSE_MS")
private val HapticsKey = booleanPreferencesKey("WHEEL_HAPTICS_ENABLED")
private val WheelSizeKey = floatPreferencesKey("WHEEL_SIZE_PERCENT")
private val TextSpeedKey = floatPreferencesKey("INTERFACE_TEXT_SCROLL_SPEED")
private val TextDelayKey = longPreferencesKey("INTERFACE_TEXT_SCROLL_DELAY_MS")
private val CoverDefaultKey = booleanPreferencesKey("WHEEL_COVER_VISIBLE_DEFAULT")
private val ScreenAwakeKey = stringPreferencesKey("INTERFACE_SCREEN_AWAKE_MODE")

private fun readInterfaceSettings(prefs: Preferences?): InterfaceSettings {
    val defaults = InterfaceSettings()
    return InterfaceSettings(
        gesturePauseMs = (prefs?.get(GesturePauseKey) ?: defaults.gesturePauseMs).coerceIn(100L, 1000L),
        hapticsEnabled = prefs?.get(HapticsKey) ?: defaults.hapticsEnabled,
        wheelSizePercent = (prefs?.get(WheelSizeKey) ?: defaults.wheelSizePercent).coerceIn(60f, 120f),
        textScrollSpeed = (prefs?.get(TextSpeedKey) ?: defaults.textScrollSpeed).coerceIn(8f, 96f),
        textScrollDelayMs = (prefs?.get(TextDelayKey) ?: defaults.textScrollDelayMs).coerceIn(0L, 5000L),
        coverVisibleByDefault = prefs?.get(CoverDefaultKey) ?: defaults.coverVisibleByDefault,
        screenAwakeMode = ScreenAwakeMode.entries.firstOrNull { it.name == prefs?.get(ScreenAwakeKey) }
            ?: defaults.screenAwakeMode
    )
}

val SettingsStore.interfaceSettingsFlow: Flow<InterfaceSettings>
    get() = store.data.map(::readInterfaceSettings)

val SettingsStore.currentInterfaceSettings: InterfaceSettings
    get() = readInterfaceSettings(currentPreferences)

suspend fun SettingsStore.setGesturePauseMs(value: Long) {
    store.edit { it[GesturePauseKey] = value.coerceIn(100L, 1000L) }
}

suspend fun SettingsStore.setWheelHapticsEnabled(value: Boolean) {
    store.edit { it[HapticsKey] = value }
}

suspend fun SettingsStore.setWheelSizePercent(value: Float) {
    store.edit { it[WheelSizeKey] = value.coerceIn(60f, 120f) }
}

suspend fun SettingsStore.setTextScrollSpeed(value: Float) {
    store.edit { it[TextSpeedKey] = value.coerceIn(8f, 96f) }
}

suspend fun SettingsStore.setTextScrollDelayMs(value: Long) {
    store.edit { it[TextDelayKey] = value.coerceIn(0L, 5000L) }
}

suspend fun SettingsStore.setCoverVisibleByDefault(value: Boolean) {
    store.edit { it[CoverDefaultKey] = value }
}

suspend fun SettingsStore.setScreenAwakeMode(value: ScreenAwakeMode) {
    store.edit { it[ScreenAwakeKey] = value.name }
}
