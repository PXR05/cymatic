package com.pxr.cymatic.data.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object LibraryChanges {
    private val mutableVersion = MutableStateFlow(0L)
    val version = mutableVersion.asStateFlow()
    fun notifyChanged() { mutableVersion.update { it + 1 } }
}
