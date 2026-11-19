package com.smartorchard.mobile.state

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartorchard.mobile.data.AnalysisPhase
import com.smartorchard.mobile.data.InspectionDraft
import com.smartorchard.mobile.data.NavRoute
import com.smartorchard.mobile.data.Tree
import com.smartorchard.mobile.domain.AppError
import com.smartorchard.mobile.domain.toUserMessage
import com.smartorchard.mobile.domain.uid
import com.smartorchard.mobile.domain.usecase.AddTreeNoteUseCase
import com.smartorchard.mobile.domain.usecase.AnalyzePhotoUseCase
import com.smartorchard.mobile.domain.usecase.SaveObservationUseCase
import com.smartorchard.mobile.services.PhotoStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

private val stateJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Immutable
data class TreeInspectionUiState(
    val inspection: InspectionDraft? = null,
    val pendingCameraUri: String? = null,
    val busy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TreeInspectionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val stateProvider: ActiveGardenStateProvider,
    private val offlineMode: OfflineModeManager,
    private val photos: PhotoStore,
    private val snackbarManager: SnackbarManager,
    private val addTreeNoteUseCase: AddTreeNoteUseCase,
    private val saveObservationUseCase: SaveObservationUseCase,
    private val analyzePhotoUseCase: AnalyzePhotoUseCase
) : ViewModel() {

    private val navChannel = Channel<NavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NavEvent> = navChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(restoreInitialState())
    val uiState: StateFlow<TreeInspectionUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    fun beginInspection(tree: Tree) {
        analysisJob?.cancel()
        val draft = InspectionDraft(
            id = uid(),
            treeId = tree.id,
            previousStatus = tree.status,
            status = tree.status
        )
        updateState {
            it.copy(
                inspection = draft,
                pendingCameraUri = null,
                error = null
            )
        }
        navChannel.trySend(NavEvent.To(NavRoute.Observe(tree.id)))
    }

    fun updateDraft(draft: InspectionDraft) {
        updateState { it.copy(inspection = draft, error = null) }
    }

    fun useSamplePhoto() {
        updateState { current ->
            current.copy(
                inspection = current.inspection?.copy(samplePhoto = true, photoPath = null),
                error = null
            )
        }
    }

    fun prepareCameraUri(): Uri {
        val uri = photos.cameraUri()
        updateState { it.copy(pendingCameraUri = uri.toString(), error = null) }
        return uri
    }

    fun onCameraCaptured(success: Boolean) {
        val rawUri = _uiState.value.pendingCameraUri
        updateState { it.copy(pendingCameraUri = null) }
        if (success) {
            rawUri?.let(Uri::parse)?.let(::importPhoto)
        }
    }

    fun importPhoto(uri: Uri) {
        val id = _uiState.value.inspection?.id ?: return
        viewModelScope.launch {
            try {
                val path = photos.import(uri)
                if (_uiState.value.inspection?.id == id) {
                    updateState { current ->
                        current.copy(
                            inspection = current.inspection?.copy(photoPath = path, samplePhoto = false),
                            error = null
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.toUserMessage("Не удалось загрузить фото."))
            }
        }
    }

    fun analyze() {
        val d = _uiState.value.inspection ?: return
        if (d.photoPath == null && !d.samplePhoto) {
            fail("Добавьте фотографию перед анализом.")
            return
        }
        if (offlineMode.isEffectivelyOffline.value) {
            val msg = if (offlineMode.offlineDemo.value) {
                "Для анализа требуется подключение. В макете включен режим без сети."
            } else {
                AppError.NetworkOffline().userMessage
            }
            fail(msg)
            return
        }

        val processingDraft = d.copy(phase = AnalysisPhase.PROCESSING)
        updateState { it.copy(inspection = processingDraft, error = null) }
        navChannel.trySend(NavEvent.To(NavRoute.AnalysisResult(d.treeId)))

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            try {
                val result = analyzePhotoUseCase(processingDraft, offlineMode.isEffectivelyOffline.value)
                val current = _uiState.value.inspection
                if (current?.id == d.id && current.phase == AnalysisPhase.PROCESSING) {
                    if (offlineMode.isEffectivelyOffline.value) {
                        updateState {
                            it.copy(
                                inspection = current.copy(phase = AnalysisPhase.DRAFT),
                                error = "Анализ прерван: нет сети."
                            )
                        }
                        navChannel.trySend(NavEvent.Back)
                    } else {
                        updateState {
                            it.copy(
                                inspection = current.copy(
                                    phase = AnalysisPhase.RESULT,
                                    diagnosis = result.diagnosis,
                                    recommendations = result.recommendations,
                                    originalAi = result
                                ),
                                error = null
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val current = _uiState.value.inspection
                if (current?.id == d.id) {
                    updateState {
                        it.copy(
                            inspection = current.copy(phase = AnalysisPhase.DRAFT),
                            error = e.toUserMessage("Ошибка анализа.")
                        )
                    }
                    navChannel.trySend(NavEvent.Back)
                }
            }
        }
    }

    fun backToInspection() {
        analysisJob?.cancel()
        updateState { current ->
            current.copy(
                inspection = current.inspection?.copy(phase = AnalysisPhase.DRAFT),
                error = null
            )
        }
        navChannel.trySend(NavEvent.Back)
    }

    fun saveInspection(withAi: Boolean) {
        if (_uiState.value.busy) return
        val draft = _uiState.value.inspection ?: return
        val garden = stateProvider.currentContext()?.garden ?: run {
            fail("Сад недоступен.")
            return
        }
        updateState { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                saveObservationUseCase(draft, garden, withAi)
                updateState {
                    it.copy(
                        inspection = null,
                        pendingCameraUri = null,
                        busy = false,
                        error = null
                    )
                }
                snackbarManager.show("Осмотр сохранен для дерева")
                navChannel.trySend(NavEvent.To(NavRoute.TreePassport(draft.treeId)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateState { it.copy(busy = false, error = e.toUserMessage()) }
            } finally {
                if (_uiState.value.busy) {
                    updateState { it.copy(busy = false) }
                }
            }
        }
    }

    fun addNote(treeId: String, text: String) {
        if (_uiState.value.busy) return
        updateState { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                addTreeNoteUseCase(treeId, text)
                updateState { it.copy(busy = false, error = null) }
                snackbarManager.show("Заметка добавлена")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateState { it.copy(busy = false, error = e.toUserMessage()) }
            } finally {
                if (_uiState.value.busy) {
                    updateState { it.copy(busy = false) }
                }
            }
        }
    }

    fun fail(message: String) {
        updateState { it.copy(error = message) }
    }

    fun clearError() {
        updateState { it.copy(error = null) }
    }

    private fun restoreInitialState(): TreeInspectionUiState {
        val restoredInspection = savedStateHandle.get<String>(KEY_INSPECTION)
            ?.let { raw -> runCatching { stateJson.decodeFromString<InspectionDraft>(raw) }.getOrNull() }
        val restoredCameraUri = savedStateHandle.get<String>(KEY_PENDING_CAMERA_URI)
        return TreeInspectionUiState(
            inspection = restoredInspection,
            pendingCameraUri = restoredCameraUri
        )
    }

    private fun updateState(transform: (TreeInspectionUiState) -> TreeInspectionUiState) {
        var prevState: TreeInspectionUiState? = null
        var nextState: TreeInspectionUiState? = null
        _uiState.update { current ->
            val updated = transform(current)
            prevState = current
            nextState = updated
            updated
        }
        nextState?.let { syncSavedState(prevState, it) }
    }

    private fun syncSavedState(prev: TreeInspectionUiState?, next: TreeInspectionUiState) {
        if (prev == null || prev.inspection != next.inspection) {
            if (next.inspection == null) {
                savedStateHandle.remove<String>(KEY_INSPECTION)
            } else {
                savedStateHandle[KEY_INSPECTION] = stateJson.encodeToString(next.inspection)
            }
        }
        if (prev == null || prev.pendingCameraUri != next.pendingCameraUri) {
            if (next.pendingCameraUri == null) {
                savedStateHandle.remove<String>(KEY_PENDING_CAMERA_URI)
            } else {
                savedStateHandle[KEY_PENDING_CAMERA_URI] = next.pendingCameraUri
            }
        }
    }

    private companion object {
        const val KEY_INSPECTION = "tree_inspection_draft_json"
        const val KEY_PENDING_CAMERA_URI = "tree_pending_camera_uri"
    }
}
