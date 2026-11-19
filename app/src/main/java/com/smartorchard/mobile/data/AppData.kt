package com.smartorchard.mobile.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class DemoPrinter(val device: String? = null)

@Immutable
@Serializable
data class AppData(
    val schema: Int = 1,
    val crops: List<Crop>,
    val gardens: List<Garden>,
    val currentGardenId: String,
    val zones: List<IrrigationZone> = emptyList(),
    val schedules: List<IrrigationSchedule> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val observations: List<Observation> = emptyList(),
    val notes: List<TreeNote> = emptyList(),
    val printer: DemoPrinter = DemoPrinter()
)
