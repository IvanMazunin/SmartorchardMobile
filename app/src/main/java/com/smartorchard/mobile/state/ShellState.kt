package com.smartorchard.mobile.state

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.smartorchard.mobile.data.AppData
import com.smartorchard.mobile.data.Crop
import com.smartorchard.mobile.data.Expense
import com.smartorchard.mobile.data.Garden
import com.smartorchard.mobile.data.IrrigationSchedule
import com.smartorchard.mobile.data.IrrigationZone
import com.smartorchard.mobile.data.NavRoute
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.data.Tree
import com.smartorchard.mobile.di.ApplicationScope
import com.smartorchard.mobile.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
import javax.inject.Inject
import javax.inject.Singleton

sealed interface NavEvent {
    data class To(val route: NavRoute) : NavEvent
    data object Back : NavEvent
}

@Immutable
data class ActiveGardenContext(
    val garden: Garden,
    val crop: Crop,
    val defaultTree: Tree,
    val zones: List<IrrigationZone>,
    val schedules: List<IrrigationSchedule>,
    val expenses: List<Expense>
)

@Singleton
class SnackbarManager @Inject constructor() {
    private val channel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = channel.receiveAsFlow()

    fun show(message: String) {
        channel.trySend(message)
    }
}

@Singleton
class OfflineModeManager @Inject constructor(
    networkMonitor: NetworkMonitor,
    @ApplicationScope scope: CoroutineScope
) {
    private val _offlineDemo = MutableStateFlow(false)
    val offlineDemo: StateFlow<Boolean> = _offlineDemo.asStateFlow()
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val isEffectivelyOffline: StateFlow<Boolean> = combine(isOnline, _offlineDemo) { online, demo ->
        !online || demo
    }.stateIn(scope, SharingStarted.Eagerly, !networkMonitor.isOnline.value)

    fun setOfflineDemo(enabled: Boolean) {
        _offlineDemo.value = enabled
    }
}

@Singleton
class ActiveGardenStateProvider @Inject constructor(
    private val repository: OrchardRepository,
    @ApplicationScope scope: CoroutineScope
) {
    val appData: StateFlow<AppData> = repository.data

    val activeContext: StateFlow<ActiveGardenContext?> = repository.data
        .map { buildContext(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, buildContext(repository.data.value))

    fun currentContext(): ActiveGardenContext? = buildContext(repository.data.value)

    private fun buildContext(data: AppData): ActiveGardenContext? {
        if (data.gardens.isEmpty() || data.crops.isEmpty()) return null
        val garden = data.gardens.find { it.id == data.currentGardenId } ?: data.gardens.first()
        val crop = data.crops.find { it.id == garden.cropId } ?: data.crops.first()
        val defaultTree = garden.trees.find { it.row == 3 && it.place == 5 }
            ?: garden.trees.firstOrNull()
            ?: return null
        val zones = data.zones.filter { it.gardenId == garden.id }
        val schedules = data.schedules.filter { it.gardenId == garden.id }.sortedBy { it.start }
        val expenses = data.expenses.filter { it.gardenId == garden.id }
        return ActiveGardenContext(garden, crop, defaultTree, zones, schedules, expenses)
    }
}

@HiltViewModel
class ShellViewModel @Inject constructor(
    stateProvider: ActiveGardenStateProvider,
    snackbarManager: SnackbarManager
) : ViewModel() {
    val appData: StateFlow<AppData> = stateProvider.appData
    val activeContext: StateFlow<ActiveGardenContext?> = stateProvider.activeContext
    val snackbarMessages: Flow<String> = snackbarManager.messages
}
