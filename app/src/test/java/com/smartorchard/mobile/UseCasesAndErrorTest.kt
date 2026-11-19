package com.smartorchard.mobile

import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.domain.usecase.*
import com.smartorchard.mobile.services.DiagnosisService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class UseCasesAndErrorTest {

    private val fixedInstant: Instant = Instant.parse("2026-11-18T10:00:00Z")
    private val fixedClock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

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

    private class FakeDiagnosisService(
        private val result: AiResult = AiResult(
            diagnosis = "Монилиальный ожог",
            recommendations = "Санитарная обрезка пораженных побегов"
        )
    ) : DiagnosisService {
        var lastPhotoPath: String? = null
        var lastSampleFlag: Boolean? = null
        var callCount: Int = 0

        override suspend fun analyze(photoPath: String?, sample: Boolean): AiResult {
            callCount++
            lastPhotoPath = photoPath
            lastSampleFlag = sample
            return result
        }
    }

    private suspend fun assertValidationFails(block: suspend () -> Unit): ValidationException {
        try {
            block()
            fail("Ожидалось исключение ValidationException")
        } catch (e: ValidationException) {
            return e
        }
        throw AssertionError("Unreachable")
    }

    // --- 1. SaveCropUseCase ---

    @Test
    fun saveCropUseCase_createsCropNormalizesCommasAndMapsPrefixes(): Unit = runTest {
        val repo = FakeOrchardRepository(
            AppData(crops = emptyList(), gardens = emptyList(), currentGardenId = "")
        )
        val useCase = SaveCropUseCase(repo)

        val almond = useCase(CropDraft("  Миндаль ", " Никитский ", "6,0", "4,25"), repo.data.value.crops)
        val apple = useCase(CropDraft("Яблоня", "Гала", "4.5", "2.125"), repo.data.value.crops)
        val pear = useCase(CropDraft("Груша", "Конференция", "5", "3"), repo.data.value.crops)
        val custom = useCase(CropDraft("Черешня", "Кордия", "5,5", "3.5"), repo.data.value.crops)

        assertEquals("МД", almond.prefix)
        assertEquals("Миндаль", almond.name)
        assertEquals("Никитский", almond.variety)
        assertEquals("6.0", almond.rowSpacing)
        assertEquals("4.25", almond.treeSpacing)
        assertEquals("ЯБ", apple.prefix)
        assertEquals("ГР", pear.prefix)
        assertEquals("СД", custom.prefix)
        assertEquals("5.5", custom.rowSpacing)
        assertEquals(4, repo.data.value.crops.size)
    }

    @Test
    fun saveCropUseCase_enforcesDuplicatesLengthAndScaleBounds(): Unit = runTest {
        val repo = FakeOrchardRepository()
        val useCase = SaveCropUseCase(repo)
        val existing = repo.data.value.crops.first()

        // Duplicate with different casing and comma/trailing-zero formatting
        val dupDraft = CropDraft(
            name = " ${existing.name.lowercase()} ",
            variety = existing.variety.uppercase(),
            row = "${existing.rowSpacing},00",
            tree = existing.treeSpacing
        )
        val dupError = assertValidationFails { useCase(dupDraft, repo.data.value.crops) }
        assertTrue(dupError.message.contains("уже есть"))

        // Exact 60-char name and 60-char variety are accepted
        val maxLenName = "К".repeat(60)
        val maxLenVariety = "С".repeat(60)
        val boundaryCrop = useCase(
            CropDraft(maxLenName, maxLenVariety, "4.125", "2.375"),
            repo.data.value.crops
        )
        assertEquals(60, boundaryCrop.name.length)
        assertEquals(60, boundaryCrop.variety.length)

        // Blank or >60 char name / variety rejected
        assertValidationFails { useCase(CropDraft("   ", "Стенли", "4", "4"), repo.data.value.crops) }
        assertValidationFails { useCase(CropDraft("К".repeat(61), "Стенли", "4", "4"), repo.data.value.crops) }
        assertValidationFails { useCase(CropDraft("Слива", "С".repeat(61), "4", "4"), repo.data.value.crops) }

        // Zero, negative, or >3 decimal places spacings rejected
        assertValidationFails { useCase(CropDraft("Слива", "Стенли", "0", "4"), repo.data.value.crops) }
        assertValidationFails { useCase(CropDraft("Слива", "Стенли", "4", "0"), repo.data.value.crops) }
        assertValidationFails { useCase(CropDraft("Слива", "Стенли", "-3", "4"), repo.data.value.crops) }
        assertValidationFails { useCase(CropDraft("Слива", "Стенли", "4.1234", "4"), repo.data.value.crops) }
        assertValidationFails { useCase(CropDraft("Слива", "Стенли", "4", "2,9876"), repo.data.value.crops) }
    }

    // --- 2. CreateGardenUseCase ---

    @Test
    fun createGardenUseCase_withFixedClockValidatesDatesAreaRowsAndTreeBounds(): Unit = runTest {
        val denseCrop = Crop("c-dense", "Яблоня", "Суперкарлик", "1", "1", "ЯБ")
        val repo = FakeOrchardRepository(
            AppData(crops = listOf(denseCrop), gardens = emptyList(), currentGardenId = "")
        )
        val useCase = CreateGardenUseCase(repo, fixedClock)

        // Accepts exact clock date 2026-11-18
        val created = useCase(
            draft = GardenDraft(
                name = "  Южный склон  ",
                cropId = denseCrop.id,
                areaHa = "0,02",
                date = "2026-11-18",
                places = "20",
                placesManual = true
            ),
            crops = repo.data.value.crops
        )
        assertEquals("Южный склон", created.name)
        assertEquals("0.02", created.areaHa)
        assertEquals("2026-11-18", created.plantingDate)
        assertEquals(200, created.trees.size)
        assertEquals(created.id, repo.data.value.currentGardenId)

        // Rejects tomorrow 2026-11-19 relative to fixedClock
        val futureError = assertValidationFails {
            useCase(
                draft = GardenDraft(
                    name = "Будущий сад",
                    cropId = denseCrop.id,
                    areaHa = "0.02",
                    date = "2026-11-19",
                    places = "20"
                ),
                crops = repo.data.value.crops
            )
        }
        assertTrue(futureError.message.contains("18 ноября 2026"))

        // Rejects missing crop and blank name
        assertValidationFails {
            useCase(
                GardenDraft(name = "Сад", cropId = "missing", areaHa = "0.02", date = "2026-11-18", places = "10"),
                repo.data.value.crops
            )
        }
        assertValidationFails {
            useCase(
                GardenDraft(name = "   ", cropId = denseCrop.id, areaHa = "0.02", date = "2026-11-18", places = "10"),
                repo.data.value.crops
            )
        }

        // Rejects invalid area (zero, negative, >4 decimals, or 0 estimated trees)
        assertValidationFails {
            useCase(
                GardenDraft(name = "Сад", cropId = denseCrop.id, areaHa = "0", date = "2026-11-18", places = "10"),
                repo.data.value.crops
            )
        }
        assertValidationFails {
            useCase(
                GardenDraft(name = "Сад", cropId = denseCrop.id, areaHa = "-0.5", date = "2026-11-18", places = "10"),
                repo.data.value.crops
            )
        }
        assertValidationFails {
            useCase(
                GardenDraft(name = "Сад", cropId = denseCrop.id, areaHa = "0.12345", date = "2026-11-18", places = "10"),
                repo.data.value.crops
            )
        }

        // Rejects >100 rows (200 trees with 1 place per row -> 200 rows) and >100 places per row
        val rowsError = assertValidationFails {
            useCase(
                GardenDraft(name = "Узкий сад", cropId = denseCrop.id, areaHa = "0.02", date = "2026-11-18", places = "1"),
                repo.data.value.crops
            )
        }
        assertTrue(rowsError.message.contains("не более 100 рядов"))

        assertValidationFails {
            useCase(
                GardenDraft(name = "Широкий сад", cropId = denseCrop.id, areaHa = "0.05", date = "2026-11-18", places = "101"),
                repo.data.value.crops
            )
        }

        // Rejects >10,000 trees (1.0001 ha at 1m x 1m = 10,001 trees)
        val tooManyTreesError = assertValidationFails {
            useCase(
                GardenDraft(name = "Огромный сад", cropId = denseCrop.id, areaHa = "1.0001", date = "2026-11-18", places = "100"),
                repo.data.value.crops
            )
        }
        assertTrue(tooManyTreesError.message.contains("10 000"))
    }

    // --- 3. SaveZoneUseCase ---

    @Test
    fun saveZoneUseCase_createsUpdatesAndEnforcesUniqueNameAndTreeOwnership(): Unit = runTest {
        val crop = Crop("c1", "Яблоня", "Фуджи", "4", "2", "ЯБ")
        val garden = newGarden("Тестовый сад", crop, "0.01", DEMO_DATE, 5)
        val repo = FakeOrchardRepository(
            AppData(crops = listOf(crop), gardens = listOf(garden), currentGardenId = garden.id)
        )
        val useCase = SaveZoneUseCase(repo)

        // Blank name & empty treeIds rejected
        assertValidationFails {
            useCase(ZoneDraft(name = "  ", treeIds = listOf(garden.trees[0].id)), garden, emptyList())
        }
        assertValidationFails {
            useCase(ZoneDraft(name = "Зона", treeIds = emptyList()), garden, emptyList())
        }

        // Create new zone
        val zoneA = useCase(
            draft = ZoneDraft(name = " Сектор А ", color = 1, treeIds = listOf(garden.trees[0].id, garden.trees[1].id)),
            garden = garden,
            gardenZones = repo.data.value.zones
        )
        assertEquals("Сектор А", zoneA.name)
        assertEquals(1, repo.data.value.zones.size)

        // Duplicate zone name (case & whitespace normalized) rejected
        val dupNameError = assertValidationFails {
            useCase(
                draft = ZoneDraft(name = "  сектор   а ", color = 2, treeIds = listOf(garden.trees[2].id)),
                garden = garden,
                gardenZones = repo.data.value.zones
            )
        }
        assertTrue(dupNameError.message.contains("названием уже есть"))

        // Tree already owned by another zone rejected
        val overlapTreeError = assertValidationFails {
            useCase(
                draft = ZoneDraft(name = "Сектор Б", color = 2, treeIds = listOf(garden.trees[1].id, garden.trees[2].id)),
                garden = garden,
                gardenZones = repo.data.value.zones
            )
        }
        assertTrue(overlapTreeError.message.contains("заняты другой зоной"))

        // Foreign tree ID not belonging to garden rejected
        assertValidationFails {
            useCase(
                draft = ZoneDraft(name = "Сектор В", color = 3, treeIds = listOf("foreign-tree-id")),
                garden = garden,
                gardenZones = repo.data.value.zones
            )
        }

        // Update existing zone keeping its own trees and adding an unassigned tree succeeds
        val updatedZoneA = useCase(
            draft = ZoneDraft(
                id = zoneA.id,
                name = "Сектор А",
                color = 4,
                treeIds = listOf(garden.trees[0].id, garden.trees[1].id, garden.trees[2].id)
            ),
            garden = garden,
            gardenZones = repo.data.value.zones
        )
        assertEquals(zoneA.id, updatedZoneA.id)
        assertEquals(4, updatedZoneA.color)
        assertEquals(3, updatedZoneA.treeIds.size)
        assertEquals(1, repo.data.value.zones.size)
    }

    // --- 4. SaveScheduleUseCase ---

    @Test
    fun saveScheduleUseCase_acceptsValidAndAdjoiningAndRejectsOverlapInvalidRangeOrMissingZone(): Unit = runTest {
        val repo = FakeOrchardRepository()
        val useCase = SaveScheduleUseCase(repo)
        val gardenId = repo.data.value.currentGardenId
        val zones = repo.data.value.zones.filter { it.gardenId == gardenId }
        val zone = zones.first()

        // Valid schedule
        val first = useCase(
            gardenId = gardenId,
            zoneId = zone.id,
            start = "2026-11-18T08:00",
            end = "2026-11-18T10:00",
            gardenZones = zones,
            gardenSchedules = emptyList()
        )
        assertTrue(repo.data.value.schedules.any { it.id == first.id })

        // Adjoining schedule (start == previous end) succeeds
        val adjoiningAfter = useCase(
            gardenId = gardenId,
            zoneId = zone.id,
            start = "2026-11-18T10:00",
            end = "2026-11-18T11:30",
            gardenZones = zones,
            gardenSchedules = listOf(first)
        )
        assertTrue(repo.data.value.schedules.any { it.id == adjoiningAfter.id })

        // Overlapping schedule in same zone rejected
        val overlapError = assertValidationFails {
            useCase(
                gardenId = gardenId,
                zoneId = zone.id,
                start = "2026-11-18T09:30",
                end = "2026-11-18T10:30",
                gardenZones = zones,
                gardenSchedules = listOf(first, adjoiningAfter)
            )
        }
        assertTrue(overlapError.message.contains("пересекающийся"))

        // Reject start >= end (equal and inverted)
        val equalRangeError = assertValidationFails {
            useCase(
                gardenId = gardenId,
                zoneId = zone.id,
                start = "2026-11-18T12:00",
                end = "2026-11-18T12:00",
                gardenZones = zones,
                gardenSchedules = emptyList()
            )
        }
        assertTrue(equalRangeError.message.contains("позже начала"))

        assertValidationFails {
            useCase(
                gardenId = gardenId,
                zoneId = zone.id,
                start = "2026-11-18T14:00",
                end = "2026-11-18T13:00",
                gardenZones = zones,
                gardenSchedules = emptyList()
            )
        }

        // Reject missing zone
        val missingZoneError = assertValidationFails {
            useCase(
                gardenId = gardenId,
                zoneId = "missing-zone-id",
                start = "2026-11-18T15:00",
                end = "2026-11-18T16:00",
                gardenZones = zones,
                gardenSchedules = emptyList()
            )
        }
        assertTrue(missingZoneError.message.contains("зону полива"))
    }

    // --- 5. SaveExpenseUseCase ---

    @Test
    fun saveExpenseUseCase_withFixedClockCalculatesExactCentsAndValidatesInputs(): Unit = runTest {
        val repo = FakeOrchardRepository()
        val useCase = SaveExpenseUseCase(repo, fixedClock)
        val gardenId = repo.data.value.currentGardenId

        // Exact cents calculation (0.5 * 2.01 = 1.005 -> HALF_UP -> 1.01 = 101 cents) on today's date
        val saved = useCase(
            gardenId = gardenId,
            title = "  Капельная лента  ",
            category = "Оборудование",
            quantity = "0,5",
            unit = "  бухта ",
            price = "2,01",
            date = "2026-11-18"
        )
        assertEquals("Капельная лента", saved.title)
        assertEquals("бухта", saved.unit)
        assertEquals(101L, saved.totalCents)
        assertEquals("2026-11-18", saved.date)
        assertTrue(repo.data.value.expenses.any { it.id == saved.id })

        // Rejects tomorrow's date 2026-11-19
        val tomorrowError = assertValidationFails {
            useCase(
                gardenId = gardenId,
                title = "Удобрения",
                category = "Материалы",
                quantity = "10",
                unit = "кг",
                price = "150",
                date = "2026-11-19"
            )
        }
        assertTrue(tomorrowError.message.contains("18 ноября 2026"))

        // Rejects blank title, blank unit, zero/negative quantity, or negative price
        assertValidationFails {
            useCase(gardenId, "   ", "Материалы", "5", "кг", "100", "2026-11-18")
        }
        assertValidationFails {
            useCase(gardenId, "Селитры", "Материалы", "5", "   ", "100", "2026-11-18")
        }
        assertValidationFails {
            useCase(gardenId, "Селитры", "Материалы", "0", "кг", "100", "2026-11-18")
        }
        assertValidationFails {
            useCase(gardenId, "Селитры", "Материалы", "-2", "кг", "100", "2026-11-18")
        }
        assertValidationFails {
            useCase(gardenId, "Селитры", "Материалы", "2", "кг", "-10", "2026-11-18")
        }
    }

    // --- 6. UpdateFinanceUseCase ---

    @Test
    fun updateFinanceUseCase_convertsDecimalsToCentsAndRejectsNegativeOrExcessiveScale(): Unit = runTest {
        val repo = FakeOrchardRepository()
        val useCase = UpdateFinanceUseCase(repo)
        val gardenId = repo.data.value.currentGardenId

        val updated = useCase(
            gardenId = gardenId,
            revenue = "12500,50",
            annualRevenue = "4000",
            annualCosts = "0.05"
        )
        assertEquals(Finance(1_250_050L, 400_000L, 5L), updated)
        assertEquals(updated, repo.data.value.gardens.first { it.id == gardenId }.finance)

        // Rejects negative numbers and >2 decimal places
        assertValidationFails { useCase(gardenId, "-1", "100", "50") }
        assertValidationFails { useCase(gardenId, "100", "-0.01", "50") }
        assertValidationFails { useCase(gardenId, "100.123", "100", "50") }
        assertValidationFails { useCase(gardenId, "100", "100", "50,999") }
        assertValidationFails { useCase(gardenId, "", "100", "50") }
    }

    // --- 7. AddTreeNoteUseCase ---

    @Test
    fun addTreeNoteUseCase_withFixedClockTrimsTextRecordsExactTimestampAndRejectsBlank(): Unit = runTest {
        val repo = FakeOrchardRepository()
        val useCase = AddTreeNoteUseCase(repo, fixedClock)
        val treeId = repo.data.value.gardens.first().trees.first().id

        val note = useCase(treeId, "  Проверить прививку весной  ")
        assertEquals("Проверить прививку весной", note.text)
        assertEquals(treeId, note.treeId)
        assertEquals(OffsetDateTime.now(fixedClock).toString(), note.createdAt)
        assertEquals(fixedInstant, OffsetDateTime.parse(note.createdAt).toInstant())
        assertTrue(repo.data.value.notes.any { it.id == note.id })

        assertValidationFails { useCase(treeId, "") }
        assertValidationFails { useCase(treeId, "   \n\t  ") }
    }

    // --- 8. SaveObservationUseCase ---

    @Test
    fun saveObservationUseCase_withFixedClockValidatesModesPreservesOriginalAiAndUpdatesStatus(): Unit = runTest {
        val repo = FakeOrchardRepository()
        val useCase = SaveObservationUseCase(repo, fixedClock)
        val garden = repo.data.value.gardens.first { it.id == repo.data.value.currentGardenId }
        val targetTree = garden.trees.first()
        val expectedTimestamp = OffsetDateTime.now(fixedClock).toString()

        // Unknown tree rejected
        assertValidationFails {
            useCase(
                draft = InspectionDraft("d0", "unknown-tree", TreeStatus.HEALTHY, TreeStatus.WARNING, comment = "Осмотр"),
                garden = garden,
                withAi = false
            )
        }

        // Empty manual draft with unchanged status rejected
        assertValidationFails {
            useCase(
                draft = InspectionDraft(
                    id = "d1",
                    treeId = targetTree.id,
                    previousStatus = targetTree.status,
                    status = targetTree.status
                ),
                garden = garden,
                withAi = false
            )
        }

        // Valid manual observation records fixed timestamp and updates tree status
        val manualObs = useCase(
            draft = InspectionDraft(
                id = "d2",
                treeId = targetTree.id,
                previousStatus = targetTree.status,
                status = TreeStatus.WARNING,
                comment = "  Пожелтение листьев  "
            ),
            garden = garden,
            withAi = false
        )
        assertEquals(expectedTimestamp, manualObs.createdAt)
        assertEquals("Пожелтение листьев", manualObs.comment)
        assertEquals("", manualObs.diagnosis)
        assertNull(manualObs.originalAi)
        val updatedTreeAfterManual = repo.data.value.gardens
            .first { it.id == garden.id }
            .trees.first { it.id == targetTree.id }
        assertEquals(TreeStatus.WARNING, updatedTreeAfterManual.status)

        // AI mode without originalAi or blank diagnosis/recommendations rejected
        assertValidationFails {
            useCase(
                draft = InspectionDraft(
                    id = "d3",
                    treeId = targetTree.id,
                    previousStatus = TreeStatus.WARNING,
                    status = TreeStatus.WARNING,
                    samplePhoto = true,
                    diagnosis = "Парша",
                    recommendations = "Обработка",
                    originalAi = null
                ),
                garden = repo.data.value.gardens.first { it.id == garden.id },
                withAi = true
            )
        }
        assertValidationFails {
            useCase(
                draft = InspectionDraft(
                    id = "d3b",
                    treeId = targetTree.id,
                    previousStatus = TreeStatus.WARNING,
                    status = TreeStatus.WARNING,
                    samplePhoto = true,
                    diagnosis = "   ",
                    recommendations = "Обработка",
                    originalAi = AiResult("ИИ", "Рек")
                ),
                garden = repo.data.value.gardens.first { it.id == garden.id },
                withAi = true
            )
        }

        // Valid AI observation preserves originalAi alongside edited diagnosis and updates tree status
        val ai = AiResult("Исходный ИИ диагноз", "Исходная рекомендация")
        val aiObs = useCase(
            draft = InspectionDraft(
                id = "d4",
                treeId = targetTree.id,
                previousStatus = TreeStatus.WARNING,
                status = TreeStatus.HEALTHY,
                samplePhoto = true,
                diagnosis = "  Уточненный диагноз агронома ",
                recommendations = " Профилактический полив ",
                originalAi = ai
            ),
            garden = repo.data.value.gardens.first { it.id == garden.id },
            withAi = true
        )
        assertEquals(expectedTimestamp, aiObs.createdAt)
        assertEquals("Уточненный диагноз агронома", aiObs.diagnosis)
        assertEquals("Профилактический полив", aiObs.recommendations)
        assertEquals(ai, aiObs.originalAi)
        val updatedTreeAfterAi = repo.data.value.gardens
            .first { it.id == garden.id }
            .trees.first { it.id == targetTree.id }
        assertEquals(TreeStatus.HEALTHY, updatedTreeAfterAi.status)
    }

    // --- 9. AnalyzePhotoUseCase ---

    @Test
    fun analyzePhotoUseCase_succeedsOnlineWithPhotoOrSampleAndRejectsMissingPhotoOrOffline(): Unit = runTest {
        val fakeService = FakeDiagnosisService()
        val useCase = AnalyzePhotoUseCase(fakeService)

        // Succeeds online with samplePhoto
        val sampleDraft = InspectionDraft(
            id = "a1",
            treeId = "t1",
            previousStatus = TreeStatus.UNKNOWN,
            status = TreeStatus.UNKNOWN,
            photoPath = null,
            samplePhoto = true
        )
        val resSample = useCase(sampleDraft, isOffline = false)
        assertEquals("Монилиальный ожог", resSample.diagnosis)
        assertEquals(null, fakeService.lastPhotoPath)
        assertEquals(true, fakeService.lastSampleFlag)

        // Succeeds online with file photoPath
        val fileDraft = sampleDraft.copy(photoPath = "/data/photos/leaf1.jpg", samplePhoto = false)
        val resFile = useCase(fileDraft, isOffline = false)
        assertEquals("Монилиальный ожог", resFile.diagnosis)
        assertEquals("/data/photos/leaf1.jpg", fakeService.lastPhotoPath)
        assertEquals(false, fakeService.lastSampleFlag)

        // Rejects missing photo (neither photoPath nor samplePhoto)
        val noPhotoDraft = sampleDraft.copy(photoPath = null, samplePhoto = false)
        val noPhotoError = assertValidationFails { useCase(noPhotoDraft, isOffline = false) }
        assertTrue(noPhotoError.message.contains("фотографию"))

        // Rejects offline even when photo is present
        val offlineError = assertValidationFails { useCase(fileDraft, isOffline = true) }
        assertTrue(offlineError.message.contains("подключение"))
        assertEquals(2, fakeService.callCount)
    }

    // --- 10. FindTreeUseCase ---

    @Test
    fun findTreeUseCase_looksUpCoordinatesOpensByQrAndRejectsInvalidOrUnknownQr(): Unit = runTest {
        val repo = FakeOrchardRepository()
        val useCase = FindTreeUseCase(repo)
        val firstGarden = repo.data.value.gardens.first()
        val secondGarden = repo.data.value.gardens.last()
        assertNotEquals(firstGarden.id, secondGarden.id)

        // Coordinate lookup
        val expectedTree = firstGarden.trees.first { it.row == 1 && it.place == 1 }
        assertEquals(expectedTree.id, useCase.byCoordinates(firstGarden, "1", "1").id)

        assertValidationFails { useCase.byCoordinates(firstGarden, "999", "999") }
        assertValidationFails { useCase.byCoordinates(firstGarden, "abc", "1") }

        // Open by QR switches currentGardenId to the garden containing that tree
        repo.setCurrentGarden(firstGarden.id)
        val remoteTree = secondGarden.trees.first()
        val foundId = useCase.openByQr(repo.data.value.gardens, qrPayload(remoteTree))
        assertEquals(remoteTree.id, foundId)
        assertEquals(secondGarden.id, repo.data.value.currentGardenId)

        // Invalid QR payload rejected
        val badQr = assertValidationFails {
            useCase.openByQr(repo.data.value.gardens, "https://evil.example/tree/123")
        }
        assertEquals("Некорректная метка SmartOrchard.", badQr.message)

        // Unknown valid UUID QR rejected
        val missingTree = Tree(uid(), 1, 1)
        val missingQr = assertValidationFails {
            useCase.openByQr(repo.data.value.gardens, qrPayload(missingTree))
        }
        assertEquals("Дерево не найдено в локальном наборе данных.", missingQr.message)
    }

    // --- 11. AppError / toUserMessage ---

    private fun httpException(code: Int): HttpException = HttpException(
        Response.error<Any>(code, """{"error":"fail"}""".toResponseBody("application/json".toMediaType()))
    )

    @Test
    fun appError_mapsAllExceptionCategoriesAndHidesRawJavaClassNames() {
        // ValidationException
        val validationMsg = "Укажите положительную площадь до четырех знаков после запятой."
        val validationEx = ValidationException(validationMsg)
        assertTrue(validationEx.toAppError() is AppError.Validation)
        assertEquals(validationMsg, validationEx.toUserMessage())

        // UnknownHostException & ConnectException -> NetworkOffline
        val unknownHostEx = UnknownHostException("Unable to resolve host api.smartorchard.ru")
        assertTrue(unknownHostEx.toAppError() is AppError.NetworkOffline)
        assertTrue(unknownHostEx.toUserMessage().contains("Нет подключения к интернету"))
        assertFalse(unknownHostEx.toUserMessage().contains("UnknownHostException"))

        val connectEx = ConnectException("Failed to connect to /10.0.2.2:8080")
        assertTrue(connectEx.toAppError() is AppError.NetworkOffline)
        assertTrue(connectEx.toUserMessage().contains("Нет подключения к интернету"))

        // SocketTimeoutException -> Timeout
        val timeoutEx = SocketTimeoutException("Read timed out")
        assertTrue(timeoutEx.toAppError() is AppError.Timeout)
        assertEquals("Превышено время ожидания ответа от сервера.", timeoutEx.toUserMessage())

        // HttpException 401 & 403 -> Unauthorized
        val http401 = httpException(401)
        assertTrue(http401.toAppError() is AppError.Unauthorized)
        assertEquals("Сессия истекла. Войдите в аккаунт повторно.", http401.toUserMessage())

        val http403 = httpException(403)
        assertTrue(http403.toAppError() is AppError.Unauthorized)
        assertEquals("Сессия истекла. Войдите в аккаунт повторно.", http403.toUserMessage())

        // HttpException 503 -> Server(503)
        val http503 = httpException(503)
        val serverErr = http503.toAppError()
        assertTrue(serverErr is AppError.Server)
        assertEquals(503, (serverErr as AppError.Server).code)
        assertEquals("Ошибка сервера (503). Попробуйте позже.", http503.toUserMessage())

        // Generic IOException -> NetworkOffline I/O message
        val ioEx = IOException("Connection reset by peer")
        assertTrue(ioEx.toAppError() is AppError.NetworkOffline)
        assertEquals("Ошибка ввода-вывода или отсутствует сеть.", ioEx.toUserMessage())

        // Generic RuntimeException containing Java Exception class name -> hides it, uses defaultMessage
        val leakedEx = RuntimeException("java.lang.NullPointerException: Attempt to invoke virtual method")
        val unknownErr = leakedEx.toAppError(defaultMessage = "Операция временно недоступна.")
        assertTrue(unknownErr is AppError.Unknown)
        assertEquals("Операция временно недоступна.", leakedEx.toUserMessage("Операция временно недоступна."))
        assertFalse(leakedEx.toUserMessage().contains("NullPointerException"))
    }
}
