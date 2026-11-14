package com.smartorchard.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartorchard.mobile.data.AiResult
import com.smartorchard.mobile.data.Tree
import com.smartorchard.mobile.data.TreeStatus

@Entity(tableName = "crops")
data class CropEntity(
    @PrimaryKey val id: String,
    val name: String,
    val variety: String = "",
    @ColumnInfo(name = "row_spacing") val rowSpacing: String,
    @ColumnInfo(name = "tree_spacing") val treeSpacing: String,
    val prefix: String = "СД"
)

@Entity(tableName = "gardens")
data class GardenEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "crop_id") val cropId: String,
    @ColumnInfo(name = "area_ha") val areaHa: String,
    @ColumnInfo(name = "planting_date") val plantingDate: String,
    @ColumnInfo(name = "places_per_row") val placesPerRow: Int,
    @ColumnInfo(name = "initial_tree_count") val initialTreeCount: Int,
    val trees: List<Tree>,
    @ColumnInfo(name = "revenue_cents") val revenueCents: Long = 0,
    @ColumnInfo(name = "annual_revenue_cents") val annualRevenueCents: Long = 0,
    @ColumnInfo(name = "annual_costs_cents") val annualCostsCents: Long = 0
)

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "garden_id") val gardenId: String,
    val name: String,
    val color: Int = 0,
    @ColumnInfo(name = "tree_ids") val treeIds: List<String>
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "garden_id") val gardenId: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val start: String,
    val end: String
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "garden_id") val gardenId: String,
    val title: String,
    val category: String,
    val quantity: String,
    val unit: String,
    val price: String,
    @ColumnInfo(name = "total_cents") val totalCents: Long,
    val date: String
)

@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "garden_id") val gardenId: String,
    @ColumnInfo(name = "tree_id") val treeId: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    val status: TreeStatus,
    val comment: String = "",
    val diagnosis: String = "",
    val recommendations: String = "",
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "sample_photo") val samplePhoto: Boolean = false,
    @ColumnInfo(name = "original_ai") val originalAi: AiResult? = null
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tree_id") val treeId: String,
    val text: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "current_garden_id") val currentGardenId: String = "",
    @ColumnInfo(name = "printer_device") val printerDevice: String? = null
)
