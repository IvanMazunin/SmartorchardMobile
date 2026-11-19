package com.smartorchard.mobile.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class AiResult(
    val diagnosis: String,
    val recommendations: String
)

@Immutable
@Serializable
data class Observation(
    val id: String,
    val gardenId: String,
    val treeId: String,
    val createdAt: String,
    val status: TreeStatus,
    val comment: String = "",
    val diagnosis: String = "",
    val recommendations: String = "",
    val photoPath: String? = null,
    val samplePhoto: Boolean = false,
    val originalAi: AiResult? = null
)

@Immutable
@Serializable
data class TreeNote(
    val id: String,
    val treeId: String,
    val text: String,
    val createdAt: String
)

@Serializable
enum class AnalysisPhase {
    DRAFT,
    PROCESSING,
    RESULT
}
