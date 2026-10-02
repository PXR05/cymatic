package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.first

data class WheelActions(
    val onRotate: (Int) -> Unit,
    val onSelect: () -> Unit,
    val onContext: () -> Unit = {},
    val onMovementStarted: () -> Unit = {}
)

class WheelNavigation {
    private val handlers = mutableStateListOf<WheelActions>()
    val actions: WheelActions? get() = handlers.lastOrNull()
    var onPlaybackRequested: () -> Unit = {}
    var onBackRequested: (() -> Unit)? = null
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
        var canWrap = false
        WheelActions(
            onRotate = { steps ->
                if (count > 0 && steps != 0) {
                    val last = count - 1
                    val current = selection.coerceIn(0, last)
                    selection = when {
                        canWrap && steps > 0 && current == last -> (steps - 1).coerceIn(0, last)
                        canWrap && steps < 0 && current == 0 -> (last + steps + 1).coerceIn(0, last)
                        else -> (current + steps).coerceIn(0, last)
                    }
                    canWrap = false
                }
            },
            onSelect = { if (count > 0) select(selection.coerceIn(0, count - 1)) },
            onContext = { if (count > 0) context(selection.coerceIn(0, count - 1)) },
            onMovementStarted = { canWrap = true }
        )
    }

    DisposableEffect(wheel, actions) {
        wheel?.register(actions)
        onDispose { wheel?.unregister(actions) }
    }

    val viewport = listState.layoutInfo.viewportSize
    LaunchedEffect(selection, itemCount, wheel, viewport) {
        if (wheel != null && itemCount > 0) {
            selection = selection.coerceIn(0, itemCount - 1)
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == selection }) {
                listState.scrollToItem(selection)
            }
            val layout = snapshotFlow { listState.layoutInfo }.first {
                it.viewportEndOffset > it.viewportStartOffset &&
                    it.visibleItemsInfo.any { item -> item.index == selection }
            }
            val item = layout.visibleItemsInfo.first { it.index == selection }
            val start = layout.viewportStartOffset + layout.beforeContentPadding
            val end = layout.viewportEndOffset - layout.afterContentPadding
            val offset = when {
                item.size > end - start || item.offset < start -> item.offset - start
                item.offset + item.size > end -> item.offset + item.size - end
                else -> 0
            }
            if (offset != 0) listState.scrollBy(offset.toFloat())
        }
    }
    return if (wheel == null || itemCount == 0) -1 else selection.coerceIn(0, itemCount - 1)
}
