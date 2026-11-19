package com.smartorchard.mobile.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class IrrigationZone(
    val id: String,
    val gardenId: String,
    val name: String,
    val color: Int = 0,
    val treeIds: List<String>
)

@Immutable
@Serializable
data class IrrigationSchedule(
    val id: String,
    val gardenId: String,
    val zoneId: String,
    val start: String,
    val end: String
)
