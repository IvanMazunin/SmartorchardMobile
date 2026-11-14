package com.smartorchard.mobile.data

import com.smartorchard.mobile.data.local.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first репозиторий: все данные читаются из Room и доступны без интернета.
 * Запись сначала идёт в Room, затем (TODO) синхронизируется с сервером.
 *
 * Для переключения на этот репозиторий в AppModule:
 *   fun provideRepository(offline: OfflineFirstRepository): OrchardRepository = offline
 */
@Singleton
class OfflineFirstRepository @Inject constructor(
    private val dao: OrchardDao
) : OrchardRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Начальное значение пока Room не эмитит
    private val emptyData = AppData(crops = emptyList(), gardens = emptyList(), currentGardenId = "")

    // Комбинируем все Flow из DAO в единый AppData
    private val coreFlow: Flow<CoreSnapshot> = combine(
        dao.observeCrops(),
        dao.observeGardens(),
        dao.observeZones(),
        dao.observeSchedules(),
        dao.observeExpenses()
    ) { crops, gardens, zones, schedules, expenses ->
        CoreSnapshot(
            crops.map { it.toDomain() },
            gardens.map { it.toDomain() },
            zones.map { it.toDomain() },
            schedules.map { it.toDomain() },
            expenses.map { it.toDomain() }
        )
    }

    override val data: StateFlow<AppData> = combine(
        coreFlow,
        dao.observeObservations(),
        dao.observeNotes(),
        dao.observeSettings()
    ) { core, observations, notes, settings ->
        AppData(
            crops = core.crops,
            gardens = core.gardens,
            currentGardenId = settings?.currentGardenId
                ?: core.gardens.firstOrNull()?.id ?: "",
            zones = core.zones,
            schedules = core.schedules,
            expenses = core.expenses,
            observations = observations.map { it.toDomain() },
            notes = notes.map { it.toDomain() },
            printer = DemoPrinter(settings?.printerDevice)
        )
    }.stateIn(scope, SharingStarted.Eagerly, emptyData)

    init {
        // При первом запуске заполняем базу демоданными
        scope.launch { seedIfEmpty() }
    }

    private suspend fun seedIfEmpty() {
        if (dao.cropCount() > 0) return
        val seed = SeedData.create()
        dao.insertCrops(seed.crops.map { it.toEntity() })
        dao.insertGardens(seed.gardens.map { it.toEntity() })
        dao.insertZones(seed.zones.map { it.toEntity() })
        dao.insertSchedules(seed.schedules.map { it.toEntity() })
        dao.insertExpenses(seed.expenses.map { it.toEntity() })
        dao.insertSettings(UserSettingsEntity(currentGardenId = seed.currentGardenId))
    }

    override suspend fun setCurrentGarden(gardenId: String) {
        val current = dao.getSettings() ?: UserSettingsEntity()
        dao.insertSettings(current.copy(currentGardenId = gardenId))
    }

    override suspend fun addCrop(crop: Crop) {
        dao.insertCrop(crop.toEntity())
    }

    override suspend fun addGarden(garden: Garden) {
        dao.insertGarden(garden.toEntity())
        setCurrentGarden(garden.id)
    }

    override suspend fun saveZone(zone: IrrigationZone) {
        dao.insertZone(zone.toEntity())
    }

    override suspend fun addSchedule(schedule: IrrigationSchedule) {
        dao.insertSchedule(schedule.toEntity())
    }

    override suspend fun deleteSchedule(scheduleId: String) {
        dao.deleteSchedule(scheduleId)
    }

    override suspend fun addExpense(expense: Expense) {
        dao.insertExpense(expense.toEntity())
    }

    override suspend fun updateGardenFinance(gardenId: String, finance: Finance) {
        dao.updateFinance(gardenId, finance.revenueCents, finance.annualRevenueCents, finance.annualCostsCents)
    }

    override suspend fun addNote(note: TreeNote) {
        dao.insertNote(note.toEntity())
    }

    override suspend fun addObservation(observation: Observation, gardenId: String, treeId: String, newStatus: TreeStatus) {
        dao.insertObservation(observation.toEntity())
        // Обновляем статус дерева в саду (trees хранятся как JSON в GardenEntity)
        val entity = dao.getGarden(gardenId) ?: return
        val updatedTrees = entity.trees.map { t ->
            if (t.id == treeId) t.copy(status = newStatus) else t
        }
        dao.updateGarden(entity.copy(trees = updatedTrees))
    }

    override suspend fun updatePrinter(printer: DemoPrinter) {
        val current = dao.getSettings() ?: UserSettingsEntity()
        dao.insertSettings(current.copy(printerDevice = printer.device))
    }
}

/** Промежуточный снимок для комбинирования потоков. */
private data class CoreSnapshot(
    val crops: List<Crop>,
    val gardens: List<Garden>,
    val zones: List<IrrigationZone>,
    val schedules: List<IrrigationSchedule>,
    val expenses: List<Expense>
)
