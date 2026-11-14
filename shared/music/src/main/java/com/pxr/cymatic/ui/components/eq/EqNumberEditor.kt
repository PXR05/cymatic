package com.pxr.cymatic.ui.components.eq

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pxr.cymatic.ui.components.common.WheelNumberEditor

@Composable
internal fun EqNumberEditor(edit: EqNumberEdit, onDismiss: () -> Unit) {
    WheelNumberEditor(
        edit.title,
        edit.value,
        edit.range,
        edit.step,
        edit.format,
        edit.save,
        onDismiss,
        inputToValue = edit.inputToValue,
    )
}
