package com.smartorchard.mobile.network.model

import com.smartorchard.mobile.data.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(val data: T, val message: String? = null)

@Serializable
data class CreateGardenRequest(
    val name: String,
    @SerialName("crop_id") val cropId: String,
    @SerialName("area_ha") val areaHa: String,
    @SerialName("planting_date") val plantingDate: String,
    @SerialName("places_per_row") val placesPerRow: Int
)

@Serializable
data class CreateCropRequest(
    val name: String,
    val variety: String,
    @SerialName("row_spacing") val rowSpacing: String,
    @SerialName("tree_spacing") val treeSpacing: String
)

@Serializable
data class CreateObservationRequest(
    @SerialName("garden_id") val gardenId: String,
    @SerialName("tree_id") val treeId: String,
    val status: TreeStatus,
    val comment: String = "",
    val diagnosis: String = "",
    val recommendations: String = "",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("sample_photo") val samplePhoto: Boolean = false,
    @SerialName("original_ai") val originalAi: AiResult? = null
)

@Serializable
data class CreateZoneRequest(
    val id: String? = null,
    @SerialName("garden_id") val gardenId: String,
    val name: String,
    val color: Int = 0,
    @SerialName("tree_ids") val treeIds: List<String>
)

@Serializable
data class CreateScheduleRequest(
    @SerialName("garden_id") val gardenId: String,
    @SerialName("zone_id") val zoneId: String,
    val start: String,
    val end: String
)

@Serializable
data class CreateExpenseRequest(
    @SerialName("garden_id") val gardenId: String,
    val title: String,
    val category: String,
    val quantity: String,
    val unit: String,
    val price: String,
    val date: String
)

@Serializable
data class UpdateFinanceRequest(
    @SerialName("revenue_cents") val revenueCents: Long,
    @SerialName("annual_revenue_cents") val annualRevenueCents: Long,
    @SerialName("annual_costs_cents") val annualCostsCents: Long
)

@Serializable
data class CreateNoteRequest(
    @SerialName("tree_id") val treeId: String,
    val text: String
)

@Serializable
data class UserSettingsResponse(
    @SerialName("current_garden_id") val currentGardenId: String,
    val printer: DemoPrinter = DemoPrinter()
)

@Serializable
data class DiagnosisRequest(
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("sample_photo") val samplePhoto: Boolean = false
)
