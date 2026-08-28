package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.components.primitives.CymaticSlider
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LauncherSettingsScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val use24Hour by LauncherStore.use24HourFlow.collectAsState(initial = true)
    val showClock by LauncherStore.showClockFlow.collectAsState(initial = true)
    val showDay by LauncherStore.showDayFlow.collectAsState(initial = true)
    val showDate by LauncherStore.showDateFlow.collectAsState(initial = true)
    val showPinnedLabels by LauncherStore.showPinnedLabelsFlow.collectAsState(initial = false)
    val showFolderLabels by LauncherStore.showFolderLabelsFlow.collectAsState(initial = true)
    val showAllAppsLabels by LauncherStore.showAllAppsLabelsFlow.collectAsState(initial = true)
    val appIconScale by LauncherStore.appIconScaleFlow.collectAsState(initial = 1.0f)
    val darkenOpacity by LauncherStore.wallpaperDarkenOpacityFlow.collectAsState(initial = 0.0f)
    val gradientEnabled by LauncherStore.wallpaperGradientEnabledFlow.collectAsState(initial = false)
    val blurRadius by LauncherStore.wallpaperBlurRadiusFlow.collectAsState(initial = 0.0f)
    val useSongWallpaper by LauncherStore.useSongWallpaperFlow.collectAsState(initial = false)

    BaseScreen(
        title = "Launcher",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(24.dp, 16.dp)
        ) {
            Text(
                text = "Clock",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LauncherToggleRow(
                title = "24-Hour Time",
                subtitle = "Use 24-hour clock format",
                checked = use24Hour,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setUse24Hour(value) }
                }
            )

            LauncherToggleRow(
                title = "Show Clock",
                subtitle = "Show the clock on the home screen",
                checked = showClock,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowClock(value) }
                }
            )

            LauncherToggleRow(
                title = "Show Day",
                subtitle = "Show the weekday under the clock",
                checked = showDay,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowDay(value) }
                }
            )

            LauncherToggleRow(
                title = "Show Date",
                subtitle = "Show the date under the clock",
                checked = showDate,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowDate(value) }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Apps",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LauncherToggleRow(
                title = "Most Used Labels",
                subtitle = "Show app names under most used icons",
                checked = showPinnedLabels,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowPinnedLabels(value) }
                }
            )

            LauncherToggleRow(
                title = "Folder Labels",
                subtitle = "Show app names inside folders",
                checked = showFolderLabels,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowFolderLabels(value) }
                }
            )

            LauncherToggleRow(
                title = "All Apps Labels",
                subtitle = "Show app names in the app drawer",
                checked = showAllAppsLabels,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setShowAllAppsLabels(value) }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Icon Size",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scale app icons (${(appIconScale * 100).roundToInt()}%)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = "${(appIconScale * 100).roundToInt()}%",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                CymaticSlider(
                    value = appIconScale,
                    onValueChange = { LauncherStore.setAppIconScale(it) },
                    valueRange = 0.5f..2.0f
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Wallpaper Effects",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LauncherToggleRow(
                title = "Use Song Artwork as Wallpaper",
                subtitle = "Apply Apple Music style ambient blur and extended gradient from current track",
                checked = useSongWallpaper,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setUseSongWallpaper(value) }
                }
            )

            if (useSongWallpaper) {
                Text(
                    text = "Automatic Apple Music style effects are pre-applied when artwork is available.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Darken Opacity",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dim wallpaper behind content",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = "${(darkenOpacity * 100).roundToInt()}%",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                CymaticSlider(
                    value = darkenOpacity,
                    onValueChange = { LauncherStore.setWallpaperDarkenOpacity(it) },
                    valueRange = 0.0f..1.0f
                )
            }

            LauncherToggleRow(
                title = "Gradient Darken",
                subtitle = "Keep top clear, darken bottom for visibility",
                checked = gradientEnabled,
                onCheckedChange = { value ->
                    scope.launch { LauncherStore.setWallpaperGradientEnabled(value) }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wallpaper Blur",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Blur background wallpaper",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = "${blurRadius.roundToInt()} px",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                CymaticSlider(
                    value = blurRadius,
                    onValueChange = { LauncherStore.setWallpaperBlurRadius(it) },
                    valueRange = 0.0f..30.0f
                )
            }
        }
    }
}

@Composable
private fun LauncherToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = if (checked) "I" else "O",
            fontSize = 16.sp,
            color = if (checked) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .background(if (checked) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                .border(1.dp, MaterialTheme.colorScheme.onBackground)
                .clickable(
                    onClick = { onCheckedChange(!checked) },
                    indication = null,
                    interactionSource = null
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}
