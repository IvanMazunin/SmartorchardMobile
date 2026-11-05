package com.smartorchard.mobile.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MockRepository(context: Context) : OrchardRepository {
    private val file = AtomicFile(File(context.filesDir, "orchard-state.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(load())
    override val data: StateFlow<AppData> = mutable.asStateFlow()

    private fun load(): AppData = runCatching {
        json.decodeFromString<AppData>(file.openRead().bufferedReader().use { it.readText() }).also {
            require(it.schema == 1 && it.gardens.isNotEmpty() && it.crops.isNotEmpty())
            require(it.gardens.all { g -> g.trees.isNotEmpty() && it.crops.any { c -> c.id == g.cropId } })
        }
    }.getOrElse { SeedData.create() }

    private suspend fun update(transform: (AppData) -> AppData) = mutex.withLock {
        val next = transform(mutable.value)
        withContext(Dispatchers.IO) {
            val stream = file.startWrite()
            try { stream.write(json.encodeToString(next).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
        }
        mutable.value = next
    }

    override suspend fun setCurrentGarden(gardenId: String) = update { it.copy(currentGardenId = gardenId) }

    override suspend fun addCrop(crop: Crop) = update { it.copy(crops = it.crops + crop) }

    override suspend fun addGarden(garden: Garden) = update {
        it.copy(gardens = it.gardens + garden, currentGardenId = garden.id)
    }

    override suspend fun saveZone(zone: IrrigationZone) = update {
        it.copy(zones = it.zones.filterNot { z -> z.id == zone.id } + zone)
    }

    override suspend fun addSchedule(schedule: IrrigationSchedule) = update {
        it.copy(schedules = it.schedules + schedule)
    }

    override suspend fun deleteSchedule(scheduleId: String) = update {
        it.copy(schedules = it.schedules.filterNot { s -> s.id == scheduleId })
    }

    override suspend fun addExpense(expense: Expense) = update {
        it.copy(expenses = it.expenses + expense)
    }

    override suspend fun updateGardenFinance(gardenId: String, finance: Finance) = update {
        it.copy(gardens = it.gardens.map { g -> if (g.id == gardenId) g.copy(finance = finance) else g })
    }

    override suspend fun addNote(note: TreeNote) = update {
        it.copy(notes = it.notes + note)
    }

    override suspend fun addObservation(observation: Observation, gardenId: String, treeId: String, newStatus: TreeStatus) = update { current ->
        current.copy(
            observations = current.observations + observation,
            gardens = current.gardens.map { g ->
                if (g.id == gardenId) g.copy(trees = g.trees.map { t ->
                    if (t.id == treeId) t.copy(status = newStatus) else t
                }) else g
            }
        )
    }

    override suspend fun updatePrinter(printer: DemoPrinter) = update {
        it.copy(printer = printer)
    }
}
