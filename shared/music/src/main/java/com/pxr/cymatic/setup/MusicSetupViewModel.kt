package com.pxr.cymatic.setup

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.LocalLibraryScanner
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.sync.AudioStreamConnection
import com.pxr.cymatic.sync.LibraryCatalogManager
import com.pxr.cymatic.sync.connectionError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MusicSetupStep { LOADING, NAVIGATE, SELECT, BACK, BROWSE, SUCCESS, CHOICE, DEVICE, SCANNING, ADDRESS, USERNAME, PASSWORD, CONNECTING, CONNECTED, COMPLETE }

data class MusicSetupState(
    val step: MusicSetupStep = MusicSetupStep.LOADING,
    val address: String = "",
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val storageError: Boolean = false,
    val openLibrary: Boolean = false,
    val navigationProgress: Int = 0,
    val tutorialFolder: Int = 0,
    val backPracticed: Boolean = false,
    val tutorialMessage: String? = null,
)

class MusicSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val mutableState = MutableStateFlow(MusicSetupState())
    val state = mutableState.asStateFlow()
    private var draftJob: Job? = null
    private var operation: Job? = null
    private var connection: AudioStreamConnection? = null

    init {
        load()
    }

    private fun load() {
        operation = viewModelScope.launch {
            try {
                val draft = MusicSetupStore.load(context)
                val step = if (draft.complete) MusicSetupStep.COMPLETE else when (draft.step) {
                    "navigate" -> MusicSetupStep.NAVIGATE
                    "select" -> MusicSetupStep.SELECT
                    "back" -> MusicSetupStep.BACK
                    "browse" -> MusicSetupStep.BROWSE
                    "success" -> MusicSetupStep.SUCCESS
                    "device" -> MusicSetupStep.DEVICE
                    "scanning" -> MusicSetupStep.SCANNING
                    "remote", "address" -> MusicSetupStep.ADDRESS
                    "username" -> MusicSetupStep.USERNAME
                    "password", "connecting" -> MusicSetupStep.PASSWORD
                    "connected" -> MusicSetupStep.CONNECTED
                    else -> MusicSetupStep.CHOICE
                }
                mutableState.value = MusicSetupState(
                    step,
                    draft.address,
                    draft.username,
                    navigationProgress = draft.navigationProgress,
                    tutorialFolder = draft.tutorialFolder,
                    backPracticed = draft.backPracticed
                )
                if (step == MusicSetupStep.CONNECTED) {
                    val source = com.pxr.cymatic.sync.RemoteLibraries.currentSource()
                    val hasPassword =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.pxr.cymatic.sync.SyncCredentialStore.hasPassword(context, source.id)
                        }
                    if (hasPassword) LibraryCatalogManager.refresh(context)
                    else mutableState.update {
                        it.copy(
                            step = MusicSetupStep.PASSWORD,
                            error = "Enter your password to reconnect."
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("MusicSetup", "Could not load setup", error)
                mutableState.value = MusicSetupState(
                    MusicSetupStep.CHOICE,
                    error = "Could not load setup settings. You can continue without saving.",
                    storageError = true
                )
            }
        }
    }

    fun choose(step: MusicSetupStep) {
        if (state.value.busy) return
        mutableState.update {
            it.copy(step = step, error = null, tutorialMessage = null)
        }
        val snapshot = state.value
        viewModelScope.launch {
            try {
                MusicSetupStore.saveStep(
                    step.name.lowercase(), snapshot.navigationProgress,
                    snapshot.tutorialFolder, snapshot.backPracticed
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        error = "Could not save setup progress.",
                        storageError = true
                    )
                }
            }
        }
    }

    fun back() {
        if (state.value.busy) {
            if (connection != null) cancelConnection()
            return
        }
        val previous = when (state.value.step) {
            MusicSetupStep.BACK -> {
                mutableState.update { it.copy(backPracticed = true) }
                MusicSetupStep.SELECT
            }

            MusicSetupStep.BROWSE -> MusicSetupStep.SELECT
            MusicSetupStep.DEVICE, MusicSetupStep.ADDRESS -> MusicSetupStep.CHOICE
            MusicSetupStep.SCANNING -> MusicSetupStep.CHOICE
            MusicSetupStep.USERNAME -> MusicSetupStep.ADDRESS
            MusicSetupStep.PASSWORD -> MusicSetupStep.USERNAME
            MusicSetupStep.CONNECTED -> MusicSetupStep.ADDRESS
            else -> return
        }
        choose(previous)
    }

    fun turnWheel(steps: Int) {
        if (steps == 0 || state.value.step != MusicSetupStep.NAVIGATE) return
        mutableState.update {
            it.copy(
                navigationProgress = (it.navigationProgress.toLong() + steps.toLong() * 4L)
                    .coerceIn(0L, 100L).toInt()
            )
        }
    }

    fun openTutorialFolder(index: Int) {
        if (state.value.step != MusicSetupStep.SELECT || index !in MusicSetupTutorial.folders.indices) return
        mutableState.update { it.copy(tutorialFolder = index) }
        choose(if (state.value.backPracticed) MusicSetupStep.BROWSE else MusicSetupStep.BACK)
    }

    fun selectTutorialItem(index: Int) {
        if (state.value.step != MusicSetupStep.BROWSE) return
        val item = MusicSetupTutorial.items(state.value.tutorialFolder).getOrNull(index) ?: return
        if (item == MusicSetupTutorial.target) choose(MusicSetupStep.SUCCESS)
        else mutableState.update { it.copy(tutorialMessage = "Keep looking.") }
    }

    fun next() {
        when (state.value.step) {
            MusicSetupStep.NAVIGATE -> if (state.value.navigationProgress >= 100) choose(
                MusicSetupStep.SELECT
            )

            MusicSetupStep.SUCCESS -> choose(MusicSetupStep.CHOICE)
            MusicSetupStep.ADDRESS -> {
                val address =
                    runCatching { com.pxr.cymatic.sync.AudioStreamClient.normalizeBaseUrl(state.value.address) }.getOrNull()
                if (address == null) mutableState.update { it.copy(error = "Enter a server address starting with http:// or https://.") }
                else {
                    editAddress(address)
                    choose(MusicSetupStep.USERNAME)
                }
            }

            MusicSetupStep.USERNAME -> if (state.value.username.isBlank())
                mutableState.update { it.copy(error = "Enter your username.") }
            else {
                editUsername(state.value.username.trim())
                choose(MusicSetupStep.PASSWORD)
            }

            MusicSetupStep.PASSWORD -> if (state.value.password.isEmpty())
                mutableState.update { it.copy(error = "Enter your password.") }
            else connect()

            else -> Unit
        }
    }

    fun editAddress(value: String) {
        mutableState.update { it.copy(address = value, error = null) }
        saveDraft()
    }

    fun editUsername(value: String) {
        mutableState.update { it.copy(username = value, error = null) }
        saveDraft()
    }

    fun editPassword(value: String) {
        mutableState.update { it.copy(password = value, error = null) }
    }

    private fun saveDraft() {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(300)
            try {
                MusicSetupStore.saveDraft(state.value.address, state.value.username)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        error = "Could not save connection details.",
                        storageError = true
                    )
                }
            }
        }
    }

    fun connect() {
        if (state.value.busy) return
        val details = state.value
        val attempt =
            AudioStreamConnection(context, details.address, details.username, details.password)
        connection = attempt
        draftJob?.cancel()
        mutableState.update { it.copy(step = MusicSetupStep.CONNECTING, busy = true, error = null) }
        operation = viewModelScope.launch {
            try {
                MusicSetupStore.saveDraft(details.address, details.username)
                MusicSetupStore.saveStep("password")
                attempt.save()
                MusicSetupStore.saveStep("connected")
                mutableState.update {
                    it.copy(
                        step = MusicSetupStep.CONNECTED,
                        password = "",
                        busy = false
                    )
                }
                LibraryCatalogManager.refresh(context)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (connection === attempt)
                    mutableState.update {
                        it.copy(
                            step = MusicSetupStep.PASSWORD,
                            error = connectionError(error),
                            busy = false
                        )
                    }
            } finally {
                if (connection === attempt) {
                    connection = null
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun cancelConnection() {
        val current = connection ?: return
        connection = null
        current.cancel()
        operation?.cancel()
        mutableState.update { it.copy(step = MusicSetupStep.PASSWORD, busy = false, error = null) }
    }

    fun scan() {
        if (!com.pxr.cymatic.ui.components.common.hasStoragePermission(context)) {
            choose(MusicSetupStep.DEVICE)
            return
        }
        choose(MusicSetupStep.SCANNING)
        LocalLibraryScanner.scan(context)
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                SettingsStore.setScanDirectories(
                    SettingsStore.getScanDirectories().toSet() + uri.toString()
                )
                SettingsStore.setScanAllMedia(false)
                scan()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(error = "Could not open this folder. Choose another folder.") }
            }
        }
    }

    fun finish(openLibrary: Boolean) {
        if (state.value.busy) return
        draftJob?.cancel()
        mutableState.update { it.copy(busy = true, error = null) }
        operation = viewModelScope.launch {
            try {
                MusicSetupStore.finish()
                mutableState.update {
                    it.copy(
                        step = MusicSetupStep.COMPLETE,
                        busy = false,
                        password = "",
                        openLibrary = openLibrary
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        busy = false,
                        error = "Could not save setup settings. Try again or continue without saving.",
                        storageError = true
                    )
                }
            }
        }
    }

    fun continueWithoutSaving() {
        mutableState.update { it.copy(step = MusicSetupStep.COMPLETE, busy = false, password = "") }
    }

    fun libraryOpened() {
        mutableState.update { it.copy(openLibrary = false) }
    }

    fun retryCatalog() {
        LibraryCatalogManager.refresh(context)
    }

    override fun onCleared() {
        connection?.cancel()
        super.onCleared()
    }
}
