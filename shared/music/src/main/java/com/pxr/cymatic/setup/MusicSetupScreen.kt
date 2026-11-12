package com.pxr.cymatic.setup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pxr.cymatic.data.media.LocalLibraryScanner
import com.pxr.cymatic.data.media.LocalScanProgress
import com.pxr.cymatic.sync.CatalogProgress
import com.pxr.cymatic.sync.LibraryCatalogManager
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.LocalWheelOverlay
import com.pxr.cymatic.ui.components.common.WheelActions
import com.pxr.cymatic.ui.components.common.WheelSelectionNavigator
import com.pxr.cymatic.ui.components.common.hasStoragePermission
import kotlinx.coroutines.delay

private data class SetupAction(
    val label: String,
    val enabled: Boolean = true,
    val showButton: Boolean = true,
    val run: () -> Unit
)

@Composable
fun MusicSetupScreen(model: MusicSetupViewModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val local by LocalLibraryScanner.progress.collectAsState()
    val catalog by LibraryCatalogManager.progress.collectAsState()
    val context = LocalContext.current
    val activity = context.setupActivity()
    var granted by remember { mutableStateOf(hasStoragePermission(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val permission = if (android.os.Build.VERSION.SDK_INT >= 33)
        android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
            denied = !it
            if (it) model.scan()
        }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) model.addFolder(it)
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasStoragePermission(context)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(granted, state.step) {
        if (granted && state.step == MusicSetupStep.DEVICE) model.scan()
        else if (state.step == MusicSetupStep.SCANNING && local == LocalScanProgress.Idle) model.scan()
    }
    BackHandler {
        if (state.step == MusicSetupStep.CHOICE || state.step == MusicSetupStep.NAVIGATE)
            activity?.finish()
        else model.back()
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.step == MusicSetupStep.LOADING) LoadingState()
        else key(state.step) {
            SetupPage(
                state, model, local, catalog,
                onPermission = {
                    val blocked =
                        denied && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            permission
                        )
                    if (blocked) context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                    else permissionRequest.launch(permission)
                },
                permissionDenied = denied,
                permissionBlocked = denied && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    permission
                ),
                onFolder = { folder.launch(null) },
            )
        }
    }
}

@Composable
private fun SetupPage(
    state: MusicSetupState,
    model: MusicSetupViewModel,
    local: LocalScanProgress,
    catalog: CatalogProgress,
    onPermission: () -> Unit,
    permissionDenied: Boolean,
    permissionBlocked: Boolean,
    onFolder: () -> Unit,
) {
    val step = state.step
    val foreground = MaterialTheme.colorScheme.onBackground
    val background = MaterialTheme.colorScheme.background
    val secondary = MaterialTheme.colorScheme.secondary
    val keyboard = LocalSoftwareKeyboardController.current
    var sourceIndex by rememberSaveable { mutableIntStateOf(0) }
    var tutorialIndex by rememberSaveable { mutableIntStateOf(if (step == MusicSetupStep.SELECT) state.tutorialFolder else 0) }
    var actionIndex by remember { mutableIntStateOf(0) }
    val navigation = step in setOf(
        MusicSetupStep.NAVIGATE,
        MusicSetupStep.SELECT,
        MusicSetupStep.BACK,
        MusicSetupStep.BROWSE,
        MusicSetupStep.SUCCESS
    )
    val field =
        step in setOf(MusicSetupStep.ADDRESS, MusicSetupStep.USERNAME, MusicSetupStep.PASSWORD)
    val remoteLoading = step == MusicSetupStep.CONNECTING ||
            (step == MusicSetupStep.CONNECTED && catalog == CatalogProgress.Refreshing)
    val centered =
        step == MusicSetupStep.NAVIGATE || step == MusicSetupStep.SUCCESS || remoteLoading
    val number = when (step) {
        MusicSetupStep.NAVIGATE, MusicSetupStep.ADDRESS -> 1
        MusicSetupStep.SELECT -> if (state.backPracticed) 3 else 2
        MusicSetupStep.USERNAME -> 2
        MusicSetupStep.BACK, MusicSetupStep.BROWSE, MusicSetupStep.SUCCESS, MusicSetupStep.PASSWORD -> 3
        else -> 0
    }
    val title = when (step) {
        MusicSetupStep.NAVIGATE -> "Fill the bar"
        MusicSetupStep.SELECT -> "Find the Golden record"
        MusicSetupStep.BACK -> "Go back"
        MusicSetupStep.BROWSE -> MusicSetupTutorial.folders[state.tutorialFolder]
        MusicSetupStep.SUCCESS -> "Record found"
        MusicSetupStep.CHOICE -> "Choose your music"
        MusicSetupStep.DEVICE -> if (permissionDenied) "Music access is off" else "Allow music access"
        MusicSetupStep.SCANNING -> when (local) {
            is LocalScanProgress.Complete -> if (local.count > 0) "Music found" else "No music found"
            LocalScanProgress.Failed -> "Scan could not finish"
            else -> "Finding music"
        }

        MusicSetupStep.ADDRESS -> "Server address"
        MusicSetupStep.USERNAME -> "Username"
        MusicSetupStep.PASSWORD -> "Password"
        MusicSetupStep.CONNECTING -> "Connecting"
        MusicSetupStep.CONNECTED -> when (catalog) {
            CatalogProgress.Refreshing -> "Loading your library"
            is CatalogProgress.Failed -> "Library could not load"
            is CatalogProgress.Complete -> if (catalog.catalog.tracks.isEmpty()) "The library is empty" else "Library ready"
            else -> "Connected to AudioStream"
        }

        else -> "Set up music"
    }
    val description = when (step) {
        MusicSetupStep.NAVIGATE -> "Clockwise adds. Counterclockwise subtracts."
        MusicSetupStep.SELECT -> "Press center to open a folder."
        MusicSetupStep.BACK -> "Use Left or Menu."
        MusicSetupStep.BROWSE -> "Select the record, or go back."
        MusicSetupStep.CHOICE -> "Local music or AudioStream."
        MusicSetupStep.DEVICE -> if (permissionDenied) "Allow access or choose another source."
        else "Allow access to find device music."

        MusicSetupStep.SCANNING -> when (local) {
            is LocalScanProgress.Complete -> if (local.count > 0) "${local.count} songs found." else "Choose a music folder."
            LocalScanProgress.Failed -> "Try again or choose a folder."
            else -> "Scanning device music."
        }

        MusicSetupStep.ADDRESS -> "Your AudioStream server URL."
        MusicSetupStep.CONNECTING -> "Signing in."
        MusicSetupStep.CONNECTED -> when (catalog) {
            CatalogProgress.Refreshing -> "Loading songs and playlists."
            is CatalogProgress.Failed -> "Check the connection and retry."
            is CatalogProgress.Complete -> if (catalog.catalog.tracks.isEmpty()) "Add music on AudioStream, then refresh."
            else "Stream now. Keep offline from the library menu."

            else -> ""
        }

        else -> ""
    }
    val skip = SetupAction(
        "Skip",
        enabled = !state.busy || step == MusicSetupStep.CONNECTING,
        showButton = false
    ) {
        keyboard?.hide()
        if (navigation) model.choose(MusicSetupStep.CHOICE)
        else {
            if (step == MusicSetupStep.CONNECTING) model.cancelConnection()
            model.finish(false)
        }
    }
    LaunchedEffect(step, state.navigationProgress) {
        if (step == MusicSetupStep.NAVIGATE && state.navigationProgress >= 100) {
            delay(600)
            model.next()
        }
    }
    val libraryReady =
        (step == MusicSetupStep.SCANNING && local is LocalScanProgress.Complete && local.count > 0) ||
                (step == MusicSetupStep.CONNECTED && catalog is CatalogProgress.Complete)
    LaunchedEffect(libraryReady, state.storageError) {
        if (libraryReady && !state.storageError) model.finish(true)
    }
    val wheelFill by animateFloatAsState(
        state.navigationProgress / 100f,
        tween(160),
        label = "Wheel progress"
    )
    val actions = buildList {
        when (step) {
            MusicSetupStep.SELECT -> add(
                SetupAction(
                    "Open folder",
                    showButton = false
                ) { model.openTutorialFolder(tutorialIndex) })

            MusicSetupStep.BROWSE -> add(
                SetupAction(
                    "Select item",
                    showButton = false
                ) { model.selectTutorialItem(tutorialIndex) })

            MusicSetupStep.SUCCESS -> add(
                SetupAction(
                    "Continue",
                    showButton = false
                ) { model.next() })

            MusicSetupStep.CHOICE -> add(
                SetupAction(
                    "Choose source",
                    !state.busy,
                    showButton = false
                ) {
                    model.choose(if (sourceIndex == 0) MusicSetupStep.DEVICE else MusicSetupStep.ADDRESS)
                })

            MusicSetupStep.DEVICE -> add(
                SetupAction(
                    if (permissionBlocked) "Open app settings" else if (permissionDenied) "Try again" else "Allow access",
                    !state.busy,
                    run = onPermission
                )
            )

            MusicSetupStep.SCANNING -> when (local) {
                is LocalScanProgress.Complete -> {
                    if (local.count == 0) add(
                        SetupAction(
                            "Choose folder",
                            !state.busy,
                            run = onFolder
                        )
                    )
                }

                LocalScanProgress.Failed -> {
                    add(SetupAction("Try again", !state.busy) { model.scan() })
                    add(SetupAction("Choose folder", !state.busy, run = onFolder))
                }

                else -> Unit
            }

            MusicSetupStep.ADDRESS, MusicSetupStep.USERNAME -> add(
                SetupAction(
                    "Continue",
                    !state.busy,
                    showButton = false
                ) { model.next() })

            MusicSetupStep.PASSWORD -> add(
                SetupAction(
                    "Connect",
                    !state.busy,
                    showButton = false
                ) { keyboard?.hide(); model.next() })

            MusicSetupStep.CONNECTING -> add(SetupAction("Cancel connection") { model.cancelConnection() })
            MusicSetupStep.CONNECTED -> {
                if (catalog is CatalogProgress.Failed || catalog == CatalogProgress.Cancelled || catalog == CatalogProgress.Idle)
                    add(SetupAction("Try again", !state.busy) { model.retryCatalog() })
            }

            else -> Unit
        }
        if (state.storageError) {
            if (libraryReady) add(
                SetupAction(
                    "Try saving again",
                    !state.busy
                ) { model.finish(true) })
            add(
                SetupAction(
                    "Continue without saving",
                    !state.busy
                ) { model.continueWithoutSaving() })
        }
    }
    val wheel = LocalWheelNavigation.current
    val overlay = LocalWheelOverlay.current
    val latestActions by rememberUpdatedState(actions)
    val latestActionIndex by rememberUpdatedState(actionIndex)
    val latestSourceIndex by rememberUpdatedState(sourceIndex)
    val latestTutorialIndex by rememberUpdatedState(tutorialIndex)
    val latestFolder by rememberUpdatedState(state.tutorialFolder)
    val handler = remember(wheel, step) {
        val navigator = WheelSelectionNavigator()
        WheelActions(
            onRotate = { delta ->
                when (step) {
                    MusicSetupStep.NAVIGATE -> model.turnWheel(delta)
                    MusicSetupStep.SELECT -> tutorialIndex =
                        navigator.next(latestTutorialIndex, delta, MusicSetupTutorial.folders.size)

                    MusicSetupStep.BROWSE -> tutorialIndex = navigator.next(
                        latestTutorialIndex,
                        delta,
                        MusicSetupTutorial.items(latestFolder).size
                    )

                    MusicSetupStep.CHOICE -> sourceIndex =
                        navigator.next(latestSourceIndex, delta, 2)

                    else -> if (latestActions.isNotEmpty()) actionIndex =
                        navigator.next(latestActionIndex, delta, latestActions.size)
                }
            },
            onSelect = {
                latestActions.getOrNull(latestActionIndex)?.takeIf { it.enabled }?.run?.invoke()
            },
            onMovementStarted = navigator::beginMovement,
        )
    }
    DisposableEffect(wheel, handler, overlay) {
        wheel?.register(handler, overlay)
        onDispose { wheel?.unregister(handler) }
    }
    Column(Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val section = when {
                navigation -> "NAVIGATION"
                field -> "AUDIOSTREAM"
                step == MusicSetupStep.CHOICE -> "MUSIC SOURCE"
                else -> "MUSIC SETUP"
            }
            Text(
                if (number > 0) "$number / 3 · $section" else section,
                modifier = Modifier.weight(1f), color = secondary, fontSize = 11.sp,
            )
            Text(
                "Skip",
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable(enabled = skip.enabled, onClick = skip.run)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = secondary,
                fontSize = 12.sp, lineHeight = 20.sp,
            )
        }
        if (number > 0) Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(foreground.copy(alpha = if (index < number) 0.8f else 0.15f))
                )
            }
        }
        if (step != MusicSetupStep.SUCCESS && !remoteLoading) {
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(description, fontSize = 13.sp, lineHeight = 19.sp, color = secondary)
            }
            Spacer(Modifier.height(12.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (centered) Modifier
                    else Modifier.verticalScroll(rememberScrollState())
                ),
            verticalArrangement = if (centered) Arrangement.Center else Arrangement.spacedBy(8.dp),
        ) {
            when (step) {
                MusicSetupStep.NAVIGATE -> {
                    Text(
                        "${(wheelFill * 100).toInt().coerceIn(0, 100)}%",
                        Modifier.align(Alignment.CenterHorizontally),
                        fontSize = 40.sp,
                        lineHeight = 48.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { wheelFill }, modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = foreground, trackColor = foreground.copy(alpha = 0.12f),
                    )
                }

                MusicSetupStep.SELECT -> MusicSetupTutorial.folders.forEachIndexed { index, label ->
                    SetupChoice(
                        label,
                        "Folder",
                        index == tutorialIndex,
                        folder = true
                    ) { model.openTutorialFolder(index) }
                }

                MusicSetupStep.BACK -> {
                    Column(Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(foreground.copy(alpha = 0.06f))
                                .clickable { model.back() }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("<", fontSize = 18.sp, color = foreground)
                            Text("Folders", Modifier.weight(1f), fontSize = 15.sp)
                            Text("Left / Menu", fontSize = 11.sp, color = secondary)
                        }
                        Row(
                            Modifier.padding(start = 24.dp, top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Canvas(Modifier.size(16.dp)) {
                                drawLine(
                                    secondary.copy(alpha = 0.5f),
                                    Offset(0f, 0f),
                                    Offset(0f, size.height / 2),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    secondary.copy(alpha = 0.5f),
                                    Offset(0f, size.height / 2),
                                    Offset(size.width / 2, size.height / 2),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            Text(
                                MusicSetupTutorial.folders[state.tutorialFolder],
                                color = secondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                MusicSetupStep.BROWSE -> {
                    Text(
                        "Folders / ${MusicSetupTutorial.folders[state.tutorialFolder]}",
                        color = secondary,
                        fontSize = 12.sp
                    )
                    MusicSetupTutorial.items(state.tutorialFolder).forEachIndexed { index, label ->
                        SetupChoice(label, null, index == tutorialIndex) {
                            model.selectTutorialItem(
                                index
                            )
                        }
                    }
                    state.tutorialMessage?.let {
                        Text(
                            it,
                            color = secondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                MusicSetupStep.SUCCESS -> {
                    Text(
                        title,
                        Modifier.align(Alignment.CenterHorizontally),
                        fontSize = 23.sp,
                        lineHeight = 29.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        MusicSetupTutorial.target,
                        Modifier.align(Alignment.CenterHorizontally),
                        color = secondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Press center to continue",
                        Modifier.align(Alignment.CenterHorizontally),
                        color = secondary,
                        fontSize = 12.sp
                    )
                }

                MusicSetupStep.CHOICE -> {
                    SetupChoice("On this device", "Device files", sourceIndex == 0) {
                        model.choose(
                            MusicSetupStep.DEVICE
                        )
                    }
                    SetupChoice("AudioStream", "Remote library", sourceIndex == 1) {
                        model.choose(
                            MusicSetupStep.ADDRESS
                        )
                    }
                }

                MusicSetupStep.ADDRESS, MusicSetupStep.USERNAME, MusicSetupStep.PASSWORD -> SetupField(
                    state,
                    model
                )

                MusicSetupStep.CONNECTING, MusicSetupStep.CONNECTED -> if (remoteLoading) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoadingState(Modifier
                            .size(48.dp)
                            .progressSemantics(), delayMillis = 0)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            title,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                MusicSetupStep.SCANNING -> if (local is LocalScanProgress.Scanning || local == LocalScanProgress.Idle)
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        color = foreground,
                        trackColor = foreground.copy(alpha = 0.12f)
                    )

                else -> Unit
            }
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.forEachIndexed { index, action ->
                if (!action.showButton) return@forEachIndexed
                val selected = index == actionIndex
                Text(
                    if (state.busy && step != MusicSetupStep.CONNECTING) "Saving…" else action.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .background(if (selected && action.enabled) foreground else background)
                        .border(1.dp, foreground.copy(alpha = if (selected) 0.7f else 0.2f))
                        .clickable(enabled = action.enabled, onClick = action.run)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (selected && action.enabled) background else if (action.enabled) foreground else secondary,
                    fontSize = 14.sp, lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun SetupChoice(
    title: String,
    detail: String?,
    selected: Boolean,
    folder: Boolean = false,
    onClick: () -> Unit
) {
    val foreground = MaterialTheme.colorScheme.onBackground
    val background = MaterialTheme.colorScheme.background
    val bringIntoView = remember { BringIntoViewRequester() }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selected, placed) { if (selected && placed) bringIntoView.bringIntoView() }
    val textColor = if (selected) background else foreground
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .onGloballyPositioned { placed = true }
            .background(if (selected) foreground else background)
            .border(1.dp, foreground.copy(alpha = if (selected) 0.8f else 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (folder) Canvas(Modifier.size(20.dp)) {
            val unit = size.width / 20f
            drawRect(
                textColor,
                Offset(2 * unit, 5 * unit),
                Size(16 * unit, 12 * unit),
                style = Stroke(2 * unit)
            )
            drawRect(textColor, Offset(2 * unit, 3 * unit), Size(7 * unit, 4 * unit))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = textColor, fontSize = 15.sp, lineHeight = 21.sp)
            if (detail != null) Text(
                detail,
                color = if (selected) background.copy(alpha = 0.8f) else MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        if (folder) Text(">", color = textColor, fontSize = 18.sp)
    }
}

@Composable
private fun SetupField(state: MusicSetupState, model: MusicSetupViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var visible by remember { mutableStateOf(false) }
    val password = state.step == MusicSetupStep.PASSWORD
    val value = when (state.step) {
        MusicSetupStep.ADDRESS -> state.address
        MusicSetupStep.USERNAME -> state.username
        else -> state.password
    }
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    OutlinedTextField(
        value = value,
        onValueChange = when (state.step) {
            MusicSetupStep.ADDRESS -> model::editAddress
            MusicSetupStep.USERNAME -> model::editUsername
            else -> model::editPassword
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
        shape = RectangleShape,
        placeholder = {
            Text(
                when (state.step) {
                    MusicSetupStep.ADDRESS -> "https://music.example.com"; MusicSetupStep.USERNAME -> "Username"; else -> "Password"
                }, fontSize = 13.sp
            )
        },
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (state.step) {
                MusicSetupStep.ADDRESS -> KeyboardType.Uri; MusicSetupStep.PASSWORD -> KeyboardType.Password; else -> KeyboardType.Text
            },
            imeAction = if (password) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { model.next() },
            onDone = { keyboard?.hide(); model.next() }),
        trailingIcon = if (password) {
            {
                TextButton(onClick = { visible = !visible }) {
                    Text(
                        if (visible) "Hide" else "Show",
                        fontSize = 12.sp
                    )
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
            unfocusedBorderColor = MaterialTheme.colorScheme.secondary
        ),
    )
}

private fun Context.setupActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.setupActivity()
    else -> null
}
