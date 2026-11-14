package com.pxr.cymatic.ui.components.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.primitives.CymaticSlider
import com.pxr.cymatic.ui.components.screen.BaseScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun WheelSettingsOverlay(
    title: String,
    onDismiss: () -> Unit,
    onMenuLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val wheel = LocalWheelNavigation.current ?: return
    val currentTitle by rememberUpdatedState(title)
    val dismiss by rememberUpdatedState(onDismiss)
    val currentMenuLongClick by rememberUpdatedState(onMenuLongClick)
    val body by rememberUpdatedState(content)
    val overlay =
        remember(wheel) {
            WheelOverlay(
                {
                    BackHandler(enabled = wheel.overlay === LocalWheelOverlay.current) { dismiss() }
                    BaseScreen(
                        title = currentTitle,
                        onBackClick = { dismiss() },
                        onMenuLongClick = currentMenuLongClick,
                    ) { body() }
                },
                { dismiss() },
            )
        }
    DisposableEffect(wheel, overlay) {
        wheel.showOverlay(overlay)
        onDispose { wheel.removeOverlay(overlay) }
    }
}

@Composable
fun WheelNumberEditor(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onSave: suspend (Float) -> Unit,
    onDismiss: () -> Unit,
    inputToValue: (Float) -> Float = { it },
) {
    val draftState = remember(title, value, range) { mutableFloatStateOf(value.coerceIn(range)) }
    var draft by draftState
    val savingState = remember(draftState) { mutableStateOf(false) }
    var saving by savingState
    val errorState = remember(draftState) { mutableStateOf<String?>(null) }
    var error by errorState
    val scope = rememberCoroutineScope()
    var input by remember(draftState) { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val currentInputToValue by rememberUpdatedState(inputToValue)
    val currentRange by rememberUpdatedState(range)
    val currentStep by rememberUpdatedState(step)
    val save by rememberUpdatedState(onSave)
    val dismiss by rememberUpdatedState(onDismiss)
    fun commitInput(): Float? {
        val text = input ?: return draft
        val parsed = text.replace(',', '.').toFloatOrNull()
        val converted = parsed?.takeIf { it.isFinite() }?.let(currentInputToValue)
        if (converted == null || !converted.isFinite()) {
            error = "Enter a valid number."
            return null
        }
        draft = converted.coerceIn(currentRange)
        input = null
        error = null
        return draft
    }
    val commit by rememberUpdatedState(::commitInput)
    WheelSettingsOverlay(title, onDismiss) {
        val wheel = LocalWheelNavigation.current
        val overlay = LocalWheelOverlay.current
        val actions =
            remember(wheel, draftState, savingState, errorState, scope) {
                WheelActions(
                    onRotate = {
                        if (!saving) {
                            val base = commit() ?: draft
                            input = null
                            error = null
                            draft = (base + it * currentStep).coerceIn(currentRange)
                            focus.clearFocus()
                        }
                    },
                    onSelect = {
                        if (!saving) {
                            val selectedValue = commit() ?: return@WheelActions
                            focus.clearFocus()
                            saving = true
                            error = null
                            scope.launch {
                                try {
                                    save(selectedValue)
                                    dismiss()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = "Could not save. Select to retry."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                )
            }
        DisposableEffect(wheel, actions, overlay) {
            wheel?.register(actions, overlay)
            onDispose { wheel?.unregister(actions) }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val formatted = format(draft)
            val number = remember(formatted) { Regex("[+-]?[0-9]+(?:[.,][0-9]+)?").find(formatted) }
            val textStyle = LocalTextStyle.current.copy(
                fontSize = 32.sp,
                color = LocalContentColor.current,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatted.substring(0, number?.range?.first ?: 0), style = textStyle)
                BasicTextField(
                    value = input ?: number?.value.orEmpty(),
                    onValueChange = {
                        input = it
                        error = null
                    },
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .onFocusChanged { if (!it.isFocused && input != null) commit() }
                        .semantics { contentDescription = title },
                    enabled = !saving,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (commit() != null) focus.clearFocus()
                    }),
                )
                Text(formatted.substring(number?.range?.last?.plus(1) ?: formatted.length), style = textStyle)
            }
            CymaticSlider(
                value = draft,
                onValueChange = {
                    if (!saving) {
                        input = null
                        error = null
                        draft = it
                        focus.clearFocus()
                    }
                },
                valueRange = range,
            )
            Text(
                error ?: if (saving) "Saving…" else "Tap value or rotate to adjust",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
fun WheelTextEditor(
    title: String,
    value: String,
    password: Boolean = false,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
) {
    var draft by remember { mutableStateOf(value) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var requested by remember { mutableStateOf(false) }
    WheelSettingsOverlay(title, onDismiss) {
        LaunchedEffect(Unit) {
            if (!requested) {
                requested = true
                requester.requestFocus()
                keyboard?.show()
            }
        }
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = draft,
                placeholder = { if (hint != null) Text(hint) },
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .focusRequester(requester),
                singleLine = true,
                visualTransformation =
                    if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            keyboard?.hide()
                            focus.clearFocus()
                        }
                    ),
            )
            NavigationList(
                listOf(
                    NavigationItem("Save") {
                        keyboard?.hide()
                        focus.clearFocus()
                        onSave(draft)
                        onDismiss()
                    },
                    NavigationItem("Cancel") {
                        keyboard?.hide()
                        focus.clearFocus()
                        onDismiss()
                    },
                )
            )
        }
    }
}

@Composable
fun WheelReadingPage(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    WheelSettingsOverlay(title, onDismiss) {
        val wheel = LocalWheelNavigation.current
        val overlay = LocalWheelOverlay.current
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val distance = with(LocalDensity.current) { 48.dp.toPx().toInt() }
        val dismiss by rememberUpdatedState(onDismiss)
        val actions =
            remember(wheel, scroll, distance) {
                WheelActions(
                    { steps -> scope.launch { scroll.scrollTo(scroll.value + steps * distance) } },
                    { dismiss() },
                )
            }
        DisposableEffect(wheel, actions, overlay) {
            wheel?.register(actions, overlay)
            onDispose { wheel?.unregister(actions) }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(24.dp)
        ) { content() }
    }
}
