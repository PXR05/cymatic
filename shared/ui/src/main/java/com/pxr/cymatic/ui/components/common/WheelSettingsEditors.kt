package com.pxr.cymatic.ui.components.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
fun WheelSettingsOverlay(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val wheel = LocalWheelNavigation.current ?: return
    val currentTitle by rememberUpdatedState(title)
    val dismiss by rememberUpdatedState(onDismiss)
    val body by rememberUpdatedState(content)
    val overlay =
        remember(wheel) {
            WheelOverlay(
                {
                    BackHandler(enabled = wheel.overlay === LocalWheelOverlay.current) { dismiss() }
                    BaseScreen(title = currentTitle, onBackClick = { dismiss() }) { body() }
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
) {
    val draftState = remember(title, value, range) { mutableFloatStateOf(value.coerceIn(range)) }
    var draft by draftState
    val savingState = remember(draftState) { mutableStateOf(false) }
    var saving by savingState
    val errorState = remember(draftState) { mutableStateOf<String?>(null) }
    var error by errorState
    val scope = rememberCoroutineScope()
    val currentRange by rememberUpdatedState(range)
    val currentStep by rememberUpdatedState(step)
    val save by rememberUpdatedState(onSave)
    val dismiss by rememberUpdatedState(onDismiss)
    WheelSettingsOverlay(title, onDismiss) {
        val wheel = LocalWheelNavigation.current
        val overlay = LocalWheelOverlay.current
        val actions =
            remember(wheel, draftState, savingState, errorState, scope) {
                WheelActions(
                    onRotate = {
                        if (!saving) draft = (draft + it * currentStep).coerceIn(currentRange)
                    },
                    onSelect = {
                        if (!saving) {
                            val selectedValue = draft
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
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(format(draft), fontSize = 32.sp)
            CymaticSlider(
                value = draft,
                onValueChange = { if (!saving) draft = it },
                valueRange = range,
            )
            Text(
                error ?: if (saving) "Saving…" else "Rotate to adjust · Select to save",
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
                modifier = Modifier.fillMaxWidth().padding(24.dp).focusRequester(requester),
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
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp)) { content() }
    }
}
