package com.pxr.cymatic.ui.components.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pxr.cymatic.R
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.components.primitives.CymaticSlider
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.theme.PixelFontFamily
import kotlin.math.roundToInt

enum class OverviewMenuLevel {
    ROOT,
    EFFECTS,
    DARKEN_OPTION,
    BLUR_OPTION
}

@Composable
fun OverviewActionCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    label: String? = null,
    badge: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cellShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(cellShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), cellShape)
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background.copy(
                    alpha = 0.90f
                )
            )
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(horizontal = 4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (iconRes != null && label != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (label != null) {
                    Text(
                        text = label,
                        fontFamily = PixelFontFamily,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        lineHeight = 1.em
                    )
                }
            }
            if (badge != null) {
                Text(
                    text = badge,
                    fontFamily = PixelFontFamily,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    lineHeight = 1.em
                )
            }
        }
    }
}

@Composable
fun OverviewBottomActions(
    isOverviewMode: Boolean,
    overviewLevel: OverviewMenuLevel,
    onLevelChange: (OverviewMenuLevel) -> Unit,
    onCloseOverview: () -> Unit,
    context: Context,
    haptic: HapticFeedback,
    navController: NavController,
    useSongWallpaper: Boolean,
    wallpaperDarkenOpacity: Float,
    wallpaperGradientEnabled: Boolean,
    wallpaperBlurRadius: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOverviewMode,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
    ) {
        AnimatedContent(
            targetState = overviewLevel,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "overview_actions_nav"
        ) { level ->
            when (level) {
                OverviewMenuLevel.ROOT -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OverviewActionCell(
                            label = "WALLPAPER",
                            iconRes = R.drawable.ic_pixel_edit,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                try {
                                    val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                                    context.startActivity(
                                        Intent.createChooser(
                                            intent,
                                            "Set Wallpaper"
                                        )
                                    )
                                } catch (e: Exception) {
                                    try {
                                        val displayIntent =
                                            Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                                        context.startActivity(displayIntent)
                                    } catch (e2: Exception) {
                                        e2.printStackTrace()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        OverviewActionCell(
                            label = "EFFECTS",
                            iconRes = R.drawable.ic_pixel_effects,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                onLevelChange(OverviewMenuLevel.EFFECTS)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        OverviewActionCell(
                            label = "SETTINGS",
                            iconRes = R.drawable.ic_pixel_settings,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                onCloseOverview()
                                navController.navigate(Screen.Settings.route)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OverviewMenuLevel.EFFECTS -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OverviewActionCell(

                            iconRes = R.drawable.ic_pixel_arrow_left,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                onLevelChange(OverviewMenuLevel.ROOT)
                            },
                            modifier = Modifier.weight(0.8f)
                        )

                        OverviewActionCell(
                            label = "SONG ART",
                            badge = if (useSongWallpaper) "ON" else "OFF",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                LauncherStore.setUseSongWallpaper(!useSongWallpaper)
                            },
                            modifier = Modifier.weight(1.05f)
                        )

                        OverviewActionCell(
                            label = "DARKEN",
                            badge = if (useSongWallpaper) "AUTO" else "${(wallpaperDarkenOpacity * 100).roundToInt()}%",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                onLevelChange(OverviewMenuLevel.DARKEN_OPTION)
                            },
                            modifier = Modifier.weight(1.0f)
                        )

                        OverviewActionCell(
                            label = "GRADIENT",
                            badge = if (useSongWallpaper) "AUTO" else (if (wallpaperGradientEnabled) "ON" else "OFF"),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                if (!useSongWallpaper) {
                                    LauncherStore.setWallpaperGradientEnabled(!wallpaperGradientEnabled)
                                }
                            },
                            modifier = Modifier.weight(1.05f)
                        )

                        OverviewActionCell(
                            label = "BLUR",
                            badge = if (useSongWallpaper) "AUTO" else "${wallpaperBlurRadius.roundToInt()}px",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                onLevelChange(OverviewMenuLevel.BLUR_OPTION)
                            },
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }

                OverviewMenuLevel.DARKEN_OPTION -> {
                    val cardShape = RoundedCornerShape(12.dp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        OverviewActionCell(

                            iconRes = R.drawable.ic_pixel_arrow_left,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                onLevelChange(OverviewMenuLevel.EFFECTS)
                            },
                            modifier = Modifier.width(68.dp)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(cardShape)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                    cardShape
                                )
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.90f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DARKEN",
                                        fontFamily = PixelFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "${(wallpaperDarkenOpacity * 100).roundToInt()}%",
                                        fontFamily = PixelFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                CymaticSlider(
                                    value = wallpaperDarkenOpacity,
                                    onValueChange = { LauncherStore.setWallpaperDarkenOpacity(it) },
                                    valueRange = 0.0f..1.0f
                                )
                            }
                        }
                    }
                }

                OverviewMenuLevel.BLUR_OPTION -> {
                    val cardShape = RoundedCornerShape(12.dp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        OverviewActionCell(

                            iconRes = R.drawable.ic_pixel_arrow_left,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                onLevelChange(OverviewMenuLevel.EFFECTS)
                            },
                            modifier = Modifier.width(68.dp)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(cardShape)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                    cardShape
                                )
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.90f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "BLUR",
                                        fontFamily = PixelFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "${wallpaperBlurRadius.roundToInt()} px",
                                        fontFamily = PixelFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                CymaticSlider(
                                    value = wallpaperBlurRadius,
                                    onValueChange = { LauncherStore.setWallpaperBlurRadius(it) },
                                    valueRange = 0.0f..30.0f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
