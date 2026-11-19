package com.smartorchard.mobile.state

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartorchard.mobile.data.NavRoute
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.data.ZoneDraft
import com.smartorchard.mobile.domain.DEMO_TIME
import com.smartorchard.mobile.domain.toUserMessage
import com.smartorchard.mobile.domain.usecase.SaveScheduleUseCase
import com.smartorchard.mobile.domain.usecase.SaveZoneUseCase
import com.smartorchard.mobile.domain.validDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

private val stateJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Immutable
data class IrrigationUiState(
    val previewAt: String = DEMO_TIME,
    val zoneDraft: ZoneDraft = ZoneDraft(),
    val busy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class IrrigationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: OrchardRepository,
    private val stateProvider: ActiveGardenStateProvider,
    private val snackbarManager: SnackbarManager,
    private val saveZoneUseCase: SaveZoneUseCase,
    private val saveScheduleUseCase: SaveScheduleUseCase
) : ViewModel() {

    private val navChannel = Channel<NavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NavEvent> = navChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(restoreInitialState())
    val uiState: StateFlow<IrrigationUiState> = _uiState.asStateFlow()

    val wateringZoneIds: StateFlow<Set<String>> = combine(
        stateProvider.activeContext,
        _uiState.map { it.previewAt }.distinctUntilChanged()
    ) { context, previewAt ->
        computeWateringZoneIds(context, previewAt)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = computeWateringZoneIds(stateProvider.currentContext(), _uiState.value.previewAt)
    )

    init {
        syncSavedState(prev = null, next = _uiState.value)
    }

    fun applyPreviewTime(time: String) {
        val trimmed = time.trim()
        if (!validDateTime(trimmed)) {
            updateState { it.copy(error = "Укажите корректные дату и время в формате ГГГГ-ММ-ДДTЧЧ:ММ.") }
            return
        }
        updateState { it.copy(previewAt = trimmed, error = null) }
    }

    fun beginZone(zoneId: String? = null) {
        val zones = stateProvider.currentContext()?.zones.orEmpty()
        val existing = zoneId?.let { id -> zones.find { it.id == id } }
        val draft = existing?.let {
            ZoneDraft(
                id = it.id,
                name = it.name,
                color = it.color,
                treeIds = it.treeIds
            )
        } ?: ZoneDraft(color = zones.size % 3)
        updateState { it.copy(zoneDraft = draft, error = null) }
        navChannel.trySend(NavEvent.To(NavRoute.ZoneEditor(zoneId)))
    }

    fun beginNewSchedule(initialZoneId: String? = null) {
        val zones = stateProvider.currentContext()?.zones.orEmpty()
        if (zones.isEmpty()) {
            beginZone(null)
            snackbarManager.show("Сначала создайте зону из клеток")
        } else {
            updateState { it.copy(error = null) }
            navChannel.trySend(NavEvent.To(NavRoute.ScheduleForm(initialZoneId)))
        }
    }

    fun toggleZoneCell(treeId: String) {
        val zones = stateProvider.currentContext()?.zones.orEmpty()
        val draft = _uiState.value.zoneDraft
        val owner = zones.find { treeId in it.treeIds }
        if (owner != null && owner.id != draft.id) return
        val updatedTreeIds = if (treeId in draft.treeIds) {
            draft.treeIds - treeId
        } else {
            draft.treeIds + treeId
        }
        updateState {
            it.copy(
                zoneDraft = draft.copy(treeIds = updatedTreeIds),
                error = null
            )
        }
    }

    fun updateZoneDraft(draft: ZoneDraft) {
        updateState { it.copy(zoneDraft = draft, error = null) }
    }

    fun saveZone() {
        if (_uiState.value.busy) return
        val ctx = stateProvider.currentContext() ?: run {
            updateState { it.copy(error = "Сад недоступен.") }
            return
        }
        val draft = _uiState.value.zoneDraft
        updateState { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val item = saveZoneUseCase(draft, ctx.garden, ctx.zones)
                updateState { it.copy(busy = false, error = null) }
                snackbarManager.show("Зона сохранена")
                navChannel.trySend(NavEvent.To(NavRoute.ZoneDetail(item.id)))
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

    fun saveSchedule(zoneId: String, start: String, end: String) {
        if (_uiState.value.busy) return
        val ctx = stateProvider.currentContext() ?: run {
            updateState { it.copy(error = "Сад недоступен.") }
            return
        }
        updateState { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                saveScheduleUseCase(
                    gardenId = ctx.garden.id,
                    zoneId = zoneId,
                    start = start,
                    end = end,
                    gardenZones = ctx.zones,
                    gardenSchedules = ctx.schedules
                )
                updateState { it.copy(busy = false, error = null) }
                snackbarManager.show("Интервал добавлен")
                navChannel.trySend(NavEvent.To(NavRoute.Irrigation))
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

    fun deleteSchedule(scheduleId: String) {
        if (_uiState.value.busy) return
        updateState { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.deleteSchedule(scheduleId)
                updateState { it.copy(busy = false, error = null) }
                snackbarManager.show("Интервал удален")
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

    private fun computeWateringZoneIds(
        context: ActiveGardenContext?,
        previewAt: String
    ): Set<String> {
        val schedules = context?.schedules ?: return emptySet()
        return schedules
            .filter { previewAt >= it.start && previewAt < it.end }
            .mapTo(mutableSetOf()) { it.zoneId }
    }

    private fun restoreInitialState(): IrrigationUiState {
        val restoredPreview = savedStateHandle.get<String>(KEY_PREVIEW_AT) ?: DEMO_TIME
        val restoredDraft = savedStateHandle.get<String>(KEY_ZONE_DRAFT)
            ?.let { raw -> runCatching { stateJson.decodeFromString<ZoneDraft>(raw) }.getOrNull() }
            ?: ZoneDraft()
        return IrrigationUiState(
            previewAt = restoredPreview,
            zoneDraft = restoredDraft
        )
    }

    private fun updateState(transform: (IrrigationUiState) -> IrrigationUiState) {
        var prevState: IrrigationUiState? = null
        var nextState: IrrigationUiState? = null
        _uiState.update { current ->
            val updated = transform(current)
            prevState = current
            nextState = updated
            updated
        }
        nextState?.let { syncSavedState(prevState, it) }
    }

    private fun syncSavedState(prev: IrrigationUiState?, next: IrrigationUiState) {
        if (prev == null || prev.previewAt != next.previewAt) {
            savedStateHandle[KEY_PREVIEW_AT] = next.previewAt
        }
        if (prev == null || prev.zoneDraft != next.zoneDraft) {
            savedStateHandle[KEY_ZONE_DRAFT] = stateJson.encodeToString(next.zoneDraft)
        }
    }

    private companion object {
        const val KEY_PREVIEW_AT = "irrigation_preview_at"
        const val KEY_ZONE_DRAFT = "irrigation_zone_draft_json"
    }
}
