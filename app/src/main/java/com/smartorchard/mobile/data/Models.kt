package com.smartorchard.mobile.data

import kotlinx.serialization.Serializable

@Serializable
enum class TreeStatus(val label: String) {
    HEALTHY("Здорово"), WARNING("Есть отклонения"), DEAD("Погибло"), UNKNOWN("Не обследовано")
}
@Serializable data class Crop(val id: String, val name: String, val variety: String = "", val rowSpacing: String, val treeSpacing: String, val prefix: String = "СД")
@Serializable data class Tree(val id: String, val row: Int, val place: Int, val status: TreeStatus = TreeStatus.UNKNOWN)
@Serializable data class Finance(val revenueCents: Long = 0, val annualRevenueCents: Long = 0, val annualCostsCents: Long = 0)
@Serializable data class Garden(val id: String, val name: String, val cropId: String, val areaHa: String, val plantingDate: String,
    val placesPerRow: Int, val initialTreeCount: Int, val trees: List<Tree>, val finance: Finance = Finance()) {
    val rows: Int get() = (trees.size + placesPerRow - 1) / placesPerRow
}
@Serializable data class IrrigationZone(val id: String, val gardenId: String, val name: String, val color: Int = 0, val treeIds: List<String>)
@Serializable data class IrrigationSchedule(val id: String, val gardenId: String, val zoneId: String, val start: String, val end: String)
@Serializable data class Expense(val id: String, val gardenId: String, val title: String, val category: String,
    val quantity: String, val unit: String, val price: String, val totalCents: Long, val date: String)
@Serializable data class AiResult(val diagnosis: String, val recommendations: String)
@Serializable data class Observation(val id: String, val gardenId: String, val treeId: String, val createdAt: String,
    val status: TreeStatus, val comment: String = "", val diagnosis: String = "", val recommendations: String = "",
    val photoPath: String? = null, val samplePhoto: Boolean = false, val originalAi: AiResult? = null)
@Serializable data class TreeNote(val id: String, val treeId: String, val text: String, val createdAt: String)
@Serializable data class DemoPrinter(val device: String? = null)
@Serializable data class AppData(val schema: Int = 1, val crops: List<Crop>, val gardens: List<Garden>, val currentGardenId: String,
    val zones: List<IrrigationZone> = emptyList(), val schedules: List<IrrigationSchedule> = emptyList(), val expenses: List<Expense> = emptyList(),
    val observations: List<Observation> = emptyList(), val notes: List<TreeNote> = emptyList(), val printer: DemoPrinter = DemoPrinter())

enum class Screen {
    BOARD, GARDENS, NEW_GARDEN, CROPS, NEW_CROP, TREE, OBSERVE, RESULT, OBSERVATION_DETAIL,
    IRRIGATION, ZONES, ZONE_EDITOR, ZONE_DETAIL, SCHEDULE, EXPENSES, NEW_EXPENSE, ECONOMICS, SCAN, LABEL, PRINTER, MORE, STORAGE
}
data class GardenDraft(val name: String = "", val cropId: String = "", val areaHa: String = "", val date: String = "2026-10-29", val places: String = "5", val placesManual: Boolean = false)
data class CropDraft(val name: String = "", val variety: String = "", val row: String = "", val tree: String = "")
data class ZoneDraft(val id: String? = null, val name: String = "", val color: Int = 0, val treeIds: List<String> = emptyList())
data class InspectionDraft(val id: String, val treeId: String, val previousStatus: TreeStatus, val status: TreeStatus,
    val comment: String = "", val photoPath: String? = null, val samplePhoto: Boolean = false, val diagnosis: String = "",
    val recommendations: String = "", val originalAi: AiResult? = null, val phase: AnalysisPhase = AnalysisPhase.DRAFT)
enum class AnalysisPhase { DRAFT, PROCESSING, RESULT }
