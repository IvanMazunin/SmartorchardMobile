package com.smartorchard.mobile.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class TreeStatus(val label: String) {
    HEALTHY("Здорово"),
    WARNING("Есть отклонения"),
    DEAD("Погибло"),
    UNKNOWN("Не обследовано")
}

@Immutable
@Serializable
data class Tree(
    val id: String,
    val row: Int,
    val place: Int,
    val status: TreeStatus = TreeStatus.UNKNOWN
)

@Immutable
@Serializable
data class Finance(
    val revenueCents: Long = 0,
    val annualRevenueCents: Long = 0,
    val annualCostsCents: Long = 0
)

@Immutable
@Serializable
data class Garden(
    val id: String,
    val name: String,
    val cropId: String,
    val areaHa: String,
    val plantingDate: String,
    val placesPerRow: Int,
    val initialTreeCount: Int,
    val trees: List<Tree>,
    val finance: Finance = Finance()
) {
    val rows: Int get() = (trees.size + placesPerRow - 1) / placesPerRow
}
