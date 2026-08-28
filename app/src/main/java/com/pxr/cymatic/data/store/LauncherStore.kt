package com.pxr.cymatic.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pxr.cymatic.data.launcher.PinnedItem
import com.pxr.cymatic.data.launcher.PinnedLayoutCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.launcherDataStore by preferencesDataStore("launcher")

object LauncherStore {
    private lateinit var dataStore: DataStore<Preferences>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val PINNED_PACKAGES_KEY = stringPreferencesKey("PINNED_PACKAGES")
    private val PINNED_LAYOUT_KEY = stringPreferencesKey("PINNED_LAYOUT")
    private val USE_24_HOUR_KEY = booleanPreferencesKey("USE_24_HOUR")
    private val SHOW_CLOCK_KEY = booleanPreferencesKey("SHOW_CLOCK")
    private val SHOW_DAY_KEY = booleanPreferencesKey("SHOW_DAY")
    private val SHOW_DATE_KEY = booleanPreferencesKey("SHOW_DATE")
    private val SHOW_PINNED_LABELS_KEY = booleanPreferencesKey("SHOW_PINNED_LABELS")
    private val SHOW_FOLDER_LABELS_KEY = booleanPreferencesKey("SHOW_FOLDER_LABELS")
    private val SHOW_ALL_APPS_LABELS_KEY = booleanPreferencesKey("SHOW_ALL_APPS_LABELS")
    private val APP_ICON_SCALE_KEY = floatPreferencesKey("APP_ICON_SCALE")
    private val WALLPAPER_DARKEN_OPACITY_KEY = floatPreferencesKey("WALLPAPER_DARKEN_OPACITY")
    private val WALLPAPER_GRADIENT_ENABLED_KEY = booleanPreferencesKey("WALLPAPER_GRADIENT_ENABLED")
    private val WALLPAPER_BLUR_RADIUS_KEY = floatPreferencesKey("WALLPAPER_BLUR_RADIUS")
    private val USE_SONG_WALLPAPER_KEY = booleanPreferencesKey("USE_SONG_WALLPAPER")

    fun init(context: Context) {
        dataStore = context.applicationContext.launcherDataStore
    }

    private fun requireInit() {
        check(::dataStore.isInitialized) {
            "LauncherStore.init(context) must be called before use."
        }
    }

    private val store: DataStore<Preferences>
        get() {
            requireInit()
            return dataStore
        }

    val pinnedLayoutFlow: Flow<List<PinnedItem>>
        get() = store.data.map { prefs ->
            val layout = prefs[PINNED_LAYOUT_KEY]
                ?.let(PinnedLayoutCodec::decode)
                ?.takeIf { it.isNotEmpty() }
            layout
                ?: prefs[PINNED_PACKAGES_KEY]
                    ?.let { legacyPackages -> parsePackages(legacyPackages).map(PinnedItem::App) }
                    ?: emptyList()
        }

    val hasStoredPinnedLayoutFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs.contains(PINNED_LAYOUT_KEY) || prefs.contains(PINNED_PACKAGES_KEY)
        }

    suspend fun getPinnedLayout(): List<PinnedItem> = pinnedLayoutFlow.first()

    fun setPinnedLayout(items: List<PinnedItem>) {
        scope.launch {
            store.edit { prefs ->
                prefs[PINNED_LAYOUT_KEY] = PinnedLayoutCodec.encode(items)
                prefs.remove(PINNED_PACKAGES_KEY)
            }
        }
    }

    val use24HourFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[USE_24_HOUR_KEY] ?: true
        }

    val showClockFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[SHOW_CLOCK_KEY] ?: true
        }

    val showDayFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[SHOW_DAY_KEY] ?: true
        }

    val showDateFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[SHOW_DATE_KEY] ?: true
        }

    val showPinnedLabelsFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[SHOW_PINNED_LABELS_KEY] ?: false
        }

    val showFolderLabelsFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[SHOW_FOLDER_LABELS_KEY] ?: true
        }

    val showAllAppsLabelsFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[SHOW_ALL_APPS_LABELS_KEY] ?: true
        }

    val appIconScaleFlow: Flow<Float>
        get() = store.data.map { prefs ->
            prefs[APP_ICON_SCALE_KEY] ?: 1.0f
        }

    val wallpaperDarkenOpacityFlow: Flow<Float>
        get() = store.data.map { prefs ->
            prefs[WALLPAPER_DARKEN_OPACITY_KEY] ?: 0.0f
        }

    val wallpaperGradientEnabledFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[WALLPAPER_GRADIENT_ENABLED_KEY] ?: false
        }

    val wallpaperBlurRadiusFlow: Flow<Float>
        get() = store.data.map { prefs ->
            prefs[WALLPAPER_BLUR_RADIUS_KEY] ?: 0.0f
        }

    val useSongWallpaperFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[USE_SONG_WALLPAPER_KEY] ?: false
        }

    fun setUse24Hour(value: Boolean) {
        scope.launch {
            store.edit { prefs ->
                prefs[USE_24_HOUR_KEY] = value
            }
        }
    }

    fun setShowClock(value: Boolean) {
        scope.launch {
            store.edit { prefs ->
                prefs[SHOW_CLOCK_KEY] = value
            }
        }
    }

    fun setShowDay(value: Boolean) {
        scope.launch {
            store.edit { prefs ->
                prefs[SHOW_DAY_KEY] = value
            }
        }
    }

    fun setShowDate(value: Boolean) {
        scope.launch {
            store.edit { prefs ->
                prefs[SHOW_DATE_KEY] = value
            }
        }
    }

    fun setShowPinnedLabels(value: Boolean) {
        scope.launch {
            store.edit { prefs ->
                prefs[SHOW_PINNED_LABELS_KEY] = value
            }
        }
    }

    fun setShowFolderLabels(value: Boolean) {
        scope.launch {
            store.edit { prefs ->
                prefs[SHOW_FOLDER_LABELS_KEY] = value
            }
        }
    }

    fun setShowAllAppsLabels(value: Boolean) {
        scope.launch {
            store.edit { prefs ->
                prefs[SHOW_ALL_APPS_LABELS_KEY] = value
            }
        }
    }

    fun setAppIconScale(value: Float) {
        scope.launch {
            store.edit { prefs ->
                prefs[APP_ICON_SCALE_KEY] = value.coerceIn(0.5f, 2.0f)
            }
        }
    }

    fun setWallpaperDarkenOpacity(value: Float) {
        scope.launch {
            store.edit { prefs ->
                prefs[WALLPAPER_DARKEN_OPACITY_KEY] = value.coerceIn(0.0f, 1.0f)
            }
        }
    }

    fun setWallpaperGradientEnabled(value: Boolean) {
        scope.launch {
            store.edit { prefs ->
                prefs[WALLPAPER_GRADIENT_ENABLED_KEY] = value
            }
        }
    }

    fun setWallpaperBlurRadius(value: Float) {
        scope.launch {
            store.edit { prefs ->
                prefs[WALLPAPER_BLUR_RADIUS_KEY] = value.coerceIn(0.0f, 30.0f)
            }
        }
    }

    fun setUseSongWallpaper(value: Boolean) {
        scope.launch {
            store.edit { prefs ->
                prefs[USE_SONG_WALLPAPER_KEY] = value
            }
        }
    }

    private fun parsePackages(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
