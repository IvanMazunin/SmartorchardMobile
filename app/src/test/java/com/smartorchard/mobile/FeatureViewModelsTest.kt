package com.smartorchard.mobile

import androidx.lifecycle.SavedStateHandle
import com.smartorchard.mobile.data.AiResult
import com.smartorchard.mobile.data.AnalysisPhase
import com.smartorchard.mobile.data.AppData
import com.smartorchard.mobile.data.Crop
import com.smartorchard.mobile.data.CropDraft
import com.smartorchard.mobile.data.DemoPrinter
import com.smartorchard.mobile.data.Expense
import com.smartorchard.mobile.data.Finance
import com.smartorchard.mobile.data.Garden
import com.smartorchard.mobile.data.GardenDraft
import com.smartorchard.mobile.data.IrrigationSchedule
import com.smartorchard.mobile.data.IrrigationZone
import com.smartorchard.mobile.data.NavRoute
import com.smartorchard.mobile.data.Observation
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.data.SeedData
import com.smartorchard.mobile.data.TreeNote
import com.smartorchard.mobile.data.TreeStatus
import com.smartorchard.mobile.domain.Categories
import com.smartorchard.mobile.domain.DEMO_DATE
import com.smartorchard.mobile.domain.qrPayload
import com.smartorchard.mobile.domain.usecase.AddTreeNoteUseCase
import com.smartorchard.mobile.domain.usecase.AnalyzePhotoUseCase
import com.smartorchard.mobile.domain.usecase.CreateGardenUseCase
import com.smartorchard.mobile.domain.usecase.FindTreeUseCase
import com.smartorchard.mobile.domain.usecase.SaveCropUseCase
import com.smartorchard.mobile.domain.usecase.SaveExpenseUseCase
import com.smartorchard.mobile.domain.usecase.SaveObservationUseCase
import com.smartorchard.mobile.domain.usecase.SaveScheduleUseCase
import com.smartorchard.mobile.domain.usecase.SaveZoneUseCase
import com.smartorchard.mobile.domain.usecase.UpdateFinanceUseCase
import com.smartorchard.mobile.network.NetworkMonitor
import com.smartorchard.mobile.services.DiagnosisService
import com.smartorchard.mobile.services.PairedPrinter
import com.smartorchard.mobile.services.PlantPrinter
import com.smartorchard.mobile.services.PrinterStatus
import com.smartorchard.mobile.services.PlantLabel
import com.smartorchard.mobile.services.PhotoStore
import com.smartorchard.mobile.state.ActiveGardenStateProvider
import com.smartorchard.mobile.state.ExpensesViewModel
import com.smartorchard.mobile.state.GardenFeatureViewModel
import com.smartorchard.mobile.state.IrrigationViewModel
import com.smartorchard.mobile.state.NavEvent
import com.smartorchard.mobile.state.OfflineModeManager
import com.smartorchard.mobile.state.SnackbarManager
import com.smartorchard.mobile.state.ToolsViewModel
import com.smartorchard.mobile.state.TreeInspectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-10-29T09:41:00Z"),
        ZoneOffset.UTC
    )

    private class FakeOrchardRepository(
        initial: AppData = SeedData.create()
    ) : OrchardRepository {
        private val mutable = MutableStateFlow(initial)
        override val data: StateFlow<AppData> = mutable.asStateFlow()

        override suspend fun setCurrentGarden(gardenId: String) {
            mutable.update { it.copy(currentGardenId = gardenId) }
        }

        override suspend fun addCrop(crop: Crop) {
            mutable.update { it.copy(crops = it.crops + crop) }
        }

        override suspend fun addGarden(garden: Garden) {
            mutable.update { it.copy(gardens = it.gardens + garden, currentGardenId = garden.id) }
        }

        override suspend fun saveZone(zone: IrrigationZone) {
            mutable.update { it.copy(zones = it.zones.filterNot { z -> z.id == zone.id } + zone) }
        }

        override suspend fun addSchedule(schedule: IrrigationSchedule) {
            mutable.update { it.copy(schedules = it.schedules + schedule) }
        }

        override suspend fun deleteSchedule(scheduleId: String) {
            mutable.update { it.copy(schedules = it.schedules.filterNot { s -> s.id == scheduleId }) }
        }

        override suspend fun addExpense(expense: Expense) {
            mutable.update { it.copy(expenses = it.expenses + expense) }
        }

        override suspend fun updateGardenFinance(gardenId: String, finance: Finance) {
            mutable.update { state ->
                state.copy(
                    gardens = state.gardens.map { g ->
                        if (g.id == gardenId) g.copy(finance = finance) else g
                    }
                )
            }
        }

        override suspend fun addNote(note: TreeNote) {
            mutable.update { it.copy(notes = it.notes + note) }
        }

        override suspend fun addObservation(
            observation: Observation,
            gardenId: String,
            treeId: String,
            newStatus: TreeStatus
        ) {
            mutable.update { current ->
                current.copy(
                    observations = current.observations + observation,
                    gardens = current.gardens.map { g ->
                        if (g.id == gardenId) {
                            g.copy(
                                trees = g.trees.map { t ->
                                    if (t.id == treeId) t.copy(status = newStatus) else t
                                }
                            )
                        } else {
                            g
                        }
                    }
                )
            }
        }

        override suspend fun updatePrinter(printer: DemoPrinter) {
            mutable.update { it.copy(printer = printer) }
        }
    }

    private class FakeNetworkMonitor(initialOnline: Boolean = true) : NetworkMonitor {
        val online = MutableStateFlow(initialOnline)
        override val isOnline: StateFlow<Boolean> = online.asStateFlow()
    }

    private class FakeDiagnosisService(
        private val delayMs: Long = 0L,
        private val result: AiResult = AiResult(
            diagnosis = "Пятнистость листьев",
            recommendations = "Осмотрите соседние деревья и проконсультируйтесь с агрономом."
        )
    ) : DiagnosisService {
        override suspend fun analyze(photoPath: String?, sample: Boolean): AiResult {
            if (delayMs > 0L) delay(delayMs.milliseconds)
            return result
        }
    }

    private fun dummyPhotoStore(): PhotoStore {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(theUnsafe, PhotoStore::class.java) as PhotoStore
    }

    // --- 1. GardenFeatureViewModel ---

    @Test
    fun gardenFeatureViewModel_autoCalculatesPlacesAndRespectsManualOverride() = runTest {
        val repo = FakeOrchardRepository()
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val snackbar = SnackbarManager()
        val vm = GardenFeatureViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            stateProvider = provider,
            snackbarManager = snackbar,
            saveCropUseCase = SaveCropUseCase(repo),
            createGardenUseCase = CreateGardenUseCase(repo, fixedClock),
            clock = fixedClock
        )

        // Almond crop: 6m x 4m = 24 m² per tree; 0.24 ha = 2400 m² -> 100 trees -> ceil(sqrt(100)) = 10
        val almond = repo.data.value.crops.first()
        vm.updateGardenDraft(
            vm.uiState.value.gardenDraft.copy(
                name = "Новый миндальный блок",
                cropId = almond.id,
                areaHa = "0.24",
                placesManual = false
            )
        )
        assertEquals("10", vm.uiState.value.gardenDraft.places)

        // Manual override keeps user-provided places
        vm.updateGardenDraft(
            vm.uiState.value.gardenDraft.copy(
                areaHa = "0.24",
                places = "20",
                placesManual = true
            )
        )
        assertEquals("20", vm.uiState.value.gardenDraft.places)
    }

    @Test
    fun gardenFeatureViewModel_saveCropFromGardenUpdatesGardenDraftCropIdAndEmitsBack() = runTest {
        val repo = FakeOrchardRepository()
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val snackbar = SnackbarManager()
        val vm = GardenFeatureViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            stateProvider = provider,
            snackbarManager = snackbar,
            saveCropUseCase = SaveCropUseCase(repo),
            createGardenUseCase = CreateGardenUseCase(repo, fixedClock),
            clock = fixedClock
        )

        vm.beginGarden()
        assertEquals(NavEvent.To(NavRoute.NewGarden), vm.navEvents.first())
        val initialCropId = vm.uiState.value.gardenDraft.cropId

        vm.beginCrop(fromGarden = true)
        assertEquals(NavEvent.To(NavRoute.NewCrop(fromGarden = true)), vm.navEvents.first())

        vm.updateCropDraft(
            CropDraft(
                name = "Черешня",
                variety = "Кордия",
                row = "5",
                tree = "3"
            )
        )
        vm.saveCrop(fromGarden = true)
        advanceUntilIdle()

        val createdCrop = repo.data.value.crops.last()
        assertNotEquals(initialCropId, createdCrop.id)
        assertEquals(createdCrop.id, vm.uiState.value.gardenDraft.cropId)
        assertEquals("Культура создана", snackbar.messages.first())
        assertEquals(NavEvent.Back, vm.navEvents.first())
    }

    @Test
    fun gardenFeatureViewModel_restoresGardenDraftAndFilterFromSavedStateHandleAfterProcessDeath() = runTest {
        val repo = FakeOrchardRepository()
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val snackbar = SnackbarManager()
        val savedStateHandle = SavedStateHandle()

        val vmBeforeKill = GardenFeatureViewModel(
            savedStateHandle = savedStateHandle,
            repository = repo,
            stateProvider = provider,
            snackbarManager = snackbar,
            saveCropUseCase = SaveCropUseCase(repo),
            createGardenUseCase = CreateGardenUseCase(repo, fixedClock),
            clock = fixedClock
        )

        val appleCrop = repo.data.value.crops[1]
        vmBeforeKill.updateFilter(TreeStatus.WARNING)
        vmBeforeKill.updatePage(row = 2, col = 1)
        vmBeforeKill.updateBoardZones(true)
        vmBeforeKill.updateGardenDraft(
            GardenDraft(
                name = "Восточный склон",
                cropId = appleCrop.id,
                areaHa = "0.08",
                date = DEMO_DATE,
                placesManual = false
            )
        )
        val expectedDraft = vmBeforeKill.uiState.value.gardenDraft

        // Simulate Android process death & recreation with the same SavedStateHandle
        val vmAfterRecreate = GardenFeatureViewModel(
            savedStateHandle = savedStateHandle,
            repository = repo,
            stateProvider = provider,
            snackbarManager = snackbar,
            saveCropUseCase = SaveCropUseCase(repo),
            createGardenUseCase = CreateGardenUseCase(repo, fixedClock),
            clock = fixedClock
        )

        assertEquals(TreeStatus.WARNING, vmAfterRecreate.uiState.value.filter)
        assertEquals(2, vmAfterRecreate.uiState.value.rowPage)
        assertEquals(1, vmAfterRecreate.uiState.value.colPage)
        assertTrue(vmAfterRecreate.uiState.value.boardZones)
        assertEquals(expectedDraft, vmAfterRecreate.uiState.value.gardenDraft)
    }

    // --- 2. TreeInspectionViewModel ---

    @Test
    fun treeInspectionViewModel_beginInspectionNavigatesAndSurvivesProcessDeath() = runTest {
        val repo = FakeOrchardRepository()
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val network = FakeNetworkMonitor(initialOnline = true)
        val offlineMode = OfflineModeManager(network, backgroundScope)
        val snackbar = SnackbarManager()
        val savedStateHandle = SavedStateHandle()
        val photos = dummyPhotoStore()

        val vmBeforeKill = TreeInspectionViewModel(
            savedStateHandle = savedStateHandle,
            stateProvider = provider,
            offlineMode = offlineMode,
            photos = photos,
            snackbarManager = snackbar,
            addTreeNoteUseCase = AddTreeNoteUseCase(repo, fixedClock),
            saveObservationUseCase = SaveObservationUseCase(repo, fixedClock),
            analyzePhotoUseCase = AnalyzePhotoUseCase(FakeDiagnosisService())
        )

        val targetTree = provider.currentContext()!!.defaultTree
        vmBeforeKill.beginInspection(targetTree)
        assertEquals(NavEvent.To(NavRoute.Observe(targetTree.id)), vmBeforeKill.navEvents.first())

        val initialDraft = vmBeforeKill.uiState.value.inspection
        assertNotNull(initialDraft)
        assertEquals(targetTree.id, initialDraft!!.treeId)

        vmBeforeKill.updateDraft(
            initialDraft.copy(
                status = TreeStatus.WARNING,
                comment = "Замечены пятна после дождя"
            )
        )
        vmBeforeKill.useSamplePhoto()
        val expectedInspection = vmBeforeKill.uiState.value.inspection
        assertTrue(expectedInspection!!.samplePhoto)

        // Recreate ViewModel with same SavedStateHandle (simulating Camera process death)
        val vmAfterRecreate = TreeInspectionViewModel(
            savedStateHandle = savedStateHandle,
            stateProvider = provider,
            offlineMode = offlineMode,
            photos = photos,
            snackbarManager = snackbar,
            addTreeNoteUseCase = AddTreeNoteUseCase(repo, fixedClock),
            saveObservationUseCase = SaveObservationUseCase(repo, fixedClock),
            analyzePhotoUseCase = AnalyzePhotoUseCase(FakeDiagnosisService())
        )

        assertEquals(expectedInspection, vmAfterRecreate.uiState.value.inspection)
    }

    @Test
    fun treeInspectionViewModel_analyzeSucceedsOnlineAndAbortsWhenOfflineDemoEnabled() {
        val standardDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(standardDispatcher)
        runTest(standardDispatcher) {
            val repo = FakeOrchardRepository()
            val provider = ActiveGardenStateProvider(repo, backgroundScope)
            val network = FakeNetworkMonitor(initialOnline = true)
            val offlineMode = OfflineModeManager(network, backgroundScope)
            val snackbar = SnackbarManager()
            val photos = dummyPhotoStore()

            val vm = TreeInspectionViewModel(
                savedStateHandle = SavedStateHandle(),
                stateProvider = provider,
                offlineMode = offlineMode,
                photos = photos,
                snackbarManager = snackbar,
                addTreeNoteUseCase = AddTreeNoteUseCase(repo, fixedClock),
                saveObservationUseCase = SaveObservationUseCase(repo, fixedClock),
                analyzePhotoUseCase = AnalyzePhotoUseCase(FakeDiagnosisService(delayMs = 500L))
            )
            runCurrent()

            val tree = provider.currentContext()!!.defaultTree
            vm.beginInspection(tree)
            assertEquals(NavEvent.To(NavRoute.Observe(tree.id)), vm.navEvents.first())
            vm.useSamplePhoto()

            // 1. Offline demo blocks analysis before starting
            offlineMode.setOfflineDemo(true)
            runCurrent()
            vm.analyze()
            assertEquals(AnalysisPhase.DRAFT, vm.uiState.value.inspection?.phase)
            assertTrue(vm.uiState.value.error.orEmpty().contains("режим без сети"))

            // 2. Online analysis succeeds
            offlineMode.setOfflineDemo(false)
            runCurrent()
            vm.analyze()
            assertEquals(AnalysisPhase.PROCESSING, vm.uiState.value.inspection?.phase)
            assertEquals(NavEvent.To(NavRoute.AnalysisResult(tree.id)), vm.navEvents.first())

            advanceTimeBy(500L.milliseconds)
            runCurrent()
            assertEquals(AnalysisPhase.RESULT, vm.uiState.value.inspection?.phase)
            assertEquals("Пятнистость листьев", vm.uiState.value.inspection?.diagnosis)
            assertNotNull(vm.uiState.value.inspection?.originalAi)

            // 3. Offline occurring mid-flight reverts to DRAFT and emits NavEvent.Back
            vm.beginInspection(tree)
            assertEquals(NavEvent.To(NavRoute.Observe(tree.id)), vm.navEvents.first())
            vm.useSamplePhoto()
            vm.analyze()
            assertEquals(AnalysisPhase.PROCESSING, vm.uiState.value.inspection?.phase)
            assertEquals(NavEvent.To(NavRoute.AnalysisResult(tree.id)), vm.navEvents.first())

            advanceTimeBy(200L.milliseconds)
            offlineMode.setOfflineDemo(true)
            runCurrent()
            advanceTimeBy(400L.milliseconds)
            runCurrent()

            assertEquals(AnalysisPhase.DRAFT, vm.uiState.value.inspection?.phase)
            assertEquals("Анализ прерван: нет сети.", vm.uiState.value.error)
            assertEquals(NavEvent.Back, vm.navEvents.first())
        }
    }

    @Test
    fun treeInspectionViewModel_saveInspectionUpdatesTreeStatusAndEmitsSnackbar() = runTest {
        val repo = FakeOrchardRepository()
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val network = FakeNetworkMonitor(initialOnline = true)
        val offlineMode = OfflineModeManager(network, backgroundScope)
        val snackbar = SnackbarManager()
        val savedStateHandle = SavedStateHandle()

        val vm = TreeInspectionViewModel(
            savedStateHandle = savedStateHandle,
            stateProvider = provider,
            offlineMode = offlineMode,
            photos = dummyPhotoStore(),
            snackbarManager = snackbar,
            addTreeNoteUseCase = AddTreeNoteUseCase(repo, fixedClock),
            saveObservationUseCase = SaveObservationUseCase(repo, fixedClock),
            analyzePhotoUseCase = AnalyzePhotoUseCase(FakeDiagnosisService())
        )

        val garden = provider.currentContext()!!.garden
        val tree = garden.trees.first { it.status == TreeStatus.HEALTHY }

        vm.beginInspection(tree)
        assertEquals(NavEvent.To(NavRoute.Observe(tree.id)), vm.navEvents.first())

        vm.updateDraft(
            vm.uiState.value.inspection!!.copy(
                status = TreeStatus.DEAD,
                comment = "Корневая шейка погибла"
            )
        )
        vm.saveInspection(withAi = false)
        advanceUntilIdle()

        val updatedTree = repo.data.value.gardens
            .first { it.id == garden.id }
            .trees.first { it.id == tree.id }
        assertEquals(TreeStatus.DEAD, updatedTree.status)
        assertNull(vm.uiState.value.inspection)
        assertEquals("Осмотр сохранен для дерева", snackbar.messages.first())
        assertEquals(NavEvent.To(NavRoute.TreePassport(tree.id)), vm.navEvents.first())
    }

    // --- 3. IrrigationViewModel ---

    @Test
    fun irrigationViewModel_toggleZoneCellBlocksTreesOwnedByAnotherZone() = runTest {
        val repo = FakeOrchardRepository()
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val snackbar = SnackbarManager()
        val vm = IrrigationViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            stateProvider = provider,
            snackbarManager = snackbar,
            saveZoneUseCase = SaveZoneUseCase(repo),
            saveScheduleUseCase = SaveScheduleUseCase(repo)
        )

        val ctx = provider.currentContext()!!
        val existingZone = ctx.zones.first()
        val occupiedTreeId = existingZone.treeIds.first()
        val freeTree = ctx.garden.trees.first { tree -> ctx.zones.none { tree.id in it.treeIds } }

        vm.beginZone(zoneId = null)
        assertEquals(NavEvent.To(NavRoute.ZoneEditor(null)), vm.navEvents.first())

        // Occupied tree owned by another zone is blocked
        vm.toggleZoneCell(occupiedTreeId)
        assertFalse(occupiedTreeId in vm.uiState.value.zoneDraft.treeIds)

        // Free tree can be selected and deselected
        vm.toggleZoneCell(freeTree.id)
        assertTrue(freeTree.id in vm.uiState.value.zoneDraft.treeIds)
        vm.toggleZoneCell(freeTree.id)
        assertFalse(freeTree.id in vm.uiState.value.zoneDraft.treeIds)

        // Editing the owning zone itself allows toggling its own tree
        vm.beginZone(zoneId = existingZone.id)
        assertEquals(NavEvent.To(NavRoute.ZoneEditor(existingZone.id)), vm.navEvents.first())
        assertTrue(occupiedTreeId in vm.uiState.value.zoneDraft.treeIds)
        vm.toggleZoneCell(occupiedTreeId)
        assertFalse(occupiedTreeId in vm.uiState.value.zoneDraft.treeIds)
    }

    @Test
    fun irrigationViewModel_beginNewScheduleRedirectsToZoneEditorWhenNoZonesExist() = runTest {
        val seed = SeedData.create()
        val repo = FakeOrchardRepository(seed.copy(zones = emptyList(), schedules = emptyList()))
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val snackbar = SnackbarManager()
        val vm = IrrigationViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            stateProvider = provider,
            snackbarManager = snackbar,
            saveZoneUseCase = SaveZoneUseCase(repo),
            saveScheduleUseCase = SaveScheduleUseCase(repo)
        )

        vm.beginNewSchedule()
        assertEquals(NavEvent.To(NavRoute.ZoneEditor(null)), vm.navEvents.first())
        assertEquals("Сначала создайте зону из клеток", snackbar.messages.first())
    }

    @Test
    fun irrigationViewModel_wateringZoneIdsReactsToApplyPreviewTime() = runTest {
        val repo = FakeOrchardRepository()
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val snackbar = SnackbarManager()
        val vm = IrrigationViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            stateProvider = provider,
            snackbarManager = snackbar,
            saveZoneUseCase = SaveZoneUseCase(repo),
            saveScheduleUseCase = SaveScheduleUseCase(repo)
        )

        val zones = provider.currentContext()!!.zones
        val zoneA = zones[0] // scheduled 2026-10-29T09:00 .. 10:30
        val zoneB = zones[1] // scheduled 2026-10-29T18:00 .. 18:30

        // Default DEMO_TIME is 2026-10-29T09:41 -> zoneA is active
        assertEquals(setOf(zoneA.id), vm.wateringZoneIds.value)

        // Switch preview time to evening -> zoneB becomes active
        vm.applyPreviewTime("2026-10-29T18:15")
        advanceUntilIdle()
        assertEquals(setOf(zoneB.id), vm.wateringZoneIds.value)

        // Midday -> no zones watering
        vm.applyPreviewTime("2026-10-29T13:00")
        advanceUntilIdle()
        assertTrue(vm.wateringZoneIds.value.isEmpty())
    }

    // --- 4. ExpensesViewModel & ToolsViewModel ---

    @Test
    fun expensesViewModel_saveExpenseEmitsNavEventBackAndSnackbar() = runTest {
        val repo = FakeOrchardRepository()
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val snackbar = SnackbarManager()
        val vm = ExpensesViewModel(
            stateProvider = provider,
            snackbarManager = snackbar,
            saveExpenseUseCase = SaveExpenseUseCase(repo, fixedClock),
            updateFinanceUseCase = UpdateFinanceUseCase(repo),
            clock = fixedClock
        )

        val beforeCount = repo.data.value.expenses.size
        vm.saveExpense(
            title = "Секаторы садовые",
            category = Categories.first(),
            quantity = "4",
            unit = "шт.",
            price = "2500",
            date = DEMO_DATE
        )
        advanceUntilIdle()

        assertEquals(beforeCount + 1, repo.data.value.expenses.size)
        assertEquals("Затрата сохранена", snackbar.messages.first())
        assertEquals(NavEvent.Back, vm.navEvents.first())
    }

    @Test
    fun toolsViewModel_openQrSwitchesGardenAndEmitsTreePassportAndPrintUsesLiveConnection() = runTest {
        val repo = FakeOrchardRepository()
        val provider = ActiveGardenStateProvider(repo, backgroundScope)
        val network = FakeNetworkMonitor(initialOnline = true)
        val offlineMode = OfflineModeManager(network, backgroundScope)
        val snackbar = SnackbarManager()
        var printed: Pair<PlantLabel, Int>? = null
        val printer = object : PlantPrinter {
            override val status = MutableStateFlow(PrinterStatus())
            override fun pairedDevices() = emptyList<PairedPrinter>()
            override fun connect(device: PairedPrinter) { status.value = PrinterStatus(connected = device) }
            override fun disconnect() { status.value = PrinterStatus() }
            override fun print(label: PlantLabel, copies: Int) { printed = label to copies }
        }
        val vm = ToolsViewModel(
            savedStateHandle = SavedStateHandle(),
            printer = printer,
            stateProvider = provider,
            offlineMode = offlineMode,
            snackbarManager = snackbar,
            findTreeUseCase = FindTreeUseCase(repo)
        )

        val northGarden = repo.data.value.gardens.first()
        val appleGarden = repo.data.value.gardens.last()
        assertEquals(northGarden.id, repo.data.value.currentGardenId)

        val targetTree = appleGarden.trees.first()
        vm.openQr(qrPayload(targetTree))
        advanceUntilIdle()

        assertEquals(appleGarden.id, repo.data.value.currentGardenId)
        assertEquals(NavEvent.To(NavRoute.TreePassport(targetTree.id)), vm.navEvents.first())

        val label = PlantLabel(appleGarden.name, "ЯБ-0101", targetTree.row, targetTree.place, qrPayload(targetTree))
        vm.printLabel(label)
        assertEquals(NavEvent.To(NavRoute.Printer(returnToLabel = true)), vm.navEvents.first())
        vm.connectPrinter(PairedPrinter("TSPL", "00:11:22:33:44:55"))
        vm.updateCopies("3")
        vm.printLabel(label)
        assertEquals("Задание передано на печать", snackbar.messages.first())
        assertEquals(label to 3, printed)
    }
}
