package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

data class WheelActions(
    val onRotate: (Int) -> Unit,
    val onSelect: () -> Unit,
    val onContext: () -> Unit = {}
)

class WheelNavigation {
    private val handlers = mutableStateListOf<WheelActions>()
    val actions: WheelActions? get() = handlers.lastOrNull()
    var onPlaybackRequested: () -> Unit = {}
    var overlay by mutableStateOf<WheelOverlay?>(null)

    fun register(actions: WheelActions) {
        handlers.add(actions)
    }

    fun unregister(actions: WheelActions) {
        handlers.remove(actions)
    }
}

data class WheelOverlay(val content: @Composable () -> Unit, val onDismiss: () -> Unit)

val LocalWheelNavigation = staticCompositionLocalOf<WheelNavigation?> { null }

@Composable
fun rememberWheelSelection(
    itemCount: Int,
    listState: LazyListState,
    onSelect: (Int) -> Unit,
    onContext: (Int) -> Unit = {}
): Int {
    val wheel = LocalWheelNavigation.current
    var selection by rememberSaveable { mutableIntStateOf(0) }
    val count by rememberUpdatedState(itemCount)
    val select by rememberUpdatedState(onSelect)
    val context by rememberUpdatedState(onContext)
    val actions = remember(wheel, listState) {
        WheelActions(
            onRotate = { steps ->
                if (count > 0) selection = Math.floorMod(selection + steps, count)
            },
            onSelect = { if (count > 0) select(selection.coerceIn(0, count - 1)) },
            onContext = { if (count > 0) context(selection.coerceIn(0, count - 1)) }
        )
    }

    DisposableEffect(wheel, actions) {
        wheel?.register(actions)
        onDispose { wheel?.unregister(actions) }
    }

    LaunchedEffect(selection, itemCount, wheel) {
        if (wheel != null && itemCount > 0) {
            selection = selection.coerceIn(0, itemCount - 1)
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == selection }) listState.scrollToItem(selection)
        }
    }
    return if (wheel == null || itemCount == 0) -1 else selection.coerceIn(0, itemCount - 1)
}
