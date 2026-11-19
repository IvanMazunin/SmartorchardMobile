package com.smartorchard.mobile.state

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartorchard.mobile.data.CropDraft
import com.smartorchard.mobile.data.GardenDraft
import com.smartorchard.mobile.data.NavRoute
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.data.TreeStatus
import com.smartorchard.mobile.domain.plantingEstimate
import com.smartorchard.mobile.domain.toUserMessage
import com.smartorchard.mobile.domain.usecase.CreateGardenUseCase
import com.smartorchard.mobile.domain.usecase.SaveCropUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.sqrt

private val stateJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Immutable
data class GardenUiState(
    val rowPage: Int = 0,
    val colPage: Int = 0,
    val boardZones: Boolean = false,
    val filter: TreeStatus? = null,
    val selectedTreeId: String? = null,
    val gardenDraft: GardenDraft = GardenDraft(),
    val cropDraft: CropDraft = CropDraft(),
    val busy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GardenFeatureViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: OrchardRepository,
    private val stateProvider: ActiveGardenStateProvider,
    private val snackbarManager: SnackbarManager,
    private val saveCropUseCase: SaveCropUseCase,
    private val createGardenUseCase: CreateGardenUseCase,
    private val clock: Clock
) : ViewModel() {

    private val navChannel = Channel<NavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NavEvent> = navChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(restoreInitialState())
    val uiState: StateFlow<GardenUiState> = _uiState.asStateFlow()

    init {
        syncSavedState(prev = null, next = _uiState.value)
        viewModelScope.launch {
            repository.data.collect { data ->
                if (data.crops.isNotEmpty() && _uiState.value.gardenDraft.cropId.isEmpty()) {
                    val firstCropId = data.crops.first().id
                    updateState { current ->
                        if (current.gardenDraft.cropId.isEmpty()) {
                            current.copy(gardenDraft = current.gardenDraft.copy(cropId = firstCropId))
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    fun updatePage(row: Int, col: Int) {
        updateState { it.copy(rowPage = row, colPage = col) }
    }

    fun updateBoardZones(enabled: Boolean) {
        updateState { it.copy(boardZones = enabled) }
    }

    fun updateFilter(filter: TreeStatus?) {
        updateState { it.copy(filter = filter) }
    }

    fun selectTree(treeId: String?) {
        updateState { it.copy(selectedTreeId = treeId) }
    }

    fun openTreePassport(treeId: String) {
        updateState { it.copy(selectedTreeId = treeId, error = null) }
        navChannel.trySend(NavEvent.To(NavRoute.TreePassport(treeId)))
    }

    fun chooseGarden(gardenId: String) {
        if (_uiState.value.busy) return
        updateState { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.setCurrentGarden(gardenId)
                updateState {
                    it.copy(
                        rowPage = 0,
                        colPage = 0,
                        boardZones = false,
                        filter = null,
                        selectedTreeId = null,
                        busy = false,
                        error = null
                    )
                }
                navChannel.trySend(NavEvent.To(NavRoute.Board))
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

    fun beginGarden() {
        val cropId = stateProvider.appData.value.crops.firstOrNull()?.id.orEmpty()
        val draft = GardenDraft(
            cropId = cropId,
            date = LocalDate.now(clock).toString()
        )
        updateState { it.copy(gardenDraft = draft, error = null) }
        navChannel.trySend(NavEvent.To(NavRoute.NewGarden))
    }

    fun updateGardenDraft(draft: GardenDraft) {
        val chosen = stateProvider.appData.value.crops.find { it.id == draft.cropId }
        val estimate = chosen?.let { plantingEstimate(draft.areaHa, it) }
        val resolved = if (!draft.placesManual && estimate != null && estimate.count in 1..10000) {
            draft.copy(places = ceil(sqrt(estimate.count.toDouble())).toInt().toString())
        } else {
            draft
        }
        updateState { it.copy(gardenDraft = resolved, error = null) }
    }

    fun saveGarden() {
        if (_uiState.value.busy) return
        val draft = _uiState.value.gardenDraft
        val crops = stateProvider.appData.value.crops
        updateState { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val item = createGardenUseCase(draft, crops)
                updateState {
                    it.copy(
                        selectedTreeId = item.trees.firstOrNull()?.id,
                        rowPage = 0,
                        colPage = 0,
                        filter = null,
                        boardZones = false,
                        busy = false,
                        error = null
                    )
                }
                snackbarManager.show("Сад создан · ${item.trees.size} деревьев")
                navChannel.trySend(NavEvent.To(NavRoute.Board))
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

    fun beginCrop(fromGarden: Boolean) {
        updateState { it.copy(cropDraft = CropDraft(), error = null) }
        navChannel.trySend(NavEvent.To(NavRoute.NewCrop(fromGarden)))
    }

    fun updateCropDraft(draft: CropDraft) {
        updateState { it.copy(cropDraft = draft, error = null) }
    }

    fun cancelCrop() {
        updateState { it.copy(error = null) }
        navChannel.trySend(NavEvent.Back)
    }

    fun saveCrop(fromGarden: Boolean) {
        if (_uiState.value.busy) return
        val draft = _uiState.value.cropDraft
        val existingCrops = stateProvider.appData.value.crops
        updateState { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val item = saveCropUseCase(draft, existingCrops)
                updateState { current ->
                    current.copy(
                        gardenDraft = if (fromGarden) {
                            current.gardenDraft.copy(cropId = item.id)
                        } else {
                            current.gardenDraft
                        },
                        cropDraft = CropDraft(),
                        busy = false,
                        error = null
                    )
                }
                snackbarManager.show("Культура создана")
                navChannel.trySend(NavEvent.Back)
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

    fun clearError() {
        updateState { it.copy(error = null) }
    }

    private fun restoreInitialState(): GardenUiState {
        val restoredGardenDraft = savedStateHandle.get<String>(KEY_GARDEN_DRAFT)
            ?.let { raw -> runCatching { stateJson.decodeFromString<GardenDraft>(raw) }.getOrNull() }
        val initialCrops = repository.data.value.crops
        val baseGardenDraft = restoredGardenDraft ?: GardenDraft(date = LocalDate.now(clock).toString())
        val resolvedGardenDraft = if (baseGardenDraft.cropId.isEmpty() && initialCrops.isNotEmpty()) {
            baseGardenDraft.copy(cropId = initialCrops.first().id)
        } else {
            baseGardenDraft
        }

        val restoredCropDraft = savedStateHandle.get<String>(KEY_CROP_DRAFT)
            ?.let { raw -> runCatching { stateJson.decodeFromString<CropDraft>(raw) }.getOrNull() }
            ?: CropDraft()

        val restoredFilter = savedStateHandle.get<String>(KEY_FILTER)
            ?.let { raw -> TreeStatus.entries.find { it.name == raw } }

        return GardenUiState(
            rowPage = savedStateHandle.get<Int>(KEY_ROW_PAGE) ?: 0,
            colPage = savedStateHandle.get<Int>(KEY_COL_PAGE) ?: 0,
            boardZones = savedStateHandle.get<Boolean>(KEY_BOARD_ZONES) ?: false,
            filter = restoredFilter,
            selectedTreeId = savedStateHandle.get<String>(KEY_SELECTED_TREE_ID),
            gardenDraft = resolvedGardenDraft,
            cropDraft = restoredCropDraft
        )
    }

    private fun updateState(transform: (GardenUiState) -> GardenUiState) {
        var prevState: GardenUiState? = null
        var nextState: GardenUiState? = null
        _uiState.update { current ->
            val updated = transform(current)
            prevState = current
            nextState = updated
            updated
        }
        nextState?.let { syncSavedState(prevState, it) }
    }

    private fun syncSavedState(prev: GardenUiState?, next: GardenUiState) {
        if (prev == null || prev.rowPage != next.rowPage) {
            savedStateHandle[KEY_ROW_PAGE] = next.rowPage
        }
        if (prev == null || prev.colPage != next.colPage) {
            savedStateHandle[KEY_COL_PAGE] = next.colPage
        }
        if (prev == null || prev.boardZones != next.boardZones) {
            savedStateHandle[KEY_BOARD_ZONES] = next.boardZones
        }
        if (prev == null || prev.filter != next.filter) {
            savedStateHandle[KEY_FILTER] = next.filter?.name
        }
        if (prev == null || prev.selectedTreeId != next.selectedTreeId) {
            savedStateHandle[KEY_SELECTED_TREE_ID] = next.selectedTreeId
        }
        if (prev == null || prev.gardenDraft != next.gardenDraft) {
            savedStateHandle[KEY_GARDEN_DRAFT] = stateJson.encodeToString(next.gardenDraft)
        }
        if (prev == null || prev.cropDraft != next.cropDraft) {
            savedStateHandle[KEY_CROP_DRAFT] = stateJson.encodeToString(next.cropDraft)
        }
    }

    private companion object {
        const val KEY_ROW_PAGE = "garden_row_page"
        const val KEY_COL_PAGE = "garden_col_page"
        const val KEY_BOARD_ZONES = "garden_board_zones"
        const val KEY_FILTER = "garden_filter"
        const val KEY_SELECTED_TREE_ID = "garden_selected_tree_id"
        const val KEY_GARDEN_DRAFT = "garden_draft_json"
        const val KEY_CROP_DRAFT = "crop_draft_json"
    }
}
