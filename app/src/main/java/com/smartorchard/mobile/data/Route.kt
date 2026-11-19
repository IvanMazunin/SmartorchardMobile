package com.smartorchard.mobile.data

import kotlinx.serialization.Serializable

@Serializable
sealed interface NavRoute {
    @Serializable data object Board : NavRoute
    @Serializable data object Gardens : NavRoute
    @Serializable data object NewGarden : NavRoute
    @Serializable data object Crops : NavRoute
    @Serializable data class NewCrop(val fromGarden: Boolean = false) : NavRoute
    @Serializable data class TreePassport(val treeId: String) : NavRoute
    @Serializable data class Observe(val treeId: String) : NavRoute
    @Serializable data class AnalysisResult(val treeId: String) : NavRoute
    @Serializable data class ObservationDetail(val observationId: String) : NavRoute
    @Serializable data object Irrigation : NavRoute
    @Serializable data object Zones : NavRoute
    @Serializable data class ZoneEditor(val zoneId: String? = null) : NavRoute
    @Serializable data class ZoneDetail(val zoneId: String) : NavRoute
    @Serializable data class ScheduleForm(val initialZoneId: String? = null) : NavRoute
    @Serializable data object Expenses : NavRoute
    @Serializable data object NewExpense : NavRoute
    @Serializable data object Economics : NavRoute
    @Serializable data object Scan : NavRoute
    @Serializable data class Label(val treeId: String) : NavRoute
    @Serializable data class Printer(val returnToLabel: Boolean = false) : NavRoute
    @Serializable data object More : NavRoute
    @Serializable data object Storage : NavRoute
}
