package com.smartorchard.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.state.*
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.screens.*
import com.smartorchard.mobile.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.merge

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SmartOrchardTheme { OrchardApp() } }
    }
}

@Composable
fun OrchardApp(
    shellVm: ShellViewModel = hiltViewModel(),
    gardenVm: GardenFeatureViewModel = hiltViewModel(),
    treeVm: TreeInspectionViewModel = hiltViewModel(),
    irrigationVm: IrrigationViewModel = hiltViewModel(),
    expensesVm: ExpensesViewModel = hiltViewModel(),
    toolsVm: ToolsViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val data by shellVm.appData.collectAsStateWithLifecycle()
    val active by shellVm.activeContext.collectAsStateWithLifecycle()
    val gardenUi by gardenVm.uiState.collectAsStateWithLifecycle()
    val treeUi by treeVm.uiState.collectAsStateWithLifecycle()
    val irrigationUi by irrigationVm.uiState.collectAsStateWithLifecycle()
    val wateringZoneIds by irrigationVm.wateringZoneIds.collectAsStateWithLifecycle()
    val expensesUi by expensesVm.uiState.collectAsStateWithLifecycle()
    val printerStatus by toolsVm.printerStatus.collectAsStateWithLifecycle()
    val toolsUi by toolsVm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        merge(
            gardenVm.navEvents,
            treeVm.navEvents,
            irrigationVm.navEvents,
            expensesVm.navEvents,
            toolsVm.navEvents
        ).collect { event ->
            when (event) {
                is NavEvent.To -> navController.navigate(event.route) {
                    launchSingleTop = true
                }
                is NavEvent.Back -> navController.popBackStack()
            }
        }
    }

    LaunchedEffect(Unit) {
        shellVm.snackbarMessages.collect { msg ->
            snackbar.showSnackbar(msg)
        }
    }

    val destination = navController.currentBackStackEntryAsState().value?.destination
    val selected: NavRoute = when {
        destination == null -> NavRoute.Board
        destination.hasRoute<NavRoute.Irrigation>() ||
            destination.hasRoute<NavRoute.Zones>() ||
            destination.hasRoute<NavRoute.ZoneEditor>() ||
            destination.hasRoute<NavRoute.ZoneDetail>() ||
            destination.hasRoute<NavRoute.ScheduleForm>() -> NavRoute.Irrigation

        destination.hasRoute<NavRoute.Expenses>() ||
            destination.hasRoute<NavRoute.NewExpense>() ||
            destination.hasRoute<NavRoute.Economics>() -> NavRoute.Expenses

        destination.hasRoute<NavRoute.Scan>() -> NavRoute.Scan

        destination.hasRoute<NavRoute.More>() ||
            destination.hasRoute<NavRoute.Printer>() ||
            destination.hasRoute<NavRoute.Storage>() ||
            destination.hasRoute<NavRoute.Crops>() ||
            destination.hasRoute<NavRoute.NewCrop>() ||
            destination.hasRoute<NavRoute.Gardens>() ||
            destination.hasRoute<NavRoute.NewGarden>() -> NavRoute.More

        else -> NavRoute.Board
    }

    val tabs = listOf(
        Triple(NavRoute.Board, stringResource(R.string.tab_board), R.drawable.ic_trees),
        Triple(NavRoute.Irrigation, stringResource(R.string.tab_irrigation), R.drawable.ic_droplets),
        Triple(NavRoute.Expenses, stringResource(R.string.tab_expenses), R.drawable.ic_receipt),
        Triple(NavRoute.Scan, stringResource(R.string.tab_scan), R.drawable.ic_scan_line),
        Triple(NavRoute.More, stringResource(R.string.tab_more), R.drawable.ic_ellipsis)
    )

    Scaffold(
        containerColor = OrchardBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = OrchardBackground) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OrchardIcon(R.drawable.ic_sprout, tint = OrchardGreen, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    DemoBadge(stringResource(R.string.badge_demo_mode))
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = selected == route,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo<NavRoute.Board> { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { OrchardIcon(id = icon, modifier = Modifier.size(20.dp)) },
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
        if (active == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val current = active!!
            val selectedTree = current.garden.trees.find { it.id == gardenUi.selectedTreeId }
                ?: current.defaultTree

            fun resolveTreeContext(treeId: String): Triple<Garden, Crop, Tree> {
                val treeGarden = data.gardens.find { g -> g.trees.any { it.id == treeId } }
                    ?: current.garden
                val treeCrop = data.crops.find { it.id == treeGarden.cropId }
                    ?: current.crop
                val tree = treeGarden.trees.find { it.id == treeId }
                    ?: selectedTree
                return Triple(treeGarden, treeCrop, tree)
            }

            NavHost(
                navController = navController,
                startDestination = NavRoute.Board,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                composable<NavRoute.Board> {
                    ScrollableScreen {
                        BoardScreen(
                            garden = current.garden,
                            crop = current.crop,
                            tree = selectedTree,
                            zones = current.zones,
                            wateringZoneIds = wateringZoneIds,
                            rowPage = gardenUi.rowPage,
                            colPage = gardenUi.colPage,
                            boardZones = gardenUi.boardZones,
                            filter = gardenUi.filter,
                            onOpenGardens = { navController.navigate(NavRoute.Gardens) },
                            onOpenZones = { navController.navigate(NavRoute.Zones) },
                            onBeginZone = { irrigationVm.beginZone(null) },
                            onBoardZonesChange = gardenVm::updateBoardZones,
                            onFilterChange = gardenVm::updateFilter,
                            onPageChange = gardenVm::updatePage,
                            onSelectTree = gardenVm::selectTree,
                            onOpenZoneDetail = { zoneId ->
                                navController.navigate(NavRoute.ZoneDetail(zoneId))
                            },
                            onOpenTreePassport = gardenVm::openTreePassport
                        )
                    }
                }
                composable<NavRoute.Gardens> {
                    GardensScreen(
                        gardens = data.gardens,
                        crops = data.crops,
                        onNewGarden = gardenVm::beginGarden,
                        onChooseGarden = gardenVm::chooseGarden
                    )
                }
                composable<NavRoute.NewGarden> {
                    ScrollableScreen {
                        NewGardenScreen(
                            crops = data.crops,
                            draft = gardenUi.gardenDraft,
                            busy = gardenUi.busy,
                            error = gardenUi.error,
                            onBack = {
                                gardenVm.clearError()
                                navController.popBackStack()
                            },
                            onUpdateDraft = { draft ->
                                gardenVm.updateGardenDraft(draft)
                                gardenVm.clearError()
                            },
                            onCreateCrop = { gardenVm.beginCrop(fromGarden = true) },
                            onSaveGarden = gardenVm::saveGarden
                        )
                    }
                }
                composable<NavRoute.Crops> {
                    CropsScreen(
                        crops = data.crops,
                        onBack = { navController.popBackStack() },
                        onNewCrop = { gardenVm.beginCrop(fromGarden = false) }
                    )
                }
                composable<NavRoute.NewCrop> { backStackEntry ->
                    val route = backStackEntry.toRoute<NavRoute.NewCrop>()
                    ScrollableScreen {
                        NewCropScreen(
                            draft = gardenUi.cropDraft,
                            fromGarden = route.fromGarden,
                            busy = gardenUi.busy,
                            error = gardenUi.error,
                            onCancel = gardenVm::cancelCrop,
                            onUpdateDraft = { draft ->
                                gardenVm.updateCropDraft(draft)
                                gardenVm.clearError()
                            },
                            onSaveCrop = { gardenVm.saveCrop(route.fromGarden) }
                        )
                    }
                }
                composable<NavRoute.TreePassport> { backStackEntry ->
                    val route = backStackEntry.toRoute<NavRoute.TreePassport>()
                    val (treeGarden, treeCrop, tree) = resolveTreeContext(route.treeId)
                    val treeZone = data.zones
                        .filter { it.gardenId == treeGarden.id }
                        .find { tree.id in it.treeIds }
                    val records = remember(data.observations, tree.id) {
                        data.observations
                            .filter { it.treeId == tree.id }
                            .sortedByDescending { it.createdAt }
                    }
                    val treeNotes = remember(data.notes, tree.id) {
                        data.notes
                            .filter { it.treeId == tree.id }
                            .sortedByDescending { it.createdAt }
                    }
                    TreeScreen(
                        garden = treeGarden,
                        crop = treeCrop,
                        tree = tree,
                        zone = treeZone,
                        isWatering = treeZone != null && treeZone.id in wateringZoneIds,
                        records = records,
                        treeNotes = treeNotes,
                        busy = treeUi.busy,
                        error = treeUi.error,
                        onBack = {
                            treeVm.clearError()
                            navController.popBackStack()
                        },
                        onBeginInspection = { treeVm.beginInspection(tree) },
                        onOpenLabel = {
                            toolsVm.clearError()
                            navController.navigate(NavRoute.Label(tree.id))
                        },
                        onOpenObservationDetail = { obsId ->
                            navController.navigate(NavRoute.ObservationDetail(obsId))
                        },
                        onAddNote = { text -> treeVm.addNote(tree.id, text) }
                    )
                }
                composable<NavRoute.Observe> { backStackEntry ->
                    val route = backStackEntry.toRoute<NavRoute.Observe>()
                    val (_, treeCrop, tree) = resolveTreeContext(route.treeId)
                    ScrollableScreen {
                        ObservationFormScreen(
                            crop = treeCrop,
                            tree = tree,
                            draft = treeUi.inspection,
                            busy = treeUi.busy,
                            error = treeUi.error,
                            onBack = {
                                treeVm.clearError()
                                navController.popBackStack()
                            },
                            onUseSamplePhoto = treeVm::useSamplePhoto,
                            onProvideCameraUri = treeVm::prepareCameraUri,
                            onCameraCaptured = treeVm::onCameraCaptured,
                            onImportPhoto = treeVm::importPhoto,
                            onFail = treeVm::fail,
                            onUpdateDraft = { draft ->
                                treeVm.updateDraft(draft)
                                treeVm.clearError()
                            },
                            onAnalyze = treeVm::analyze,
                            onSaveWithoutAi = { treeVm.saveInspection(withAi = false) }
                        )
                    }
                }
                composable<NavRoute.AnalysisResult> { backStackEntry ->
                    val route = backStackEntry.toRoute<NavRoute.AnalysisResult>()
                    val (_, treeCrop, tree) = resolveTreeContext(route.treeId)
                    ScrollableScreen {
                        AnalysisScreen(
                            crop = treeCrop,
                            tree = tree,
                            draft = treeUi.inspection,
                            busy = treeUi.busy,
                            error = treeUi.error,
                            onBackToInspection = treeVm::backToInspection,
                            onUpdateDraft = { draft ->
                                treeVm.updateDraft(draft)
                                treeVm.clearError()
                            },
                            onSaveWithAi = { treeVm.saveInspection(withAi = true) }
                        )
                    }
                }
                composable<NavRoute.ObservationDetail> { backStackEntry ->
                    val route = backStackEntry.toRoute<NavRoute.ObservationDetail>()
                    val record = data.observations.find { it.id == route.observationId }
                    ScrollableScreen {
                        ObservationDetailScreen(
                            record = record,
                            onBackToTree = { treeId ->
                                if (!navController.popBackStack()) {
                                    gardenVm.openTreePassport(treeId)
                                }
                            }
                        )
                    }
                }
                composable<NavRoute.Irrigation> {
                    IrrigationScreen(
                        garden = current.garden,
                        zones = current.zones,
                        schedules = current.schedules,
                        previewAt = irrigationUi.previewAt,
                        busy = irrigationUi.busy,
                        error = irrigationUi.error,
                        onOpenZones = { navController.navigate(NavRoute.Zones) },
                        onNewSchedule = { irrigationVm.beginNewSchedule(null) },
                        onApplyPreviewTime = irrigationVm::applyPreviewTime,
                        onDeleteSchedule = irrigationVm::deleteSchedule
                    )
                }
                composable<NavRoute.Zones> {
                    ZonesScreen(
                        zones = current.zones,
                        wateringZoneIds = wateringZoneIds,
                        onBack = { navController.popBackStack() },
                        onCreateZone = { irrigationVm.beginZone(null) },
                        onOpenZoneDetail = { zoneId ->
                            navController.navigate(NavRoute.ZoneDetail(zoneId))
                        }
                    )
                }
                composable<NavRoute.ZoneEditor> {
                    ScrollableScreen {
                        ZoneEditorScreen(
                            garden = current.garden,
                            selectedTreeId = selectedTree.id,
                            zones = current.zones,
                            wateringZoneIds = wateringZoneIds,
                            zoneDraft = irrigationUi.zoneDraft,
                            busy = irrigationUi.busy,
                            error = irrigationUi.error,
                            onBack = {
                                irrigationVm.clearError()
                                navController.popBackStack()
                            },
                            onUpdateDraft = { draft ->
                                irrigationVm.updateZoneDraft(draft)
                                irrigationVm.clearError()
                            },
                            onToggleZoneCell = irrigationVm::toggleZoneCell,
                            onSaveZone = irrigationVm::saveZone
                        )
                    }
                }
                composable<NavRoute.ZoneDetail> { backStackEntry ->
                    val route = backStackEntry.toRoute<NavRoute.ZoneDetail>()
                    val zone = current.zones.find { it.id == route.zoneId }
                    ScrollableScreen {
                        ZoneDetailScreen(
                            garden = current.garden,
                            zone = zone,
                            zones = current.zones,
                            wateringZoneIds = wateringZoneIds,
                            onBack = { navController.popBackStack() },
                            onEditZone = { id -> irrigationVm.beginZone(id) },
                            onAddSchedule = { irrigationVm.beginNewSchedule(route.zoneId) }
                        )
                    }
                }
                composable<NavRoute.ScheduleForm> { backStackEntry ->
                    val route = backStackEntry.toRoute<NavRoute.ScheduleForm>()
                    val initialZone = current.zones.find { it.id == route.initialZoneId }
                    ScrollableScreen {
                        ScheduleFormScreen(
                            zones = current.zones,
                            initialZone = initialZone,
                            busy = irrigationUi.busy,
                            error = irrigationUi.error,
                            onBack = {
                                irrigationVm.clearError()
                                navController.popBackStack()
                            },
                            onClearError = irrigationVm::clearError,
                            onSaveSchedule = irrigationVm::saveSchedule
                        )
                    }
                }
                composable<NavRoute.Expenses> {
                    ExpensesScreen(
                        garden = current.garden,
                        expenses = current.expenses,
                        onAddExpense = {
                            expensesVm.clearError()
                            navController.navigate(NavRoute.NewExpense)
                        },
                        onOpenEconomics = {
                            expensesVm.clearError()
                            navController.navigate(NavRoute.Economics)
                        }
                    )
                }
                composable<NavRoute.NewExpense> {
                    ScrollableScreen {
                        ExpenseFormScreen(
                            garden = current.garden,
                            defaultDate = expensesUi.defaultDate,
                            busy = expensesUi.busy,
                            error = expensesUi.error,
                            onBack = {
                                expensesVm.clearError()
                                navController.popBackStack()
                            },
                            onSaveExpense = expensesVm::saveExpense
                        )
                    }
                }
                composable<NavRoute.Economics> {
                    ScrollableScreen {
                        EconomicsScreen(
                            garden = current.garden,
                            expenses = current.expenses,
                            busy = expensesUi.busy,
                            error = expensesUi.error,
                            onBack = {
                                expensesVm.clearError()
                                navController.popBackStack()
                            },
                            onSaveFinance = expensesVm::saveFinance
                        )
                    }
                }
                composable<NavRoute.Scan> {
                    ScrollableScreen {
                        ScanScreen(
                            garden = current.garden,
                            tree = selectedTree,
                            error = toolsUi.error,
                            onLookup = toolsVm::lookup,
                            onOpenQr = toolsVm::openQr
                        )
                    }
                }
                composable<NavRoute.Label> { backStackEntry ->
                    val route = backStackEntry.toRoute<NavRoute.Label>()
                    val (treeGarden, treeCrop, tree) = resolveTreeContext(route.treeId)
                    if (tree.id != route.treeId) {
                        Text("Дерево не найдено")
                        return@composable
                    }
                    ScrollableScreen {
                        LabelScreen(
                            garden = treeGarden,
                            crop = treeCrop,
                            tree = tree,
                            printerDevice = printerStatus.connected?.name,
                            copies = toolsUi.copies,
                            error = toolsUi.error ?: printerStatus.error,
                            onBack = {
                                toolsVm.clearError()
                                navController.popBackStack()
                            },
                            onCopiesChange = toolsVm::updateCopies,
                            onOpenPrinter = {
                                toolsVm.clearError()
                                navController.navigate(NavRoute.Printer(returnToLabel = true))
                            },
                            busy = toolsUi.busy,
                            onPrint = toolsVm::printLabel,
                            onFail = toolsVm::fail
                        )
                    }
                }
                composable<NavRoute.Printer> { backStackEntry ->
                    val route = backStackEntry.toRoute<NavRoute.Printer>()
                    ScrollableScreen {
                        PrinterScreen(
                            status = printerStatus,
                            returnToLabel = route.returnToLabel,
                            devices = toolsUi.pairedDevices,
                            printerSearched = toolsUi.printerSearched,
                            busy = toolsUi.busy,
                            error = toolsUi.error,
                            onBack = {
                                toolsVm.clearError()
                                navController.popBackStack()
                            },
                            onFail = toolsVm::fail,
                            onSearchPrinters = toolsVm::searchPrinters,
                            onConnectPrinter = toolsVm::connectPrinter,
                            onDisconnectPrinter = toolsVm::disconnectPrinter
                        )
                    }
                }
                composable<NavRoute.More> {
                    ScrollableScreen {
                        MoreScreen(
                            offlineDemo = toolsUi.offlineDemo,
                            onNavigateSection = { target ->
                                toolsVm.clearError()
                                gardenVm.clearError()
                                navController.navigate(target)
                            },
                            onOfflineDemoChange = toolsVm::setOfflineDemo
                        )
                    }
                }
                composable<NavRoute.Storage> {
                    ScrollableScreen {
                        StorageScreen(
                            data = data,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
