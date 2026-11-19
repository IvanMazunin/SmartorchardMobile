package com.smartorchard.mobile.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class GardenDraft(
    val name: String = "",
    val cropId: String = "",
    val areaHa: String = "",
    val date: String = "2026-10-29",
    val places: String = "5",
    val placesManual: Boolean = false
)

@Immutable
@Serializable
data class CropDraft(
    val name: String = "",
    val variety: String = "",
    val row: String = "",
    val tree: String = ""
)

@Immutable
@Serializable
data class ZoneDraft(
    val id: String? = null,
    val name: String = "",
    val color: Int = 0,
    val treeIds: List<String> = emptyList()
)

@Immutable
@Serializable
data class InspectionDraft(
    val id: String,
    val treeId: String,
    val previousStatus: TreeStatus,
    val status: TreeStatus,
    val comment: String = "",
    val photoPath: String? = null,
    val samplePhoto: Boolean = false,
    val diagnosis: String = "",
    val recommendations: String = "",
    val originalAi: AiResult? = null,
    val phase: AnalysisPhase = AnalysisPhase.DRAFT
)
