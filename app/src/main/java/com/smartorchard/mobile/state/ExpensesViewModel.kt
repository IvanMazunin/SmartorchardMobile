package com.smartorchard.mobile.state

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartorchard.mobile.domain.DEMO_DATE
import com.smartorchard.mobile.domain.toUserMessage
import com.smartorchard.mobile.domain.usecase.SaveExpenseUseCase
import com.smartorchard.mobile.domain.usecase.UpdateFinanceUseCase
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
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class ExpensesUiState(
    val defaultDate: String = DEMO_DATE,
    val busy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val stateProvider: ActiveGardenStateProvider,
    private val snackbarManager: SnackbarManager,
    private val saveExpenseUseCase: SaveExpenseUseCase,
    private val updateFinanceUseCase: UpdateFinanceUseCase,
    clock: Clock
) : ViewModel() {

    private val navChannel = Channel<NavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NavEvent> = navChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(
        ExpensesUiState(defaultDate = LocalDate.now(clock).toString())
    )
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    fun saveExpense(
        title: String,
        category: String,
        quantity: String,
        unit: String,
        price: String,
        date: String
    ) {
        if (_uiState.value.busy) return
        val ctx = stateProvider.currentContext() ?: run {
            _uiState.update { it.copy(error = "Сад недоступен.") }
            return
        }
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                saveExpenseUseCase(
                    gardenId = ctx.garden.id,
                    title = title,
                    category = category,
                    quantity = quantity,
                    unit = unit,
                    price = price,
                    date = date
                )
                _uiState.update { it.copy(busy = false, error = null) }
                snackbarManager.show("Затрата сохранена")
                navChannel.trySend(NavEvent.Back)
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

    fun saveFinance(revenue: String, annualRevenue: String, annualCosts: String) {
        if (_uiState.value.busy) return
        val ctx = stateProvider.currentContext() ?: run {
            _uiState.update { it.copy(error = "Сад недоступен.") }
            return
        }
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                updateFinanceUseCase(
                    gardenId = ctx.garden.id,
                    revenue = revenue,
                    annualRevenue = annualRevenue,
                    annualCosts = annualCosts
                )
                _uiState.update { it.copy(busy = false, error = null) }
                snackbarManager.show("Сценарный расчет обновлен")
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
