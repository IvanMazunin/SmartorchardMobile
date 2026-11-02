package com.smartorchard.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartorchard.mobile.data.Route
import com.smartorchard.mobile.state.NavigationEvent
import com.smartorchard.mobile.state.OrchardViewModel
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.screens.*
import com.smartorchard.mobile.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SmartOrchardTheme { OrchardApp() } }
    }
}

@Composable fun OrchardApp(vm: OrchardViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val data by vm.appData.collectAsStateWithLifecycle()
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Observe navigation events
    LaunchedEffect(Unit) {
        vm.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateTo -> navController.navigate(event.route) {
                    launchSingleTop = true
                }
                is NavigationEvent.NavigateBack -> navController.popBackStack()
            }
        }
    }

    // Show snackbar messages
    LaunchedEffect(ui.message) {
        ui.message?.let { msg ->
            vm.clearMessage()
            snackbar.showSnackbar(msg)
        }
    }

    // Determine selected tab from current route
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val selected = when (currentRoute) {
        Route.IRRIGATION, Route.ZONES, Route.ZONE_EDITOR, Route.ZONE_DETAIL, Route.SCHEDULE -> Route.IRRIGATION
        Route.EXPENSES, Route.NEW_EXPENSE, Route.ECONOMICS -> Route.EXPENSES
        Route.SCAN -> Route.SCAN
        Route.MORE, Route.PRINTER, Route.STORAGE, Route.CROPS, Route.NEW_CROP, Route.GARDENS, Route.NEW_GARDEN -> Route.MORE
        else -> Route.BOARD
    }

    val tabs = listOf(
        Triple(Route.BOARD, "Сад", R.drawable.ic_trees),
        Triple(Route.IRRIGATION, "Полив", R.drawable.ic_droplets),
        Triple(Route.EXPENSES, "Затраты", R.drawable.ic_receipt),
        Triple(Route.SCAN, "QR", R.drawable.ic_scan_line),
        Triple(Route.MORE, "Ещё", R.drawable.ic_ellipsis)
    )

    Scaffold(
        containerColor = OrchardBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = OrchardBackground) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrchardIcon(R.drawable.ic_sprout, tint = OrchardGreen)
                    Text("SmartOrchard", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    DemoBadge("МОКИ")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected == route,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(Route.BOARD) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { OrchardIcon(icon) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OrchardGreen,
                            selectedTextColor = OrchardGreen,
                            indicatorColor = GoodBackground
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.BOARD,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(Route.BOARD) {
                ScrollableScreen { BoardScreen(vm, data, ui, navController) }
            }
            composable(Route.GARDENS) {
                ScrollableScreen { GardensScreen(vm, data, navController) }
            }
            composable(Route.NEW_GARDEN) {
                ScrollableScreen { NewGardenScreen(vm, data, ui, navController) }
            }
            composable(Route.CROPS) {
                ScrollableScreen { CropsScreen(vm, data, navController) }
            }
            composable(Route.NEW_CROP) {
                ScrollableScreen { NewCropScreen(vm, ui, navController) }
            }
            composable(Route.TREE) {
                ScrollableScreen { TreeScreen(vm, data, ui, navController) }
            }
            composable(Route.OBSERVE) {
                ScrollableScreen { ObservationFormScreen(vm, ui, navController) }
            }
            composable(Route.RESULT) {
                ScrollableScreen { AnalysisScreen(vm, ui, navController) }
            }
            composable(Route.OBSERVATION_DETAIL) {
                ScrollableScreen { ObservationDetailScreen(vm, data, ui, navController) }
            }
            composable(Route.IRRIGATION) {
                ScrollableScreen { IrrigationScreen(vm, data, ui, navController) }
            }
            composable(Route.ZONES) {
                ScrollableScreen { ZonesScreen(vm, ui, navController) }
            }
            composable(Route.ZONE_EDITOR) {
                ScrollableScreen { ZoneEditorScreen(vm, data, ui, navController) }
            }
            composable(Route.ZONE_DETAIL) {
                ScrollableScreen { ZoneDetailScreen(vm, data, ui, navController) }
            }
            composable(Route.SCHEDULE) {
                ScrollableScreen { ScheduleFormScreen(vm, ui, navController) }
            }
            composable(Route.EXPENSES) {
                ScrollableScreen { ExpensesScreen(vm, data, ui, navController) }
            }
            composable(Route.NEW_EXPENSE) {
                ScrollableScreen { ExpenseFormScreen(vm, ui, navController) }
            }
            composable(Route.ECONOMICS) {
                ScrollableScreen { EconomicsScreen(vm, data, ui, navController) }
            }
            composable(Route.SCAN) {
                ScrollableScreen { ScanScreen(vm, ui, navController) }
            }
            composable(Route.LABEL) {
                ScrollableScreen { LabelScreen(vm, data, ui, navController) }
            }
            composable(Route.PRINTER) {
                ScrollableScreen { PrinterScreen(vm, data, ui, navController) }
            }
            composable(Route.MORE) {
                ScrollableScreen { MoreScreen(vm, data, ui, navController) }
            }
            composable(Route.STORAGE) {
                ScrollableScreen { StorageScreen(vm, data, ui, navController) }
            }
        }
    }
}

@Composable
fun ScrollableScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        content()
        Spacer(Modifier.height(12.dp))
    }
}
