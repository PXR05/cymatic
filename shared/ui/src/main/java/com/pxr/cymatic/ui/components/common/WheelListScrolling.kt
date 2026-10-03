package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState

internal fun LazyListState.scrollWheelSelectionIntoView(index: Int, direction: Int) {
    val initial = layoutInfo
    if (initial.contentEnd <= initial.contentStart || initial.visibleItemsInfo.isEmpty()) return
    if (index !in 0 until initial.totalItemsCount) return
    val item = initial.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        val movingDown = index > initial.visibleItemsInfo.last().index
        if (movingDown) {
            val size = initial.visibleItemsInfo.last().size
            val buffer =
                if (index < initial.totalItemsCount - 1) size + initial.mainAxisItemSpacing else 0
            val position =
                (initial.contentEnd - initial.contentStart - size - buffer).coerceAtLeast(0)
            requestScrollToItem(index, -position)
        } else {
            requestScrollToItem((index - 1).coerceAtLeast(0))
        }
    } else {
        val offset = initial.wheelSelectionScrollOffset(index, direction)
        requestScrollToItem(index, initial.contentStart - item.offset + offset)
    }
}

private fun LazyListLayoutInfo.wheelSelectionScrollOffset(index: Int, direction: Int): Int {
    val item = visibleItemsInfo.first { it.index == index }
    val start = contentStart
    val end = contentEnd
    val bottom = item.offset + item.size
    if (item.size >= end - start || item.offset < start) return item.offset - start
    if (bottom > end) return bottom - end
    return when {
        direction > 0 && index < totalItemsCount - 1 -> {
            val next = visibleItemsInfo.firstOrNull { it.index == index + 1 }
            val nextBottom =
                next?.let { it.offset + it.size } ?: (bottom + mainAxisItemSpacing + item.size)
            (nextBottom - end).coerceIn(0, item.offset - start)
        }

        direction < 0 && index > 0 -> {
            val previous = visibleItemsInfo.firstOrNull { it.index == index - 1 }
            val previousTop = previous?.offset ?: (item.offset - mainAxisItemSpacing - item.size)
            (previousTop - start).coerceIn(bottom - end, 0)
        }

        else -> 0
    }
}

private val LazyListLayoutInfo.contentStart: Int
    get() = viewportStartOffset + beforeContentPadding

private val LazyListLayoutInfo.contentEnd: Int
    get() = viewportEndOffset - afterContentPadding
