package com.smartorchard.mobile.state

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartorchard.mobile.data.DemoPrinter
import com.smartorchard.mobile.data.NavRoute
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.domain.toUserMessage
import com.smartorchard.mobile.domain.usecase.FindTreeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class ToolsUiState(
    val offlineDemo: Boolean = false,
    val copies: String = "1",
    val printerSearching: Boolean = false,
    val printerSearched: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: OrchardRepository,
    private val stateProvider: ActiveGardenStateProvider,
    private val offlineMode: OfflineModeManager,
    private val snackbarManager: SnackbarManager,
    private val findTreeUseCase: FindTreeUseCase
) : ViewModel() {

    private val navChannel = Channel<NavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NavEvent> = navChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(
        ToolsUiState(
            offlineDemo = offlineMode.offlineDemo.value,
            copies = savedStateHandle.get<String>(KEY_COPIES) ?: "1"
        )
    )
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle[KEY_COPIES] = _uiState.value.copies
        viewModelScope.launch {
            offlineMode.offlineDemo.collect { enabled ->
                _uiState.update { it.copy(offlineDemo = enabled) }
            }
        }
    }

    fun setOfflineDemo(enabled: Boolean) {
        offlineMode.setOfflineDemo(enabled)
        _uiState.update { it.copy(offlineDemo = enabled) }
    }

    fun lookup(row: String, place: String) {
        val ctx = stateProvider.currentContext() ?: run {
            fail("Сад недоступен.")
            return
        }
        try {
            val found = findTreeUseCase.byCoordinates(ctx.garden, row, place)
            clearError()
            navChannel.trySend(NavEvent.To(NavRoute.TreePassport(found.id)))
        } catch (e: Exception) {
            fail(e.toUserMessage())
        }
    }

    fun openQr(payload: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val id = findTreeUseCase.openByQr(stateProvider.appData.value.gardens, payload)
                _uiState.update { it.copy(busy = false, error = null) }
                navChannel.trySend(NavEvent.To(NavRoute.TreePassport(id)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.toUserMessage()) }
            } finally {
                if (_uiState.value.busy) {
                    _uiState.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun updateCopies(copies: String) {
        savedStateHandle[KEY_COPIES] = copies
        _uiState.update { it.copy(copies = copies, error = null) }
    }

    fun searchPrinters() {
        if (_uiState.value.printerSearching) return
        _uiState.update { it.copy(printerSearching = true, error = null) }
        viewModelScope.launch {
            try {
                delay(700.milliseconds)
                _uiState.update { it.copy(printerSearching = false, printerSearched = true) }
                snackbarManager.show("Найдены демонстрационные устройства")
            } catch (e: CancellationException) {
                _uiState.update { it.copy(printerSearching = false) }
                throw e
            }
        }
    }

    fun connectPrinter(name: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.updatePrinter(DemoPrinter(name))
                _uiState.update { it.copy(busy = false, error = null) }
                snackbarManager.show("Подключен демопринтер")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.toUserMessage()) }
            } finally {
                if (_uiState.value.busy) {
                    _uiState.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun disconnectPrinter() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.updatePrinter(DemoPrinter())
                _uiState.update { it.copy(busy = false, error = null) }
                snackbarManager.show("Демопринтер отключен")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.toUserMessage()) }
            } finally {
                if (_uiState.value.busy) {
                    _uiState.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun demoPrint() {
        val count = _uiState.value.copies.trim().toIntOrNull()
        if (count == null || count !in 1..12) {
            fail("Укажите от 1 до 12 копий.")
            return
        }
        clearError()
        if (stateProvider.appData.value.printer.device == null) {
            navChannel.trySend(NavEvent.To(NavRoute.Printer(returnToLabel = true)))
        } else {
            snackbarManager.show("Демо: отправлено $count этикеток. Физическая печать не выполнялась.")
        }
    }

    fun fail(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private companion object {
        const val KEY_COPIES = "tools_copies"
    }
}
