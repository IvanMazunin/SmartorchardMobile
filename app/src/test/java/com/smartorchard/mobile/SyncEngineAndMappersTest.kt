package com.smartorchard.mobile

import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.data.local.*
import com.smartorchard.mobile.data.sync.DeleteSchedulePayload
import com.smartorchard.mobile.data.sync.FinanceSyncPayload
import com.smartorchard.mobile.data.sync.SettingsSyncPayload
import com.smartorchard.mobile.data.sync.SyncEngine
import com.smartorchard.mobile.data.sync.SyncResult
import com.smartorchard.mobile.network.api.OrchardApi
import com.smartorchard.mobile.network.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SyncEngineAndMappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private class FakeOrchardDao : OrchardDao {
        val cropsState = MutableStateFlow<List<CropEntity>>(emptyList())
        val gardensState = MutableStateFlow<List<GardenEntity>>(emptyList())
        val treesState = MutableStateFlow<List<TreeEntity>>(emptyList())
        val zonesState = MutableStateFlow<List<ZoneEntity>>(emptyList())
        val schedulesState = MutableStateFlow<List<ScheduleEntity>>(emptyList())
        val expensesState = MutableStateFlow<List<ExpenseEntity>>(emptyList())
        val observationsState = MutableStateFlow<List<ObservationEntity>>(emptyList())
        val notesState = MutableStateFlow<List<NoteEntity>>(emptyList())
        val settingsState = MutableStateFlow<UserSettingsEntity?>(null)
        val outboxState = MutableStateFlow<List<SyncOutboxEntity>>(emptyList())

        private var nextOutboxId = 1L
        var replaceSnapshotCallCount = 0

        override fun observeCrops(): Flow<List<CropEntity>> = cropsState
        override suspend fun insertCrop(crop: CropEntity) {
            cropsState.value = cropsState.value.filterNot { it.id == crop.id } + crop
        }
        override suspend fun insertCrops(crops: List<CropEntity>) {
            crops.forEach { insertCrop(it) }
        }
        override suspend fun deleteAllCrops() {
            cropsState.value = emptyList()
        }

        override fun observeGardens(): Flow<List<GardenEntity>> = gardensState
        override suspend fun insertGarden(garden: GardenEntity) {
            gardensState.value = gardensState.value.filterNot { it.id == garden.id } + garden
        }
        override suspend fun insertGardens(gardens: List<GardenEntity>) {
            gardens.forEach { insertGarden(it) }
        }
        override suspend fun updateFinance(
            gardenId: String,
            revenueCents: Long,
            annualRevenueCents: Long,
            annualCostsCents: Long
        ) {
            gardensState.value = gardensState.value.map {
                if (it.id == gardenId) {
                    it.copy(
                        revenueCents = revenueCents,
                        annualRevenueCents = annualRevenueCents,
                        annualCostsCents = annualCostsCents
                    )
                } else {
                    it
                }
            }
        }
        override suspend fun getGarden(gardenId: String): GardenEntity? =
            gardensState.value.find { it.id == gardenId }
        override suspend fun updateGarden(garden: GardenEntity) = insertGarden(garden)
        override suspend fun deleteAllGardens() {
            gardensState.value = emptyList()
        }

        override fun observeTrees(): Flow<List<TreeEntity>> = treesState
        override suspend fun insertTrees(trees: List<TreeEntity>) {
            val incomingIds = trees.map { it.id }.toSet()
            treesState.value = (treesState.value.filterNot { it.id in incomingIds } + trees)
                .sortedWith(compareBy<TreeEntity> { it.row }.thenBy { it.place })
        }
        override suspend fun updateTreeStatus(treeId: String, status: TreeStatus) {
            treesState.value = treesState.value.map {
                if (it.id == treeId) it.copy(status = status) else it
            }
        }
        override suspend fun clearTreesFromZone(zoneId: String) {
            treesState.value = treesState.value.map {
                if (it.zoneId == zoneId) it.copy(zoneId = null) else it
            }
        }
        override suspend fun assignTreesToZone(zoneId: String, treeIds: List<String>) {
            val idSet = treeIds.toSet()
            treesState.value = treesState.value.map {
                if (it.id in idSet) it.copy(zoneId = zoneId) else it
            }
        }
        override suspend fun deleteAllTrees() {
            treesState.value = emptyList()
        }

        override fun observeZones(): Flow<List<ZoneEntity>> = zonesState
        override suspend fun insertZone(zone: ZoneEntity) {
            zonesState.value = zonesState.value.filterNot { it.id == zone.id } + zone
        }
        override suspend fun insertZones(zones: List<ZoneEntity>) {
            zones.forEach { insertZone(it) }
        }
        override suspend fun deleteAllZones() {
            zonesState.value = emptyList()
        }

        override fun observeSchedules(): Flow<List<ScheduleEntity>> = schedulesState
        override suspend fun insertSchedule(schedule: ScheduleEntity) {
            schedulesState.value = schedulesState.value.filterNot { it.id == schedule.id } + schedule
        }
        override suspend fun insertSchedules(schedules: List<ScheduleEntity>) {
            schedules.forEach { insertSchedule(it) }
        }
        override suspend fun deleteSchedule(id: String) {
            schedulesState.value = schedulesState.value.filterNot { it.id == id }
        }
        override suspend fun deleteAllSchedules() {
            schedulesState.value = emptyList()
        }

        override fun observeExpenses(): Flow<List<ExpenseEntity>> = expensesState
        override suspend fun insertExpense(expense: ExpenseEntity) {
            expensesState.value = expensesState.value.filterNot { it.id == expense.id } + expense
        }
        override suspend fun insertExpenses(expenses: List<ExpenseEntity>) {
            expenses.forEach { insertExpense(it) }
        }
        override suspend fun deleteAllExpenses() {
            expensesState.value = emptyList()
        }

        override fun observeObservations(): Flow<List<ObservationEntity>> = observationsState
        override suspend fun insertObservation(observation: ObservationEntity) {
            observationsState.value = observationsState.value.filterNot { it.id == observation.id } + observation
        }

        override fun observeNotes(): Flow<List<NoteEntity>> = notesState
        override suspend fun insertNote(note: NoteEntity) {
            notesState.value = notesState.value.filterNot { it.id == note.id } + note
        }

        override fun observeSettings(): Flow<UserSettingsEntity?> = settingsState
        override suspend fun getSettings(): UserSettingsEntity? = settingsState.value
        override suspend fun insertSettings(settings: UserSettingsEntity) {
            settingsState.value = settings
        }

        override suspend fun insertOutbox(entry: SyncOutboxEntity): Long {
            val assignedId = if (entry.id == 0L) nextOutboxId++ else entry.id
            val stored = entry.copy(id = assignedId)
            outboxState.value += stored
            return assignedId
        }

        override suspend fun getPendingOutbox(): List<SyncOutboxEntity> =
            outboxState.value.sortedWith(compareBy<SyncOutboxEntity> { it.createdAt }.thenBy { it.id })

        override fun observePendingSyncCount(): Flow<Int> = outboxState.map { it.size }

        override suspend fun deleteOutboxById(id: Long) {
            outboxState.value = outboxState.value.filterNot { it.id == id }
        }

        override suspend fun markOutboxFailed(id: Long, error: String) {
            outboxState.value = outboxState.value.map {
                if (it.id == id) it.copy(retryCount = it.retryCount + 1, lastError = error) else it
            }
        }

        override suspend fun cropCount(): Int = cropsState.value.size

        override suspend fun replaceRemoteSnapshot(
            crops: List<CropEntity>,
            gardens: List<GardenEntity>,
            zones: List<ZoneEntity>,
            trees: List<TreeEntity>,
            schedules: List<ScheduleEntity>,
            expenses: List<ExpenseEntity>,
            settings: UserSettingsEntity?
        ) {
            replaceSnapshotCallCount++
            super.replaceRemoteSnapshot(crops, gardens, zones, trees, schedules, expenses, settings)
        }
    }

    private class FakeOrchardApi : OrchardApi {
        val callOrder = mutableListOf<String>()

        var failOnExpenseError: IOException? = null

        var remoteCrops: List<Crop> = emptyList()
        var remoteGardens: List<Garden> = emptyList()
        var remoteZonesByGarden: Map<String, List<IrrigationZone>> = emptyMap()
        var remoteSchedulesByGarden: Map<String, List<IrrigationSchedule>> = emptyMap()
        var remoteExpensesByGarden: Map<String, List<Expense>> = emptyMap()
        var remoteSettings: UserSettingsResponse = UserSettingsResponse(currentGardenId = "")

        var lastCreateCrop: CreateCropRequest? = null
        var lastCreateGarden: CreateGardenRequest? = null
        var lastZoneCall: Pair<String, CreateZoneRequest>? = null
        var lastCreateSchedule: Pair<String, CreateScheduleRequest>? = null
        var lastDeletedScheduleId: String? = null
        var lastCreateExpense: Pair<String, CreateExpenseRequest>? = null
        var lastUpdateFinance: Pair<String, UpdateFinanceRequest>? = null
        var lastCreateNote: Pair<String, CreateNoteRequest>? = null
        var lastCreateObservation: CreateObservationRequest? = null
        var lastUpdatedSettings: UserSettingsResponse? = null

        override suspend fun getGardens(): ApiResponse<List<Garden>> {
            callOrder += "GET_GARDENS"
            return ApiResponse(remoteGardens)
        }

        override suspend fun createGarden(request: CreateGardenRequest): ApiResponse<Garden> {
            callOrder += "CREATE_GARDEN:${request.name}"
            lastCreateGarden = request
            return ApiResponse(
                Garden("g-new", request.name, request.cropId, request.areaHa, request.plantingDate, request.placesPerRow, 0, emptyList())
            )
        }

        override suspend fun updateGardenFinance(gardenId: String, request: UpdateFinanceRequest): ApiResponse<Garden> {
            callOrder += "UPDATE_FINANCE:$gardenId"
            lastUpdateFinance = gardenId to request
            return ApiResponse(
                Garden(gardenId, "G", "c1", "1", "2026-10-29", 10, 0, emptyList(), Finance(request.revenueCents, request.annualRevenueCents, request.annualCostsCents))
            )
        }

        override suspend fun getCrops(): ApiResponse<List<Crop>> {
            callOrder += "GET_CROPS"
            return ApiResponse(remoteCrops)
        }

        override suspend fun createCrop(request: CreateCropRequest): ApiResponse<Crop> {
            callOrder += "CREATE_CROP:${request.name}"
            lastCreateCrop = request
            return ApiResponse(Crop("c-new", request.name, request.variety, request.rowSpacing, request.treeSpacing))
        }

        override suspend fun getObservations(treeId: String): ApiResponse<List<Observation>> =
            ApiResponse(emptyList())

        override suspend fun createObservation(request: CreateObservationRequest): ApiResponse<Observation> {
            callOrder += "CREATE_OBSERVATION:${request.treeId}"
            lastCreateObservation = request
            return ApiResponse(
                Observation(
                    id = "o-new",
                    gardenId = request.gardenId,
                    treeId = request.treeId,
                    createdAt = "2026-11-18T10:00Z",
                    status = request.status,
                    comment = request.comment,
                    diagnosis = request.diagnosis,
                    recommendations = request.recommendations,
                    photoPath = request.photoUrl,
                    samplePhoto = request.samplePhoto,
                    originalAi = request.originalAi
                )
            )
        }

        override suspend fun analyzeDiagnosis(request: DiagnosisRequest): ApiResponse<AiResult> =
            ApiResponse(AiResult("Diag", "Rec"))

        override suspend fun getZones(gardenId: String): ApiResponse<List<IrrigationZone>> {
            callOrder += "GET_ZONES:$gardenId"
            return ApiResponse(remoteZonesByGarden[gardenId].orEmpty())
        }

        override suspend fun createOrUpdateZone(gardenId: String, request: CreateZoneRequest): ApiResponse<IrrigationZone> {
            callOrder += "UPSERT_ZONE:${request.id}"
            lastZoneCall = gardenId to request
            return ApiResponse(
                IrrigationZone(request.id ?: "z-new", gardenId, request.name, request.color, request.treeIds)
            )
        }

        override suspend fun getSchedules(gardenId: String): ApiResponse<List<IrrigationSchedule>> {
            callOrder += "GET_SCHEDULES:$gardenId"
            return ApiResponse(remoteSchedulesByGarden[gardenId].orEmpty())
        }

        override suspend fun createSchedule(gardenId: String, request: CreateScheduleRequest): ApiResponse<IrrigationSchedule> {
            callOrder += "CREATE_SCHEDULE:${request.zoneId}"
            lastCreateSchedule = gardenId to request
            return ApiResponse(IrrigationSchedule("s-new", gardenId, request.zoneId, request.start, request.end))
        }

        override suspend fun deleteSchedule(scheduleId: String) {
            callOrder += "DELETE_SCHEDULE:$scheduleId"
            lastDeletedScheduleId = scheduleId
        }

        override suspend fun getExpenses(gardenId: String): ApiResponse<List<Expense>> {
            callOrder += "GET_EXPENSES:$gardenId"
            return ApiResponse(remoteExpensesByGarden[gardenId].orEmpty())
        }

        override suspend fun createExpense(gardenId: String, request: CreateExpenseRequest): ApiResponse<Expense> {
            failOnExpenseError?.let { throw it }
            callOrder += "CREATE_EXPENSE:${request.title}"
            lastCreateExpense = gardenId to request
            return ApiResponse(
                Expense("e-new", gardenId, request.title, request.category, request.quantity, request.unit, request.price, 0L, request.date)
            )
        }

        override suspend fun getNotes(treeId: String): ApiResponse<List<TreeNote>> =
            ApiResponse(emptyList())

        override suspend fun createNote(treeId: String, request: CreateNoteRequest): ApiResponse<TreeNote> {
            callOrder += "CREATE_NOTE:$treeId"
            lastCreateNote = treeId to request
            return ApiResponse(TreeNote("n-new", treeId, request.text, "2026-11-18T10:00Z"))
        }

        override suspend fun getUserSettings(): ApiResponse<UserSettingsResponse> {
            callOrder += "GET_SETTINGS"
            return ApiResponse(remoteSettings)
        }

        override suspend fun updateUserSettings(settings: UserSettingsResponse): ApiResponse<UserSettingsResponse> {
            callOrder += "UPDATE_SETTINGS:${settings.currentGardenId}"
            lastUpdatedSettings = settings
            return ApiResponse(settings)
        }
    }

    // --- Converters & EntityMappers ---

    @Test
    fun converters_roundTripEnumsAndNullableAiResult() {
        val converters = Converters()

        for (status in TreeStatus.entries) {
            assertEquals(status, converters.stringToStatus(converters.statusToString(status)))
        }
        for (entityType in SyncEntityType.entries) {
            assertEquals(entityType, converters.stringToSyncEntityType(converters.syncEntityTypeToString(entityType)))
        }
        for (op in SyncOperation.entries) {
            assertEquals(op, converters.stringToSyncOperation(converters.syncOperationToString(op)))
        }

        val ai = AiResult(diagnosis = "Парша яблони", recommendations = "Обработка фунгицидом")
        val encoded = converters.aiResultToJson(ai)
        assertNotNull(encoded)
        assertEquals(ai, converters.jsonToAiResult(encoded))

        assertNull(converters.aiResultToJson(null))
        assertNull(converters.jsonToAiResult(null))
    }

    @Test
    fun entityMappers_roundTripAllDomainAndDatabaseModels() {
        val crop = Crop("c1", "Яблоня", "Фуджи", "4.5", "2.0", "ЯБ")
        assertEquals(crop, crop.toEntity().toDomain())

        val treeUnassigned = Tree("t1", row = 1, place = 2, status = TreeStatus.HEALTHY)
        val treeUnassignedEntity = treeUnassigned.toEntity(gardenId = "g1", zoneId = null)
        assertEquals("g1", treeUnassignedEntity.gardenId)
        assertNull(treeUnassignedEntity.zoneId)
        assertEquals(treeUnassigned, treeUnassignedEntity.toDomain())

        val treeAssigned = Tree("t2", row = 2, place = 3, status = TreeStatus.WARNING)
        val treeAssignedEntity = treeAssigned.toEntity(gardenId = "g1", zoneId = "z1")
        assertEquals("z1", treeAssignedEntity.zoneId)
        assertEquals(treeAssigned, treeAssignedEntity.toDomain())

        val garden = Garden(
            id = "g1",
            name = "Восточный квартал",
            cropId = "c1",
            areaHa = "1.5",
            plantingDate = "2026-10-15",
            placesPerRow = 25,
            initialTreeCount = 2,
            trees = listOf(treeUnassigned, treeAssigned),
            finance = Finance(revenueCents = 500_000L, annualRevenueCents = 200_000L, annualCostsCents = 80_000L)
        )
        assertEquals(garden, garden.toEntity().toDomain(garden.trees))

        val zone = IrrigationZone(
            id = "z1",
            gardenId = "g1",
            name = "Контур Севера",
            color = 3,
            treeIds = listOf("t2", "t3")
        )
        val zoneEntity = zone.toEntity()
        assertEquals("z1", zoneEntity.id)
        assertEquals("g1", zoneEntity.gardenId)
        assertEquals(zone, zoneEntity.toDomain(listOf("t2", "t3")))

        val schedule = IrrigationSchedule("s1", "g1", "z1", "2026-11-18T06:00", "2026-11-18T07:30")
        assertEquals(schedule, schedule.toEntity().toDomain())

        val expense = Expense("e1", "g1", "Форсунки", "Оборудование", "20", "шт", "150.00", 300_000L, "2026-11-18")
        assertEquals(expense, expense.toEntity().toDomain())

        val ai = AiResult("Хлороз листьев", "Внекорневая подкормка хелатом железа")
        val observation = Observation(
            id = "o1",
            gardenId = "g1",
            treeId = "t2",
            createdAt = "2026-11-18T10:00Z",
            status = TreeStatus.WARNING,
            comment = "Нижний ярус",
            diagnosis = "Железный хлороз",
            recommendations = "Хелат железа по листу",
            photoPath = "/storage/photos/obs1.jpg",
            samplePhoto = false,
            originalAi = ai
        )
        assertEquals(observation, observation.toEntity().toDomain())

        val note = TreeNote("n1", "t1", "Заменить колышек", "2026-11-18T10:00Z")
        assertEquals(note, note.toEntity().toDomain())
    }

    // --- SyncEngine: processOutbox ---

    @Test
    fun syncEngine_processOutboxDispatchesAllEntityTypesInFifoOrderAndClearsOutbox(): Unit = runTest {
        val dao = FakeOrchardDao()
        val api = FakeOrchardApi()
        val engine = SyncEngine(dao, api, json)

        val crop = Crop("c1", "Груша", "Конференция", "5", "3", "ГР")
        val garden = Garden("g1", "Южный сад", "c1", "0.5", "2026-11-01", 10, 1, listOf(Tree("t1", 1, 1)))
        val zone = IrrigationZone("z1", "g1", "Зона 1", 2, listOf("t1"))
        val schedule = IrrigationSchedule("s1", "g1", "z1", "2026-11-18T08:00", "2026-11-18T09:00")
        val expense = Expense("e1", "g1", "Секатор", "Оборудование", "2", "шт", "1200", 240_000L, "2026-11-18")
        val financePayload = FinanceSyncPayload("g1", Finance(100_00L, 50_00L, 20_00L))
        val note = TreeNote("n1", "t1", "Осмотрено", "2026-11-18T10:00Z")
        val observation = Observation(
            id = "o1",
            gardenId = "g1",
            treeId = "t1",
            createdAt = "2026-11-18T10:00Z",
            status = TreeStatus.WARNING,
            comment = "Пятна",
            diagnosis = "Парша",
            recommendations = "Опрыскивание",
            photoPath = "/photos/p1.jpg",
            samplePhoto = true,
            originalAi = AiResult("ИИ Парша", "ИИ Опрыскивание")
        )
        val settingsPayload = SettingsSyncPayload("g1", DemoPrinter("Zebra-BT"))

        // Enqueue with explicit timestamps (including out-of-insertion-order timestamps to verify FIFO sort)
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.GARDEN, garden.id, SyncOperation.UPSERT, json.encodeToString(garden), createdAt = 200L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.CROP, crop.id, SyncOperation.UPSERT, json.encodeToString(crop), createdAt = 100L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.ZONE, zone.id, SyncOperation.UPSERT, json.encodeToString(zone), createdAt = 300L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.SCHEDULE, schedule.id, SyncOperation.UPSERT, json.encodeToString(schedule), createdAt = 400L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.SCHEDULE, "s-del", SyncOperation.DELETE, json.encodeToString(DeleteSchedulePayload("s-del")), createdAt = 500L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.EXPENSE, expense.id, SyncOperation.UPSERT, json.encodeToString(expense), createdAt = 600L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.FINANCE, "g1", SyncOperation.UPSERT, json.encodeToString(financePayload), createdAt = 700L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.NOTE, note.id, SyncOperation.UPSERT, json.encodeToString(note), createdAt = 800L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.OBSERVATION, observation.id, SyncOperation.UPSERT, json.encodeToString(observation), createdAt = 900L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.SETTINGS, "user_settings", SyncOperation.UPSERT, json.encodeToString(settingsPayload), createdAt = 1000L))

        val result = engine.processOutbox()

        assertEquals(SyncResult(syncedCount = 10, failedCount = 0), result)
        assertTrue(dao.getPendingOutbox().isEmpty())

        assertEquals(
            listOf(
                "CREATE_CROP:Груша",
                "CREATE_GARDEN:Южный сад",
                "UPSERT_ZONE:z1",
                "CREATE_SCHEDULE:z1",
                "DELETE_SCHEDULE:s-del",
                "CREATE_EXPENSE:Секатор",
                "UPDATE_FINANCE:g1",
                "CREATE_NOTE:t1",
                "CREATE_OBSERVATION:t1",
                "UPDATE_SETTINGS:g1"
            ),
            api.callOrder
        )

        assertEquals(CreateCropRequest("Груша", "Конференция", "5", "3"), api.lastCreateCrop)
        assertEquals(CreateGardenRequest("Южный сад", "c1", "0.5", "2026-11-01", 10), api.lastCreateGarden)
        assertEquals("g1" to CreateZoneRequest("z1", "g1", "Зона 1", 2, listOf("t1")), api.lastZoneCall)
        assertEquals("g1" to CreateScheduleRequest("g1", "z1", "2026-11-18T08:00", "2026-11-18T09:00"), api.lastCreateSchedule)
        assertEquals("s-del", api.lastDeletedScheduleId)
        assertEquals("g1" to CreateExpenseRequest("g1", "Секатор", "Оборудование", "2", "шт", "1200", "2026-11-18"), api.lastCreateExpense)
        assertEquals("g1" to UpdateFinanceRequest(10_000L, 5_000L, 2_000L), api.lastUpdateFinance)
        assertEquals("t1" to CreateNoteRequest("t1", "Осмотрено"), api.lastCreateNote)
        assertEquals(
            CreateObservationRequest(
                gardenId = "g1",
                treeId = "t1",
                status = TreeStatus.WARNING,
                comment = "Пятна",
                diagnosis = "Парша",
                recommendations = "Опрыскивание",
                photoUrl = "/photos/p1.jpg",
                samplePhoto = true,
                originalAi = AiResult("ИИ Парша", "ИИ Опрыскивание")
            ),
            api.lastCreateObservation
        )
        assertEquals(UserSettingsResponse("g1", DemoPrinter("Zebra-BT")), api.lastUpdatedSettings)
    }

    @Test
    fun syncEngine_processOutboxMarksFailedEntryAndContinuesProcessingHealthyEntries(): Unit = runTest {
        val dao = FakeOrchardDao()
        val api = FakeOrchardApi().apply {
            failOnExpenseError = IOException("Broken pipe while uploading expense")
        }
        val engine = SyncEngine(dao, api, json)

        val crop = Crop("c1", "Миндаль", "Никитский", "6", "4", "МД")
        val expense = Expense("e-bad", "g1", "Шланг", "Материалы", "1", "шт", "500", 50_000L, "2026-11-18")
        val note = TreeNote("n1", "t1", "После полива", "2026-11-18T10:00Z")

        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.CROP, crop.id, SyncOperation.UPSERT, json.encodeToString(crop), createdAt = 10L))
        val expenseOutboxId = dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.EXPENSE, expense.id, SyncOperation.UPSERT, json.encodeToString(expense), createdAt = 20L))
        dao.insertOutbox(SyncOutboxEntity(0, SyncEntityType.NOTE, note.id, SyncOperation.UPSERT, json.encodeToString(note), createdAt = 30L))

        val firstRun = engine.processOutbox()

        assertEquals(SyncResult(syncedCount = 2, failedCount = 1), firstRun)
        assertEquals(listOf("CREATE_CROP:Миндаль", "CREATE_NOTE:t1"), api.callOrder)

        val remaining = dao.getPendingOutbox()
        assertEquals(1, remaining.size)
        val failedEntry = remaining.single()
        assertEquals(expenseOutboxId, failedEntry.id)
        assertEquals(SyncEntityType.EXPENSE, failedEntry.entityType)
        assertEquals(1, failedEntry.retryCount)
        assertEquals("Broken pipe while uploading expense", failedEntry.lastError)

        // Once network recovers, retrying flushes the remaining failed entry
        api.failOnExpenseError = null
        val secondRun = engine.processOutbox()
        assertEquals(SyncResult(syncedCount = 1, failedCount = 0), secondRun)
        assertTrue(dao.getPendingOutbox().isEmpty())
    }

    // --- SyncEngine: pullFromRemote ---

    @Test
    fun syncEngine_pullFromRemoteReplacesSnapshotAndMapsZoneMembershipOntoTrees(): Unit = runTest {
        val dao = FakeOrchardDao()
        // Pre-populate stale local entities to verify full replacement
        dao.insertCrop(CropEntity("stale-crop", "Старая", "", "5", "5"))
        dao.insertSchedule(ScheduleEntity("stale-sched", "stale-g", "stale-z", "2025-01-01T08:00", "2025-01-01T09:00"))

        val tree1 = Tree("t-1", row = 1, place = 1, status = TreeStatus.HEALTHY)
        val tree2 = Tree("t-2", row = 1, place = 2, status = TreeStatus.WARNING)
        val tree3 = Tree("t-3", row = 2, place = 1, status = TreeStatus.UNKNOWN)

        val remoteCrop = Crop("c-rem", "Яблоня", "Гала", "4.0", "2.0", "ЯБ")
        val remoteGarden = Garden(
            id = "g-rem",
            name = "Горный сад",
            cropId = "c-rem",
            areaHa = "0.8",
            plantingDate = "2026-04-10",
            placesPerRow = 2,
            initialTreeCount = 3,
            trees = listOf(tree1, tree2, tree3),
            finance = Finance(revenueCents = 900_000L, annualRevenueCents = 300_000L, annualCostsCents = 120_000L)
        )
        val remoteZoneA = IrrigationZone("z-A", "g-rem", "Капля Север", color = 1, treeIds = listOf("t-1", "t-3"))
        val remoteSchedule = IrrigationSchedule("s-rem", "g-rem", "z-A", "2026-11-18T07:00", "2026-11-18T08:30")
        val remoteExpense = Expense("e-rem", "g-rem", "Фильтр", "Оборудование", "1", "шт", "3500", 350_000L, "2026-11-15")

        val api = FakeOrchardApi().apply {
            remoteCrops = listOf(remoteCrop)
            remoteGardens = listOf(remoteGarden)
            remoteZonesByGarden = mapOf("g-rem" to listOf(remoteZoneA))
            remoteSchedulesByGarden = mapOf("g-rem" to listOf(remoteSchedule))
            remoteExpensesByGarden = mapOf("g-rem" to listOf(remoteExpense))
            remoteSettings = UserSettingsResponse(
                currentGardenId = "", // blank falls back to first remote garden ID
                printer = DemoPrinter("TSC-Alpha-3R")
            )
        }

        val engine = SyncEngine(dao, api, json)
        engine.pullFromRemote()

        assertEquals(1, dao.replaceSnapshotCallCount)
        assertEquals(listOf(remoteCrop.toEntity()), dao.cropsState.value)
        assertEquals(listOf(remoteGarden.toEntity()), dao.gardensState.value)
        assertEquals(listOf(remoteZoneA.toEntity()), dao.zonesState.value)
        assertEquals(listOf(remoteSchedule.toEntity()), dao.schedulesState.value)
        assertEquals(listOf(remoteExpense.toEntity()), dao.expensesState.value)

        // Verify TreeEntity.zoneId mapping: t-1 and t-3 -> z-A, t-2 -> null
        val treesById = dao.treesState.value.associateBy { it.id }
        assertEquals(3, treesById.size)
        assertEquals("z-A", treesById.getValue("t-1").zoneId)
        assertNull(treesById.getValue("t-2").zoneId)
        assertEquals("z-A", treesById.getValue("t-3").zoneId)
        assertEquals("g-rem", treesById.getValue("t-1").gardenId)

        // Verify UserSettingsEntity saved with fallback currentGardenId and printer device
        assertEquals(
            UserSettingsEntity(id = 1, currentGardenId = "g-rem", printerDevice = "TSC-Alpha-3R"),
            dao.getSettings()
        )
    }
}
