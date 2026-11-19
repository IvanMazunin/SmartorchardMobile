package com.smartorchard.mobile.data

import com.smartorchard.mobile.data.local.*
import com.smartorchard.mobile.data.sync.DeleteSchedulePayload
import com.smartorchard.mobile.data.sync.FinanceSyncPayload
import com.smartorchard.mobile.data.sync.SettingsSyncPayload
import com.smartorchard.mobile.data.sync.SyncScheduler
import com.smartorchard.mobile.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first репозиторий: все данные читаются из Room и доступны без интернета.
 * Каждая мутация атомарно сохраняется в Room вместе с записью в очередь sync_outbox,
 * после чего планируется фоновая отправка через WorkManager (SyncScheduler).
 */
@Singleton
class OfflineFirstRepository @Inject constructor(
    private val dao: OrchardDao,
    private val syncScheduler: SyncScheduler,
    private val json: Json,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope
) : OrchardRepository {

    // Начальное значение пока Room не эмитит
    private val emptyData = AppData(crops = emptyList(), gardens = emptyList(), currentGardenId = "")

    // Комбинируем все Flow из DAO в единый AppData
    private val gardenCoreFlow: Flow<CoreSnapshot> = combine(
        dao.observeCrops(),
        dao.observeGardens(),
        dao.observeTrees(),
        dao.observeZones(),
        dao.observeSchedules()
    ) { crops, gardens, trees, zones, schedules ->
        val treesByGarden = trees.groupBy({ it.gardenId }, { it.toDomain() })
        val treeIdsByZone = trees
            .filter { it.zoneId != null }
            .groupBy({ it.zoneId!! }, { it.id })
        CoreSnapshot(
            crops = crops.map { it.toDomain() },
            gardens = gardens.map { gardenEntity ->
                gardenEntity.toDomain(treesByGarden[gardenEntity.id].orEmpty())
            },
            zones = zones.map { zoneEntity ->
                zoneEntity.toDomain(treeIdsByZone[zoneEntity.id].orEmpty())
            },
            schedules = schedules.map { it.toDomain() }
        )
    }

    override val data: StateFlow<AppData> = combine(
        gardenCoreFlow,
        dao.observeExpenses(),
        dao.observeObservations(),
        dao.observeNotes(),
        dao.observeSettings()
    ) { core, expenses, observations, notes, settings ->
        AppData(
            crops = core.crops,
            gardens = core.gardens,
            currentGardenId = settings?.currentGardenId
                ?: core.gardens.firstOrNull()?.id ?: "",
            zones = core.zones,
            schedules = core.schedules,
            expenses = expenses.map { it.toDomain() },
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
        val zoneByTreeId: Map<String, String> = buildMap {
            seed.zones.forEach { zone ->
                zone.treeIds.forEach { treeId ->
                    put(treeId, zone.id)
                }
            }
        }
        val allTrees = seed.gardens.flatMap { g ->
            g.trees.map { t -> t.toEntity(g.id, zoneByTreeId[t.id]) }
        }
        dao.seedInitialData(
            crops = seed.crops.map { it.toEntity() },
            gardens = seed.gardens.map { it.toEntity() },
            zones = seed.zones.map { it.toEntity() },
            trees = allTrees,
            schedules = seed.schedules.map { it.toEntity() },
            expenses = seed.expenses.map { it.toEntity() },
            settings = UserSettingsEntity(currentGardenId = seed.currentGardenId)
        )
    }

    private inline fun <reified T> outboxEntry(
        entityType: SyncEntityType,
        entityId: String,
        operation: SyncOperation,
        payload: T
    ): SyncOutboxEntity = SyncOutboxEntity(
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        payloadJson = json.encodeToString(payload),
        createdAt = clock.millis()
    )

    override suspend fun setCurrentGarden(gardenId: String) {
        val current = dao.getSettings() ?: UserSettingsEntity()
        val updated = current.copy(currentGardenId = gardenId)
        val outbox = outboxEntry(
            entityType = SyncEntityType.SETTINGS,
            entityId = "user_settings",
            operation = SyncOperation.UPSERT,
            payload = SettingsSyncPayload(
                currentGardenId = updated.currentGardenId,
                printer = DemoPrinter(updated.printerDevice)
            )
        )
        dao.upsertSettingsWithOutbox(updated, outbox)
        syncScheduler.scheduleSync()
    }

    override suspend fun addCrop(crop: Crop) {
        val outbox = outboxEntry(
            entityType = SyncEntityType.CROP,
            entityId = crop.id,
            operation = SyncOperation.UPSERT,
            payload = crop
        )
        dao.insertCropWithOutbox(crop.toEntity(), outbox)
        syncScheduler.scheduleSync()
    }

    override suspend fun addGarden(garden: Garden) {
        val currentPrinter = dao.getSettings()?.printerDevice
        val outbox = outboxEntry(
            entityType = SyncEntityType.GARDEN,
            entityId = garden.id,
            operation = SyncOperation.UPSERT,
            payload = garden
        )
        dao.insertGardenWithTreesAndOutbox(
            garden = garden.toEntity(),
            trees = garden.trees.map { it.toEntity(garden.id) },
            printerDevice = currentPrinter,
            outbox = outbox
        )
        syncScheduler.scheduleSync()
    }

    override suspend fun saveZone(zone: IrrigationZone) {
        val outbox = outboxEntry(
            entityType = SyncEntityType.ZONE,
            entityId = zone.id,
            operation = SyncOperation.UPSERT,
            payload = zone
        )
        dao.upsertZoneWithTreesAndOutbox(
            zone = zone.toEntity(),
            treeIds = zone.treeIds,
            outbox = outbox
        )
        syncScheduler.scheduleSync()
    }

    override suspend fun addSchedule(schedule: IrrigationSchedule) {
        val outbox = outboxEntry(
            entityType = SyncEntityType.SCHEDULE,
            entityId = schedule.id,
            operation = SyncOperation.UPSERT,
            payload = schedule
        )
        dao.insertScheduleWithOutbox(schedule.toEntity(), outbox)
        syncScheduler.scheduleSync()
    }

    override suspend fun deleteSchedule(scheduleId: String) {
        val outbox = outboxEntry(
            entityType = SyncEntityType.SCHEDULE,
            entityId = scheduleId,
            operation = SyncOperation.DELETE,
            payload = DeleteSchedulePayload(scheduleId = scheduleId)
        )
        dao.deleteScheduleWithOutbox(scheduleId, outbox)
        syncScheduler.scheduleSync()
    }

    override suspend fun addExpense(expense: Expense) {
        val outbox = outboxEntry(
            entityType = SyncEntityType.EXPENSE,
            entityId = expense.id,
            operation = SyncOperation.UPSERT,
            payload = expense
        )
        dao.insertExpenseWithOutbox(expense.toEntity(), outbox)
        syncScheduler.scheduleSync()
    }

    override suspend fun updateGardenFinance(gardenId: String, finance: Finance) {
        val outbox = outboxEntry(
            entityType = SyncEntityType.FINANCE,
            entityId = gardenId,
            operation = SyncOperation.UPSERT,
            payload = FinanceSyncPayload(gardenId = gardenId, finance = finance)
        )
        dao.updateFinanceWithOutbox(
            gardenId = gardenId,
            revenueCents = finance.revenueCents,
            annualRevenueCents = finance.annualRevenueCents,
            annualCostsCents = finance.annualCostsCents,
            outbox = outbox
        )
        syncScheduler.scheduleSync()
    }

    override suspend fun addNote(note: TreeNote) {
        val outbox = outboxEntry(
            entityType = SyncEntityType.NOTE,
            entityId = note.id,
            operation = SyncOperation.UPSERT,
            payload = note
        )
        dao.insertNoteWithOutbox(note.toEntity(), outbox)
        syncScheduler.scheduleSync()
    }

    override suspend fun addObservation(
        observation: Observation,
        gardenId: String,
        treeId: String,
        newStatus: TreeStatus
    ) {
        val outbox = outboxEntry(
            entityType = SyncEntityType.OBSERVATION,
            entityId = observation.id,
            operation = SyncOperation.UPSERT,
            payload = observation
        )
        dao.insertObservationAndUpdateTreeWithOutbox(
            observation = observation.toEntity(),
            treeId = treeId,
            newStatus = newStatus,
            outbox = outbox
        )
        syncScheduler.scheduleSync()
    }

    override suspend fun updatePrinter(printer: DemoPrinter) {
        val current = dao.getSettings() ?: UserSettingsEntity()
        val updated = current.copy(printerDevice = printer.device)
        val outbox = outboxEntry(
            entityType = SyncEntityType.SETTINGS,
            entityId = "user_settings",
            operation = SyncOperation.UPSERT,
            payload = SettingsSyncPayload(
                currentGardenId = updated.currentGardenId,
                printer = printer
            )
        )
        dao.upsertSettingsWithOutbox(updated, outbox)
        syncScheduler.scheduleSync()
    }
}

/** Промежуточный снимок для комбинирования потоков. */
private data class CoreSnapshot(
    val crops: List<Crop>,
    val gardens: List<Garden>,
    val zones: List<IrrigationZone>,
    val schedules: List<IrrigationSchedule>
)
