package com.smartorchard.mobile.data

import com.smartorchard.mobile.network.api.OrchardApi
import com.smartorchard.mobile.network.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Удалённый репозиторий: все операции идут через REST API.
 * Локальное состояние (кэш) обновляется после успешного ответа сервера.
 *
 * Для переключения на бэкенд замените в AppModule:
 *   provideRepository(...): OrchardRepository = MockRepository(context)
 * на:
 *   provideRepository(remote: RemoteRepository): OrchardRepository = remote
 */
@Singleton
class RemoteRepository @Inject constructor(
    private val api: OrchardApi
) : OrchardRepository {

    private val _data = MutableStateFlow(AppData(
        crops = emptyList(),
        gardens = emptyList(),
        currentGardenId = ""
    ))
    override val data: StateFlow<AppData> = _data.asStateFlow()

    /** Загрузить начальные данные с сервера. Вызывается после авторизации. */
    suspend fun sync() {
        val crops = api.getCrops().data
        val gardens = api.getGardens().data
        val settings = api.getUserSettings().data

        val zones = gardens.flatMap { g -> api.getZones(g.id).data }
        val schedules = gardens.flatMap { g -> api.getSchedules(g.id).data }
        val expenses = gardens.flatMap { g -> api.getExpenses(g.id).data }

        _data.value = AppData(
            crops = crops,
            gardens = gardens,
            currentGardenId = settings.currentGardenId.ifEmpty { gardens.firstOrNull()?.id ?: "" },
            zones = zones,
            schedules = schedules,
            expenses = expenses,
            printer = settings.printer
        )
    }

    override suspend fun setCurrentGarden(gardenId: String) {
        api.updateUserSettings(UserSettingsResponse(currentGardenId = gardenId, printer = _data.value.printer))
        _data.value = _data.value.copy(currentGardenId = gardenId)
    }

    override suspend fun addCrop(crop: Crop) {
        val created = api.createCrop(CreateCropRequest(
            name = crop.name, variety = crop.variety,
            rowSpacing = crop.rowSpacing, treeSpacing = crop.treeSpacing
        )).data
        _data.value = _data.value.copy(crops = _data.value.crops + created)
    }

    override suspend fun addGarden(garden: Garden) {
        val created = api.createGarden(CreateGardenRequest(
            name = garden.name, cropId = garden.cropId, areaHa = garden.areaHa,
            plantingDate = garden.plantingDate, placesPerRow = garden.placesPerRow
        )).data
        _data.value = _data.value.copy(
            gardens = _data.value.gardens + created,
            currentGardenId = created.id
        )
    }

    override suspend fun saveZone(zone: IrrigationZone) {
        val saved = api.createOrUpdateZone(zone.gardenId, CreateZoneRequest(
            id = zone.id, gardenId = zone.gardenId,
            name = zone.name, color = zone.color, treeIds = zone.treeIds
        )).data
        _data.value = _data.value.copy(
            zones = _data.value.zones.filterNot { it.id == saved.id } + saved
        )
    }

    override suspend fun addSchedule(schedule: IrrigationSchedule) {
        val created = api.createSchedule(schedule.gardenId, CreateScheduleRequest(
            gardenId = schedule.gardenId, zoneId = schedule.zoneId,
            start = schedule.start, end = schedule.end
        )).data
        _data.value = _data.value.copy(schedules = _data.value.schedules + created)
    }

    override suspend fun deleteSchedule(scheduleId: String) {
        api.deleteSchedule(scheduleId)
        _data.value = _data.value.copy(
            schedules = _data.value.schedules.filterNot { it.id == scheduleId }
        )
    }

    override suspend fun addExpense(expense: Expense) {
        val created = api.createExpense(expense.gardenId, CreateExpenseRequest(
            gardenId = expense.gardenId, title = expense.title, category = expense.category,
            quantity = expense.quantity, unit = expense.unit, price = expense.price, date = expense.date
        )).data
        _data.value = _data.value.copy(expenses = _data.value.expenses + created)
    }

    override suspend fun updateGardenFinance(gardenId: String, finance: Finance) {
        val updated = api.updateGardenFinance(gardenId, UpdateFinanceRequest(
            revenueCents = finance.revenueCents,
            annualRevenueCents = finance.annualRevenueCents,
            annualCostsCents = finance.annualCostsCents
        )).data
        _data.value = _data.value.copy(
            gardens = _data.value.gardens.map { if (it.id == gardenId) updated else it }
        )
    }

    override suspend fun addNote(note: TreeNote) {
        val created = api.createNote(note.treeId, CreateNoteRequest(
            treeId = note.treeId, text = note.text
        )).data
        _data.value = _data.value.copy(notes = _data.value.notes + created)
    }

    override suspend fun addObservation(observation: Observation, gardenId: String, treeId: String, newStatus: TreeStatus) {
        val created = api.createObservation(CreateObservationRequest(
            gardenId = gardenId, treeId = treeId, status = observation.status,
            comment = observation.comment, diagnosis = observation.diagnosis,
            recommendations = observation.recommendations,
            photoUrl = observation.photoPath, samplePhoto = observation.samplePhoto,
            originalAi = observation.originalAi
        )).data
        _data.value = _data.value.copy(
            observations = _data.value.observations + created,
            gardens = _data.value.gardens.map { g ->
                if (g.id == gardenId) g.copy(trees = g.trees.map { t ->
                    if (t.id == treeId) t.copy(status = newStatus) else t
                }) else g
            }
        )
    }

    override suspend fun updatePrinter(printer: DemoPrinter) {
        api.updateUserSettings(UserSettingsResponse(
            currentGardenId = _data.value.currentGardenId, printer = printer
        ))
        _data.value = _data.value.copy(printer = printer)
    }
}
