package com.smartorchard.mobile.state

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.services.DiagnosisService
import com.smartorchard.mobile.services.PhotoStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

data class UiState(
    val rowPage: Int = 0,
    val colPage: Int = 0,
    val boardZones: Boolean = false,
    val filter: TreeStatus? = null,
    val previewAt: String = DEMO_TIME,
    val offlineDemo: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val gardenDraft: GardenDraft = GardenDraft(),
    val cropDraft: CropDraft = CropDraft(),
    val cropReturnToGarden: Boolean = false,
    val zoneDraft: ZoneDraft = ZoneDraft(),
    val selectedTreeId: String? = null,
    val selectedZoneId: String? = null,
    val inspection: InspectionDraft? = null,
    val selectedObservationId: String? = null,
    val printerSearching: Boolean = false,
    val printerSearched: Boolean = false,
    val printerReturnToLabel: Boolean = false,
    val copies: String = "1"
)

sealed class NavigationEvent {
    data class NavigateTo(val route: String) : NavigationEvent()
    data object NavigateBack : NavigationEvent()
}

@HiltViewModel
class OrchardViewModel @Inject constructor(
    private val repository: MockRepository,
    private val diagnosisService: DiagnosisService,
    private val photos: PhotoStore
) : ViewModel() {

    val appData: StateFlow<AppData> = repository.state

    private val _uiState = MutableStateFlow(UiState(
        gardenDraft = GardenDraft(cropId = appData.value.crops.first().id)
    ))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents.asSharedFlow()

    private var analysisJob: Job? = null

    // Helper accessors using current state snapshots
    private val data get() = appData.value
    private val ui get() = _uiState.value

    val garden: Garden get() {
        val gardenId = data.currentGardenId
        return data.gardens.find { it.id == gardenId } ?: data.gardens.first()
    }
    val crop: Crop get() = data.crops.first { it.id == garden.cropId }
    val tree: Tree get() {
        val id = ui.selectedTreeId
        return garden.trees.find { it.id == id } ?: garden.trees.find { it.row == 3 && it.place == 5 } ?: garden.trees.first()
    }
    val zones get() = data.zones.filter { it.gardenId == garden.id }
    val schedules get() = data.schedules.filter { it.gardenId == garden.id }.sortedBy { it.start }
    val expenses get() = data.expenses.filter { it.gardenId == garden.id }
    val records get() = data.observations.filter { it.treeId == tree.id }.sortedByDescending { it.createdAt }

    // --- State update helpers ---
    fun updateRowPage(value: Int) { _uiState.update { it.copy(rowPage = value) } }
    fun updateColPage(value: Int) { _uiState.update { it.copy(colPage = value) } }
    fun updateBoardZones(value: Boolean) { _uiState.update { it.copy(boardZones = value) } }
    fun updateFilter(value: TreeStatus?) { _uiState.update { it.copy(filter = value) } }
    fun updatePreviewAt(value: String) { _uiState.update { it.copy(previewAt = value) } }
    fun updateOfflineDemo(value: Boolean) { _uiState.update { it.copy(offlineDemo = value) } }
    fun updateSelectedTreeId(value: String?) { _uiState.update { it.copy(selectedTreeId = value) } }
    fun updateSelectedZoneId(value: String?) { _uiState.update { it.copy(selectedZoneId = value) } }
    fun updateSelectedObservationId(value: String?) { _uiState.update { it.copy(selectedObservationId = value) } }
    fun updatePrinterReturnToLabel(value: Boolean) { _uiState.update { it.copy(printerReturnToLabel = value) } }
    fun updateCopies(value: String) { _uiState.update { it.copy(copies = value) } }
    fun updateCropDraft(value: CropDraft) { _uiState.update { it.copy(cropDraft = value) } }
    fun updateZoneDraft(value: ZoneDraft) { _uiState.update { it.copy(zoneDraft = value) } }
    fun updateInspection(value: InspectionDraft?) { _uiState.update { it.copy(inspection = value) } }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }
    fun clearFormError() { _uiState.update { it.copy(error = null) } }

    // --- Navigation ---
    fun navigateTo(route: String) {
        _uiState.update { it.copy(error = null) }
        viewModelScope.launch { _navigationEvents.emit(NavigationEvent.NavigateTo(route)) }
    }
    fun navigateBack() {
        viewModelScope.launch { _navigationEvents.emit(NavigationEvent.NavigateBack) }
    }

    fun notify(text: String) { _uiState.update { it.copy(message = text) } }
    fun fail(text: String) { _uiState.update { it.copy(error = text) } }

    fun openTree(id: String) { _uiState.update { it.copy(selectedTreeId = id) }; navigateTo(Route.TREE) }
    fun zoneOf(treeId: String) = zones.find { treeId in it.treeIds }
    fun watering(zone: IrrigationZone?) = zone != null && schedules.any { it.zoneId == zone.id && ui.previewAt >= it.start && ui.previewAt < it.end }
    fun currentZone() = zones.find { it.id == ui.selectedZoneId } ?: zones.firstOrNull()

    private fun save(success: String? = null, transform: (AppData) -> AppData, after: () -> Unit = {}) {
        if (ui.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { repository.update(transform); after(); if (success != null) notify(success) }
            catch (exception: Exception) { fail(exception.message ?: "Не удалось сохранить данные на устройстве.") }
            finally { _uiState.update { it.copy(busy = false) } }
        }
    }
    fun chooseGarden(id: String) = save(transform = { it.copy(currentGardenId = id) }) {
        _uiState.update { it.copy(selectedTreeId = null, rowPage = 0, colPage = 0, boardZones = false, filter = null) }
        navigateTo(Route.BOARD)
    }
    fun beginGarden() { _uiState.update { it.copy(gardenDraft = GardenDraft(cropId = data.crops.first().id)) }; navigateTo(Route.NEW_GARDEN) }
    fun beginCrop(fromGarden: Boolean) { _uiState.update { it.copy(cropReturnToGarden = fromGarden, cropDraft = CropDraft()) }; navigateTo(Route.NEW_CROP) }
    fun cancelCrop() = navigateTo(if (ui.cropReturnToGarden) Route.NEW_GARDEN else Route.CROPS)
    fun saveCrop() {
        val d = ui.cropDraft
        if (d.name.isBlank() || d.name.trim().length > 60 || d.variety.trim().length > 60) return fail("Укажите название культуры до 60 символов; сорт — до 60 символов.")
        if (decimal(d.row, 3)?.let { it > BigDecimal.ZERO } != true || decimal(d.tree, 3)?.let { it > BigDecimal.ZERO } != true) return fail("Укажите положительные расстояния, до трех знаков после запятой.")
        val item = Crop(uid(), d.name.trim(), d.variety.trim(), d.row.trim().replace(',', '.'), d.tree.trim().replace(',', '.'), mapOf("Миндаль" to "МД", "Яблоня" to "ЯБ", "Груша" to "ГР")[d.name.trim()] ?: "СД")
        if (data.crops.any { sameCrop(it, item) }) return fail("Такая культура с этим сортом и схемой посадки уже есть.")
        save("Культура создана", { it.copy(crops = it.crops + item) }) {
            if (ui.cropReturnToGarden) _uiState.update { it.copy(gardenDraft = it.gardenDraft.copy(cropId = item.id)) }
            cancelCrop()
        }
    }
    fun saveGarden() {
        val d = ui.gardenDraft; val chosen = data.crops.find { it.id == d.cropId } ?: return fail("Выберите культуру.")
        val estimate = plantingEstimate(d.areaHa, chosen) ?: return fail("Укажите положительную площадь до четырех знаков после запятой.")
        val p = d.places.toIntOrNull()
        if (d.name.isBlank()) return fail("Укажите название сада.")
        if (estimate.count !in 1..10000) return fail("Расчет должен дать от 1 до 10 000 деревьев.")
        if (p == null || p !in 1..minOf(100, estimate.count) || (estimate.count + p - 1) / p > 100) return fail("Задайте до 100 мест в полном ряду и не более 100 рядов.")
        if (!validDate(d.date) || d.date > DEMO_DATE) return fail("Укажите дату посадки не позже 29 октября 2026.")
        val item = newGarden(d.name, chosen, d.areaHa, d.date, p)
        save("Сад создан · ${item.trees.size} деревьев", { it.copy(gardens = it.gardens + item, currentGardenId = item.id) }) {
            _uiState.update { it.copy(selectedTreeId = item.trees.first().id, rowPage = 0, colPage = 0, filter = null, boardZones = false) }
            navigateTo(Route.BOARD)
        }
    }
    fun updateGardenDraft(draft: GardenDraft) {
        val chosen = data.crops.find { it.id == draft.cropId }
        val estimate = chosen?.let { plantingEstimate(draft.areaHa, it) }
        val resolved = if (!draft.placesManual && estimate != null && estimate.count in 1..10000) draft.copy(places = kotlin.math.ceil(kotlin.math.sqrt(estimate.count.toDouble())).toInt().toString()) else draft
        _uiState.update { it.copy(gardenDraft = resolved, error = null) }
    }
    fun beginZone(id: String? = null) {
        val zone = zones.find { it.id == id }
        _uiState.update { state -> state.copy(zoneDraft = zone?.let { ZoneDraft(it.id, it.name, it.color, it.treeIds) } ?: ZoneDraft(color = zones.size % 3)) }
        navigateTo(Route.ZONE_EDITOR)
    }
    fun toggleZoneCell(id: String) {
        val owner = zoneOf(id); if (owner != null && owner.id != ui.zoneDraft.id) return
        _uiState.update { it.copy(zoneDraft = it.zoneDraft.copy(treeIds = if (id in it.zoneDraft.treeIds) it.zoneDraft.treeIds - id else it.zoneDraft.treeIds + id), error = null) }
    }
    fun saveZone() {
        val d = ui.zoneDraft
        if (d.name.isBlank()) return fail("Укажите название зоны.")
        if (d.treeIds.isEmpty()) return fail("Выберите хотя бы одно дерево.")
        if (zones.any { it.id != d.id && normalized(it.name) == normalized(d.name) }) return fail("Зона с таким названием уже есть.")
        if (d.treeIds.any { id -> garden.trees.none { it.id == id } || zones.any { it.id != d.id && id in it.treeIds } }) return fail("Некоторые деревья недоступны или заняты другой зоной.")
        val item = IrrigationZone(d.id ?: uid(), garden.id, d.name.trim(), d.color, d.treeIds)
        save("Зона сохранена", { it.copy(zones = it.zones.filterNot { z -> z.id == item.id } + item) }) { _uiState.update { it.copy(selectedZoneId = item.id, boardZones = true) }; navigateTo(Route.ZONE_DETAIL) }
    }
    fun saveSchedule(zoneId: String, start: String, end: String) {
        if (zones.none { it.id == zoneId }) return fail("Выберите зону полива.")
        if (!validDateTime(start) || !validDateTime(end) || start >= end) return fail("Окончание должно быть позже начала.")
        if (schedules.any { it.zoneId == zoneId && overlaps(start, end, it) }) return fail("В этой зоне уже есть пересекающийся интервал.")
        val item = IrrigationSchedule(uid(), garden.id, zoneId, start, end)
        save("Интервал добавлен", { it.copy(schedules = it.schedules + item) }) { navigateTo(Route.IRRIGATION) }
    }
    fun deleteSchedule(id: String) = save("Интервал удален", { it.copy(schedules = it.schedules.filterNot { s -> s.id == id }) })
    fun saveExpense(title: String, category: String, quantity: String, unit: String, price: String, date: String) {
        if (title.isBlank() || unit.isBlank()) return fail("Укажите наименование и единицу измерения.")
        val cents = expenseCents(quantity, price) ?: return fail("Количество должно быть положительным, цена — неотрицательной.")
        if (!validDate(date) || date > DEMO_DATE) return fail("Укажите дату расхода не позже 29 октября 2026.")
        val item = Expense(uid(), garden.id, title.trim(), category, quantity, unit.trim(), price, cents, date)
        save("Затрата сохранена", { it.copy(expenses = it.expenses + item) }) { navigateTo(Route.EXPENSES) }
    }
    fun saveFinance(revenue: String, annualRevenue: String, annualCosts: String) {
        val values = listOf(revenue, annualRevenue, annualCosts).map { decimal(it, 2)?.movePointRight(2)?.let { n -> runCatching { n.longValueExact() }.getOrNull() } }
        if (values.any { it == null || it > 100_000_000_000_000L }) return fail("Введите неотрицательные суммы до двух знаков после запятой.")
        val f = Finance(values[0]!!, values[1]!!, values[2]!!); val id = garden.id
        save("Сценарный расчет обновлен", { it.copy(gardens = it.gardens.map { g -> if (g.id == id) g.copy(finance = f) else g }) })
    }
    fun addNote(text: String) {
        if (text.isBlank()) return fail("Введите текст заметки.")
        val item = TreeNote(uid(), tree.id, text.trim(), OffsetDateTime.now().toString())
        save("Заметка добавлена", { it.copy(notes = it.notes + item) })
    }
    fun beginInspection() {
        analysisJob?.cancel()
        _uiState.update { it.copy(inspection = InspectionDraft(uid(), tree.id, tree.status, tree.status)) }
        navigateTo(Route.OBSERVE)
    }
    fun useSamplePhoto() { _uiState.update { it.copy(inspection = it.inspection?.copy(samplePhoto = true, photoPath = null), error = null) } }
    fun cameraUri(): Uri = photos.cameraUri()
    fun importPhoto(uri: Uri) {
        val id = ui.inspection?.id ?: return
        viewModelScope.launch {
            try { val path = photos.import(uri); if (_uiState.value.inspection?.id == id) { _uiState.update { it.copy(inspection = it.inspection?.copy(photoPath = path, samplePhoto = false), error = null) } } }
            catch (exception: Exception) { fail(exception.message ?: "Не удалось загрузить фото.") }
        }
    }
    fun analyze() {
        val d = ui.inspection ?: return
        if (d.photoPath == null && !d.samplePhoto) return fail("Добавьте фотографию перед анализом.")
        if (ui.offlineDemo) return fail("Для анализа требуется подключение. В макете включен режим без сети.")
        _uiState.update { it.copy(error = null, inspection = d.copy(phase = AnalysisPhase.PROCESSING)) }
        navigateTo(Route.RESULT)
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            try {
                val result = diagnosisService.analyze(d.photoPath, d.samplePhoto)
                if (_uiState.value.inspection?.id == d.id && _uiState.value.inspection?.phase == AnalysisPhase.PROCESSING) {
                    if (_uiState.value.offlineDemo) { _uiState.update { it.copy(inspection = it.inspection?.copy(phase = AnalysisPhase.DRAFT)) }; navigateTo(Route.OBSERVE); fail("Анализ прерван: нет сети.") }
                    else _uiState.update { it.copy(inspection = it.inspection?.copy(phase = AnalysisPhase.RESULT, diagnosis = result.diagnosis, recommendations = result.recommendations, originalAi = result)) }
                }
            } catch (_: kotlinx.coroutines.CancellationException) { }
            catch (exception: Exception) { if (_uiState.value.inspection?.id == d.id) { _uiState.update { it.copy(inspection = it.inspection?.copy(phase = AnalysisPhase.DRAFT)) }; navigateTo(Route.OBSERVE); fail(exception.message ?: "Ошибка анализа.") } }
        }
    }
    fun backToInspection() { analysisJob?.cancel(); _uiState.update { it.copy(inspection = it.inspection?.copy(phase = AnalysisPhase.DRAFT)) }; navigateTo(Route.OBSERVE) }
    fun saveInspection(withAi: Boolean) {
        val d = ui.inspection ?: return
        val originalTree = garden.trees.find { it.id == d.treeId } ?: return fail("Дерево недоступно.")
        if (withAi && (d.originalAi == null || d.diagnosis.isBlank() || d.recommendations.isBlank())) return fail("Заполните предполагаемый диагноз и рекомендации.")
        if (!withAi && d.comment.isBlank() && d.photoPath == null && !d.samplePhoto && d.status == d.previousStatus) return fail("Добавьте фото, наблюдение или измените состояние.")
        val record = Observation(uid(), garden.id, d.treeId, OffsetDateTime.now().toString(), d.status, d.comment.trim(),
            if (withAi) d.diagnosis.trim() else "", if (withAi) d.recommendations.trim() else "", d.photoPath, d.samplePhoto, if (withAi) d.originalAi else null)
        save("Осмотр сохранен для дерева", { current -> current.copy(observations = current.observations + record,
            gardens = current.gardens.map { g -> if (g.id == garden.id) g.copy(trees = g.trees.map { t -> if (t.id == originalTree.id) t.copy(status = d.status) else t }) else g }) }) {
            _uiState.update { it.copy(selectedTreeId = d.treeId, inspection = null) }; navigateTo(Route.TREE)
        }
    }
    fun lookup(row: String, place: String) {
        val found = garden.trees.find { it.row == row.toIntOrNull() && it.place == place.toIntOrNull() } ?: return fail("По указанным ряду и месту дерева нет.")
        openTree(found.id)
    }
    fun openQr(value: String) {
        val id = parseQr(value) ?: return fail("Некорректная метка SmartOrchard.")
        val g = data.gardens.find { garden -> garden.trees.any { it.id == id } } ?: return fail("Дерево не найдено в локальном наборе данных.")
        save(transform = { it.copy(currentGardenId = g.id) }) { openTree(id) }
    }
    fun searchPrinters() {
        if (ui.printerSearching) return
        _uiState.update { it.copy(printerSearching = true, error = null) }
        viewModelScope.launch { delay(700.milliseconds); _uiState.update { it.copy(printerSearching = false, printerSearched = true) }; notify("Найдены демонстрационные устройства") }
    }
    fun connectPrinter(name: String) = save("Подключен демопринтер", { it.copy(printer = DemoPrinter(name)) })
    fun disconnectPrinter() = save("Демопринтер отключен", { it.copy(printer = DemoPrinter()) })
    fun demoPrint() {
        val count = ui.copies.toIntOrNull()
        if (count == null || count !in 1..12) return fail("Укажите от 1 до 12 копий.")
        if (data.printer.device == null) { _uiState.update { it.copy(printerReturnToLabel = true) }; navigateTo(Route.PRINTER); return }
        notify("Демо: отправлено ${ui.copies.toInt()} этикеток. Физическая печать не выполнялась.")
    }
}
