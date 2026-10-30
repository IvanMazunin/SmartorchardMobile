package com.smartorchard.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartorchard.mobile.data.Screen
import com.smartorchard.mobile.state.OrchardViewModel
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.screens.*
import com.smartorchard.mobile.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SmartOrchardTheme { OrchardApp() } }
    }
}

@Composable fun OrchardApp(vm: OrchardViewModel = viewModel()) {
    val data by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { val message = it; vm.message = null; snackbar.showSnackbar(message) } }
    fun back() {
        when (vm.screen) {
            Screen.RESULT -> vm.backToInspection()
            Screen.OBSERVE, Screen.OBSERVATION_DETAIL, Screen.LABEL -> vm.go(Screen.TREE)
            Screen.NEW_CROP -> vm.cancelCrop()
            Screen.NEW_GARDEN -> vm.go(Screen.GARDENS)
            Screen.ZONE_EDITOR, Screen.ZONE_DETAIL -> vm.go(Screen.ZONES)
            Screen.ZONES, Screen.SCHEDULE -> vm.go(Screen.IRRIGATION)
            Screen.NEW_EXPENSE, Screen.ECONOMICS -> vm.go(Screen.EXPENSES)
            Screen.PRINTER -> vm.go(if (vm.printerReturnToLabel) Screen.LABEL else Screen.MORE)
            Screen.CROPS, Screen.STORAGE, Screen.GARDENS -> vm.go(Screen.MORE)
            else -> vm.go(Screen.BOARD)
        }
    }
    BackHandler(vm.screen != Screen.BOARD) { back() }
    val tabs = listOf(Triple(Screen.BOARD, "Сад", R.drawable.ic_trees), Triple(Screen.IRRIGATION, "Полив", R.drawable.ic_droplets),
        Triple(Screen.EXPENSES, "Затраты", R.drawable.ic_receipt), Triple(Screen.SCAN, "QR", R.drawable.ic_scan_line), Triple(Screen.MORE, "Ещё", R.drawable.ic_ellipsis))
    val selected = when (vm.screen) {
        Screen.IRRIGATION, Screen.ZONES, Screen.ZONE_EDITOR, Screen.ZONE_DETAIL, Screen.SCHEDULE -> Screen.IRRIGATION
        Screen.EXPENSES, Screen.NEW_EXPENSE, Screen.ECONOMICS -> Screen.EXPENSES
        Screen.SCAN -> Screen.SCAN
        Screen.MORE, Screen.PRINTER, Screen.STORAGE, Screen.CROPS, Screen.NEW_CROP, Screen.GARDENS, Screen.NEW_GARDEN -> Screen.MORE
        else -> Screen.BOARD
    }
    Scaffold(containerColor = OrchardBackground, snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = OrchardBackground) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrchardIcon(R.drawable.ic_sprout, tint = OrchardGreen); Text("SmartOrchard", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                DemoBadge("МОКИ")
            } }
        }, bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White, tonalElevation = 0.dp) {
                tabs.forEach { (screen, label, icon) -> NavigationBarItem(selected == screen, { vm.go(screen) }, { OrchardIcon(icon) }, label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = OrchardGreen, selectedTextColor = OrchardGreen, indicatorColor = GoodBackground)) }
            }
        }) { padding ->
        key(vm.screen, data.currentGardenId) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (vm.screen) {
                    Screen.BOARD -> BoardScreen(vm, data)
                    Screen.GARDENS -> GardensScreen(vm, data)
                    Screen.NEW_GARDEN -> NewGardenScreen(vm, data)
                    Screen.CROPS -> CropsScreen(vm, data)
                    Screen.NEW_CROP -> NewCropScreen(vm)
                    Screen.TREE -> TreeScreen(vm, data)
                    Screen.OBSERVE -> ObservationFormScreen(vm)
                    Screen.RESULT -> AnalysisScreen(vm)
                    Screen.OBSERVATION_DETAIL -> ObservationDetailScreen(vm, data)
                    Screen.IRRIGATION -> IrrigationScreen(vm, data)
                    Screen.ZONES -> ZonesScreen(vm)
                    Screen.ZONE_EDITOR -> ZoneEditorScreen(vm, data)
                    Screen.ZONE_DETAIL -> ZoneDetailScreen(vm, data)
                    Screen.SCHEDULE -> ScheduleFormScreen(vm)
                    Screen.EXPENSES -> ExpensesScreen(vm, data)
                    Screen.NEW_EXPENSE -> ExpenseFormScreen(vm)
                    Screen.ECONOMICS -> EconomicsScreen(vm, data)
                    Screen.SCAN -> ScanScreen(vm)
                    Screen.LABEL -> LabelScreen(vm, data)
                    Screen.PRINTER -> PrinterScreen(vm, data)
                    Screen.MORE -> MoreScreen(vm, data)
                    Screen.STORAGE -> StorageScreen(vm, data)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
