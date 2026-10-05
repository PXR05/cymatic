package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
    val onMovementStarted: () -> Unit = {},
)

class WheelNavigation {
    private data class Handler(val actions: WheelActions, val overlay: WheelOverlay?)

    private val handlers = mutableStateListOf<Handler>()
    private val overlayStack = mutableStateListOf<WheelOverlay>()
    val actions: WheelActions?
        get() = handlers.lastOrNull { it.overlay === overlay }?.actions

    val overlays: List<WheelOverlay>
        get() = overlayStack

    val overlay: WheelOverlay?
        get() = overlayStack.lastOrNull()

    var onPlaybackRequested: () -> Unit = {}
    var onBackRequested: (() -> Unit)? = null

    fun register(actions: WheelActions, overlay: WheelOverlay? = null) {
        handlers.add(Handler(actions, overlay))
    }

    fun unregister(actions: WheelActions) {
        handlers.removeAll { it.actions === actions }
    }

    fun showOverlay(overlay: WheelOverlay) {
        if (overlayStack.none { it === overlay }) overlayStack.add(overlay)
    }

    fun removeOverlay(overlay: WheelOverlay) {
        overlayStack.removeAll { it === overlay }
    }

    fun clearOverlays() {
        overlayStack.toList().asReversed().forEach { it.onDismiss() }
        overlayStack.clear()
    }
}

data class WheelOverlay(val content: @Composable () -> Unit, val onDismiss: () -> Unit)

val LocalWheelNavigation = staticCompositionLocalOf<WheelNavigation?> { null }
val LocalWheelOverlay = staticCompositionLocalOf<WheelOverlay?> { null }

@Composable
fun rememberWheelSelection(
    itemCount: Int,
    listState: LazyListState,
    onSelect: (Int) -> Unit,
    selectableIndices: List<Int>? = null,
    onContext: (Int) -> Unit = {},
): Int {
    val wheel = LocalWheelNavigation.current
    val overlay = LocalWheelOverlay.current
    var selection by rememberSaveable { mutableIntStateOf(0) }
    var scrollDirection by remember { mutableIntStateOf(0) }
    val count by rememberUpdatedState(itemCount)
    val select by rememberUpdatedState(onSelect)
    val context by rememberUpdatedState(onContext)
    val availableIndices by rememberUpdatedState(selectableIndices)
    val actions =
        remember(wheel, listState) {
            var canWrap = false
            WheelActions(
                onRotate = { steps ->
                    val available = availableIndices
                    val choices = available?.size ?: count
                    if (choices > 0 && steps != 0) {
                        scrollDirection = if (steps > 0) 1 else -1
                        val last = choices - 1
                        val current =
                            available?.indexOf(selection)?.coerceAtLeast(0)
                                ?: selection.coerceIn(0, last)
                        val nextPosition =
                            when {
                                canWrap && steps > 0 && current == last ->
                                    (steps - 1).coerceIn(0, last)
                                canWrap && steps < 0 && current == 0 ->
                                    (last + steps + 1).coerceIn(0, last)
                                else -> (current + steps).coerceIn(0, last)
                            }
                        val next = available?.get(nextPosition) ?: nextPosition
                        listState.scrollWheelSelectionIntoView(next, scrollDirection)
                        selection = next
                        canWrap = false
                    }
                },
                onSelect = {
                    if (
                        count > 0 &&
                            (availableIndices == null || selection in availableIndices.orEmpty())
                    )
                        select(selection.coerceIn(0, count - 1))
                },
                onContext = {
                    if (
                        count > 0 &&
                            (availableIndices == null || selection in availableIndices.orEmpty())
                    )
                        context(selection.coerceIn(0, count - 1))
                },
                onMovementStarted = { canWrap = true },
            )
        }

    DisposableEffect(wheel, actions, overlay) {
        wheel?.register(actions, overlay)
        onDispose { wheel?.unregister(actions) }
    }

    val viewport = listState.layoutInfo.viewportSize
    LaunchedEffect(itemCount, wheel, listState, viewport, selectableIndices) {
        if (wheel != null && itemCount > 0) {
            selection = selection.coerceIn(0, itemCount - 1)
            if (selectableIndices != null && selection !in selectableIndices) {
                selection = selectableIndices.firstOrNull() ?: return@LaunchedEffect
            }
            snapshotFlow { listState.layoutInfo }
                .first {
                    it.viewportSize.height > 0 && it.visibleItemsInfo.isNotEmpty()
                }
            listState.scrollWheelSelectionIntoView(selection, scrollDirection)
        }
    }
    return if (wheel == null || itemCount == 0 || selectableIndices?.isEmpty() == true) -1
    else selection.coerceIn(0, itemCount - 1)
}
