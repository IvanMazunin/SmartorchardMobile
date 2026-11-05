package com.smartorchard.mobile.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Контракт репозитория: оба источника (мок и удалённый) реализуют этот интерфейс.
 * ViewModel работает только через него — переключение между источниками
 * выполняется одной строкой в DI-модуле.
 */
interface OrchardRepository {
    /** Реактивный поток состояния приложения. */
    val data: StateFlow<AppData>

    suspend fun setCurrentGarden(gardenId: String)
    suspend fun addCrop(crop: Crop)
    suspend fun addGarden(garden: Garden)
    suspend fun saveZone(zone: IrrigationZone)
    suspend fun addSchedule(schedule: IrrigationSchedule)
    suspend fun deleteSchedule(scheduleId: String)
    suspend fun addExpense(expense: Expense)
    suspend fun updateGardenFinance(gardenId: String, finance: Finance)
    suspend fun addNote(note: TreeNote)
    suspend fun addObservation(observation: Observation, gardenId: String, treeId: String, newStatus: TreeStatus)
    suspend fun updatePrinter(printer: DemoPrinter)
}
