package com.pxr.cymatic.ui.components.eq

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.WheelNumberEditor
import com.pxr.cymatic.ui.components.primitives.CymaticInputDialog
import kotlin.math.round

@Composable
internal fun EqNumberEditor(edit: EqNumberEdit, onDismiss: () -> Unit) {
    if (LocalWheelNavigation.current != null) {
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
    } else {
        var value by remember(edit) { mutableStateOf(edit.value.toString()) }
        var invalid by remember(edit) { mutableStateOf(false) }
        CymaticInputDialog(
            edit.title,
            if (invalid) "Use ${edit.range.start}–${edit.range.endInclusive} in ${edit.step} steps"
            else "${edit.range.start}–${edit.range.endInclusive}; ${edit.step} steps",
            value,
            { value = it },
            onConfirm = {
                val parsed = value.toFloatOrNull()
                if (parsed != null && parsed.isFinite() && parsed in edit.range) {
                    edit.save((round(parsed / edit.step) * edit.step).coerceIn(edit.range))
                    onDismiss()
                } else invalid = true
            },
            onDismiss = onDismiss,
        )
    }
}
