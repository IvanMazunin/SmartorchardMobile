package com.smartorchard.mobile.state

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.services.DemoDiagnosisService
import com.smartorchard.mobile.services.PhotoStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.OffsetDateTime

class OrchardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MockRepository(application)
    private val diagnosisService = DemoDiagnosisService()
    private val photos = PhotoStore(application)
    val state = repository.state
    val data get() = state.value
    val garden get() = data.gardens.find { it.id == data.currentGardenId } ?: data.gardens.first()
    val crop get() = data.crops.first { it.id == garden.cropId }
    var selectedTreeId by mutableStateOf<String?>(null)
    val tree get() = garden.trees.find { it.id == selectedTreeId } ?: garden.trees.find { it.row == 3 && it.place == 5 } ?: garden.trees.first()
    val zones get() = data.zones.filter { it.gardenId == garden.id }
    val schedules get() = data.schedules.filter { it.gardenId == garden.id }.sortedBy { it.start }
    val expenses get() = data.expenses.filter { it.gardenId == garden.id }
    val records get() = data.observations.filter { it.treeId == tree.id }.sortedByDescending { it.createdAt }
    var screen by mutableStateOf(Screen.BOARD)
    var rowPage by mutableStateOf(0)
    var colPage by mutableStateOf(0)
    var boardZones by mutableStateOf(false)
    var filter by mutableStateOf<TreeStatus?>(null)
    var previewAt by mutableStateOf(DEMO_TIME)
    var offlineDemo by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var gardenDraft by mutableStateOf(GardenDraft(cropId = data.crops.first().id))
    var cropDraft by mutableStateOf(CropDraft())
    var cropReturnToGarden by mutableStateOf(false)
    var zoneDraft by mutableStateOf(ZoneDraft())
    var selectedZoneId by mutableStateOf<String?>(null)
    var inspection by mutableStateOf<InspectionDraft?>(null)
    var selectedObservationId by mutableStateOf<String?>(null)
    var printerSearching by mutableStateOf(false)
    var printerSearched by mutableStateOf(false)
    var printerReturnToLabel by mutableStateOf(false)
    var copies by mutableStateOf("1")
    private var analysisJob: Job? = null

    fun go(destination: Screen) { screen = destination; error = null }
    fun notify(text: String) { message = text }
    fun fail(text: String) { error = text }
    fun openTree(id: String) { selectedTreeId = id; go(Screen.TREE) }
    fun zoneOf(treeId: String) = zones.find { treeId in it.treeIds }
    fun watering(zone: IrrigationZone?) = zone != null && schedules.any { it.zoneId == zone.id && previewAt >= it.start && previewAt < it.end }
    fun currentZone() = zones.find { it.id == selectedZoneId } ?: zones.firstOrNull()
    fun clearFormError() { error = null }

    private fun save(success: String? = null, transform: (AppData) -> AppData, after: () -> Unit = {}) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try { repository.update(transform); after(); if (success != null) notify(success) }
            catch (exception: Exception) { fail(exception.message ?: "Не удалось сохранить данные на устройстве.") }
            finally { busy = false }
        }
    }
    fun chooseGarden(id: String) = save(transform = { it.copy(currentGardenId = id) }) {
        selectedTreeId = null; rowPage = 0; colPage = 0; boardZones = false; filter = null; go(Screen.BOARD)
    }
    fun beginGarden() { gardenDraft = GardenDraft(cropId = data.crops.first().id); go(Screen.NEW_GARDEN) }
    fun beginCrop(fromGarden: Boolean) { cropReturnToGarden = fromGarden; cropDraft = CropDraft(); go(Screen.NEW_CROP) }
    fun cancelCrop() = go(if (cropReturnToGarden) Screen.NEW_GARDEN else Screen.CROPS)
    fun saveCrop() {
        val d = cropDraft
        if (d.name.isBlank() || d.name.trim().length > 60 || d.variety.trim().length > 60) return fail("Укажите название культуры до 60 символов; сорт — до 60 символов.")
        if (decimal(d.row, 3)?.let { it > BigDecimal.ZERO } != true || decimal(d.tree, 3)?.let { it > BigDecimal.ZERO } != true) return fail("Укажите положительные расстояния, до трех знаков после запятой.")
        val item = Crop(uid(), d.name.trim(), d.variety.trim(), d.row.trim().replace(',', '.'), d.tree.trim().replace(',', '.'), mapOf("Миндаль" to "МД", "Яблоня" to "ЯБ", "Груша" to "ГР")[d.name.trim()] ?: "СД")
        if (data.crops.any { sameCrop(it, item) }) return fail("Такая культура с этим сортом и схемой посадки уже есть.")
        save("Культура создана", { it.copy(crops = it.crops + item) }) {
            if (cropReturnToGarden) gardenDraft = gardenDraft.copy(cropId = item.id)
            cancelCrop()
        }
    }
    fun saveGarden() {
        val d = gardenDraft; val chosen = data.crops.find { it.id == d.cropId } ?: return fail("Выберите культуру.")
        val estimate = plantingEstimate(d.areaHa, chosen) ?: return fail("Укажите положительную площадь до четырех знаков после запятой.")
        val p = d.places.toIntOrNull()
        if (d.name.isBlank()) return fail("Укажите название сада.")
        if (estimate.count !in 1..10000) return fail("Расчет должен дать от 1 до 10 000 деревьев.")
        if (p == null || p !in 1..minOf(100, estimate.count) || (estimate.count + p - 1) / p > 100) return fail("Задайте до 100 мест в полном ряду и не более 100 рядов.")
        if (!validDate(d.date) || d.date > DEMO_DATE) return fail("Укажите дату посадки не позже 29 октября 2026.")
        val item = newGarden(d.name, chosen, d.areaHa, d.date, p)
        save("Сад создан · ${item.trees.size} деревьев", { it.copy(gardens = it.gardens + item, currentGardenId = item.id) }) {
            selectedTreeId = item.trees.first().id; rowPage = 0; colPage = 0; filter = null; boardZones = false; go(Screen.BOARD)
        }
    }
    fun updateGardenDraft(draft: GardenDraft) {
        val chosen = data.crops.find { it.id == draft.cropId }
        val estimate = chosen?.let { plantingEstimate(draft.areaHa, it) }
        gardenDraft = if (!draft.placesManual && estimate != null && estimate.count in 1..10000) draft.copy(places = kotlin.math.ceil(kotlin.math.sqrt(estimate.count.toDouble())).toInt().toString()) else draft
        error = null
    }
    fun beginZone(id: String? = null) {
        val zone = zones.find { it.id == id }
        zoneDraft = zone?.let { ZoneDraft(it.id, it.name, it.color, it.treeIds) } ?: ZoneDraft(color = zones.size % 3)
        go(Screen.ZONE_EDITOR)
    }
    fun toggleZoneCell(id: String) {
        val owner = zoneOf(id); if (owner != null && owner.id != zoneDraft.id) return
        zoneDraft = zoneDraft.copy(treeIds = if (id in zoneDraft.treeIds) zoneDraft.treeIds - id else zoneDraft.treeIds + id); error = null
    }
    fun saveZone() {
        val d = zoneDraft
        if (d.name.isBlank()) return fail("Укажите название зоны.")
        if (d.treeIds.isEmpty()) return fail("Выберите хотя бы одно дерево.")
        if (zones.any { it.id != d.id && normalized(it.name) == normalized(d.name) }) return fail("Зона с таким названием уже есть.")
        if (d.treeIds.any { id -> garden.trees.none { it.id == id } || zones.any { it.id != d.id && id in it.treeIds } }) return fail("Некоторые деревья недоступны или заняты другой зоной.")
        val item = IrrigationZone(d.id ?: uid(), garden.id, d.name.trim(), d.color, d.treeIds)
        save("Зона сохранена", { it.copy(zones = it.zones.filterNot { z -> z.id == item.id } + item) }) { selectedZoneId = item.id; boardZones = true; go(Screen.ZONE_DETAIL) }
    }
    fun saveSchedule(zoneId: String, start: String, end: String) {
        if (zones.none { it.id == zoneId }) return fail("Выберите зону полива.")
        if (!validDateTime(start) || !validDateTime(end) || start >= end) return fail("Окончание должно быть позже начала.")
        if (schedules.any { it.zoneId == zoneId && overlaps(start, end, it) }) return fail("В этой зоне уже есть пересекающийся интервал.")
        val item = IrrigationSchedule(uid(), garden.id, zoneId, start, end)
        save("Интервал добавлен", { it.copy(schedules = it.schedules + item) }) { go(Screen.IRRIGATION) }
    }
    fun deleteSchedule(id: String) = save("Интервал удален", { it.copy(schedules = it.schedules.filterNot { s -> s.id == id }) })
    fun saveExpense(title: String, category: String, quantity: String, unit: String, price: String, date: String) {
        if (title.isBlank() || unit.isBlank()) return fail("Укажите наименование и единицу измерения.")
        val cents = expenseCents(quantity, price) ?: return fail("Количество должно быть положительным, цена — неотрицательной.")
        if (!validDate(date) || date > DEMO_DATE) return fail("Укажите дату расхода не позже 29 октября 2026.")
        val item = Expense(uid(), garden.id, title.trim(), category, quantity, unit.trim(), price, cents, date)
        save("Затрата сохранена", { it.copy(expenses = it.expenses + item) }) { go(Screen.EXPENSES) }
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
        inspection = InspectionDraft(uid(), tree.id, tree.status, tree.status); go(Screen.OBSERVE)
    }
    fun useSamplePhoto() { inspection = inspection?.copy(samplePhoto = true, photoPath = null); error = null }
    fun cameraUri(): Uri = photos.cameraUri()
    fun importPhoto(uri: Uri) {
        val id = inspection?.id ?: return
        viewModelScope.launch {
            try { val path = photos.import(uri); if (inspection?.id == id) { inspection = inspection?.copy(photoPath = path, samplePhoto = false); error = null } }
            catch (exception: Exception) { fail(exception.message ?: "Не удалось загрузить фото.") }
        }
    }
    fun analyze() {
        val d = inspection ?: return
        if (d.photoPath == null && !d.samplePhoto) return fail("Добавьте фотографию перед анализом.")
        if (offlineDemo) return fail("Для анализа требуется подключение. В макете включен режим без сети.")
        error = null; inspection = d.copy(phase = AnalysisPhase.PROCESSING); screen = Screen.RESULT
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            try {
                val result = diagnosisService.analyze(d.photoPath, d.samplePhoto)
                if (inspection?.id == d.id && inspection?.phase == AnalysisPhase.PROCESSING) {
                    if (offlineDemo) { inspection = inspection?.copy(phase = AnalysisPhase.DRAFT); screen = Screen.OBSERVE; fail("Анализ прерван: нет сети.") }
                    else inspection = inspection?.copy(phase = AnalysisPhase.RESULT, diagnosis = result.diagnosis, recommendations = result.recommendations, originalAi = result)
                }
            } catch (_: kotlinx.coroutines.CancellationException) { }
            catch (exception: Exception) { if (inspection?.id == d.id) { inspection = inspection?.copy(phase = AnalysisPhase.DRAFT); screen = Screen.OBSERVE; fail(exception.message ?: "Ошибка анализа.") } }
        }
    }
    fun backToInspection() { analysisJob?.cancel(); inspection = inspection?.copy(phase = AnalysisPhase.DRAFT); go(Screen.OBSERVE) }
    fun saveInspection(withAi: Boolean) {
        val d = inspection ?: return
        val originalTree = garden.trees.find { it.id == d.treeId } ?: return fail("Дерево недоступно.")
        if (withAi && (d.originalAi == null || d.diagnosis.isBlank() || d.recommendations.isBlank())) return fail("Заполните предполагаемый диагноз и рекомендации.")
        if (!withAi && d.comment.isBlank() && d.photoPath == null && !d.samplePhoto && d.status == d.previousStatus) return fail("Добавьте фото, наблюдение или измените состояние.")
        val record = Observation(uid(), garden.id, d.treeId, OffsetDateTime.now().toString(), d.status, d.comment.trim(),
            if (withAi) d.diagnosis.trim() else "", if (withAi) d.recommendations.trim() else "", d.photoPath, d.samplePhoto, if (withAi) d.originalAi else null)
        save("Осмотр сохранен для дерева", { current -> current.copy(observations = current.observations + record,
            gardens = current.gardens.map { g -> if (g.id == garden.id) g.copy(trees = g.trees.map { t -> if (t.id == originalTree.id) t.copy(status = d.status) else t }) else g }) }) {
            selectedTreeId = d.treeId; inspection = null; go(Screen.TREE)
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
        if (printerSearching) return
        printerSearching = true; error = null
        viewModelScope.launch { delay(700); printerSearching = false; printerSearched = true; notify("Найдены демонстрационные устройства") }
    }
    fun connectPrinter(name: String) = save("Подключен демопринтер", { it.copy(printer = DemoPrinter(name)) })
    fun disconnectPrinter() = save("Демопринтер отключен", { it.copy(printer = DemoPrinter()) })
    fun demoPrint() {
        val count = copies.toIntOrNull()
        if (count == null || count !in 1..12) return fail("Укажите от 1 до 12 копий.")
        if (data.printer.device == null) { printerReturnToLabel = true; go(Screen.PRINTER); return }
        notify("Демо: отправлено ${copies.toInt()} этикеток. Физическая печать не выполнялась.")
    }
}
