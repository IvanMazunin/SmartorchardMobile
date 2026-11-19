package com.smartorchard.mobile.data.sync

import com.smartorchard.mobile.data.Crop
import com.smartorchard.mobile.data.Expense
import com.smartorchard.mobile.data.Garden
import com.smartorchard.mobile.data.IrrigationSchedule
import com.smartorchard.mobile.data.IrrigationZone
import com.smartorchard.mobile.data.Observation
import com.smartorchard.mobile.data.TreeNote
import com.smartorchard.mobile.data.local.OrchardDao
import com.smartorchard.mobile.data.local.SyncEntityType
import com.smartorchard.mobile.data.local.SyncOperation
import com.smartorchard.mobile.data.local.SyncOutboxEntity
import com.smartorchard.mobile.data.local.UserSettingsEntity
import com.smartorchard.mobile.data.local.toEntity
import com.smartorchard.mobile.network.api.OrchardApi
import com.smartorchard.mobile.network.model.CreateCropRequest
import com.smartorchard.mobile.network.model.CreateExpenseRequest
import com.smartorchard.mobile.network.model.CreateGardenRequest
import com.smartorchard.mobile.network.model.CreateNoteRequest
import com.smartorchard.mobile.network.model.CreateObservationRequest
import com.smartorchard.mobile.network.model.CreateScheduleRequest
import com.smartorchard.mobile.network.model.CreateZoneRequest
import com.smartorchard.mobile.network.model.UpdateFinanceRequest
import com.smartorchard.mobile.network.model.UserSettingsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val syncedCount: Int,
    val failedCount: Int
)

@Singleton
class SyncEngine @Inject constructor(
    private val dao: OrchardDao,
    private val api: OrchardApi,
    private val json: Json
) {

    suspend fun processOutbox(): SyncResult {
        val pending = dao.getPendingOutbox()
        var syncedCount = 0
        var failedCount = 0

        for (entry in pending) {
            try {
                dispatchEntry(entry)
                dao.deleteOutboxById(entry.id)
                syncedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dao.markOutboxFailed(entry.id, e.message ?: "Sync failed")
                failedCount++
            }
        }

        return SyncResult(syncedCount = syncedCount, failedCount = failedCount)
    }

    private suspend fun dispatchEntry(entry: SyncOutboxEntity) {
        when (entry.entityType) {
            SyncEntityType.CROP -> {
                if (entry.operation == SyncOperation.UPSERT) {
                    val crop = json.decodeFromString<Crop>(entry.payloadJson)
                    api.createCrop(
                        CreateCropRequest(
                            name = crop.name,
                            variety = crop.variety,
                            rowSpacing = crop.rowSpacing,
                            treeSpacing = crop.treeSpacing
                        )
                    )
                }
            }
            SyncEntityType.GARDEN -> {
                if (entry.operation == SyncOperation.UPSERT) {
                    val garden = json.decodeFromString<Garden>(entry.payloadJson)
                    api.createGarden(
                        CreateGardenRequest(
                            name = garden.name,
                            cropId = garden.cropId,
                            areaHa = garden.areaHa,
                            plantingDate = garden.plantingDate,
                            placesPerRow = garden.placesPerRow
                        )
                    )
                }
            }
            SyncEntityType.ZONE -> {
                if (entry.operation == SyncOperation.UPSERT) {
                    val zone = json.decodeFromString<IrrigationZone>(entry.payloadJson)
                    api.createOrUpdateZone(
                        gardenId = zone.gardenId,
                        request = CreateZoneRequest(
                            id = zone.id,
                            gardenId = zone.gardenId,
                            name = zone.name,
                            color = zone.color,
                            treeIds = zone.treeIds
                        )
                    )
                }
            }
            SyncEntityType.SCHEDULE -> {
                when (entry.operation) {
                    SyncOperation.UPSERT -> {
                        val schedule = json.decodeFromString<IrrigationSchedule>(entry.payloadJson)
                        api.createSchedule(
                            gardenId = schedule.gardenId,
                            request = CreateScheduleRequest(
                                gardenId = schedule.gardenId,
                                zoneId = schedule.zoneId,
                                start = schedule.start,
                                end = schedule.end
                            )
                        )
                    }
                    SyncOperation.DELETE -> {
                        api.deleteSchedule(entry.entityId)
                    }
                }
            }
            SyncEntityType.EXPENSE -> {
                if (entry.operation == SyncOperation.UPSERT) {
                    val expense = json.decodeFromString<Expense>(entry.payloadJson)
                    api.createExpense(
                        gardenId = expense.gardenId,
                        request = CreateExpenseRequest(
                            gardenId = expense.gardenId,
                            title = expense.title,
                            category = expense.category,
                            quantity = expense.quantity,
                            unit = expense.unit,
                            price = expense.price,
                            date = expense.date
                        )
                    )
                }
            }
            SyncEntityType.FINANCE -> {
                if (entry.operation == SyncOperation.UPSERT) {
                    val payload = json.decodeFromString<FinanceSyncPayload>(entry.payloadJson)
                    api.updateGardenFinance(
                        gardenId = payload.gardenId,
                        request = UpdateFinanceRequest(
                            revenueCents = payload.finance.revenueCents,
                            annualRevenueCents = payload.finance.annualRevenueCents,
                            annualCostsCents = payload.finance.annualCostsCents
                        )
                    )
                }
            }
            SyncEntityType.NOTE -> {
                if (entry.operation == SyncOperation.UPSERT) {
                    val note = json.decodeFromString<TreeNote>(entry.payloadJson)
                    api.createNote(
                        treeId = note.treeId,
                        request = CreateNoteRequest(
                            treeId = note.treeId,
                            text = note.text
                        )
                    )
                }
            }
            SyncEntityType.OBSERVATION -> {
                if (entry.operation == SyncOperation.UPSERT) {
                    val observation = json.decodeFromString<Observation>(entry.payloadJson)
                    api.createObservation(
                        CreateObservationRequest(
                            gardenId = observation.gardenId,
                            treeId = observation.treeId,
                            status = observation.status,
                            comment = observation.comment,
                            diagnosis = observation.diagnosis,
                            recommendations = observation.recommendations,
                            photoUrl = observation.photoPath,
                            samplePhoto = observation.samplePhoto,
                            originalAi = observation.originalAi
                        )
                    )
                }
            }
            SyncEntityType.SETTINGS -> {
                if (entry.operation == SyncOperation.UPSERT) {
                    val payload = json.decodeFromString<SettingsSyncPayload>(entry.payloadJson)
                    api.updateUserSettings(
                        UserSettingsResponse(
                            currentGardenId = payload.currentGardenId,
                            printer = payload.printer
                        )
                    )
                }
            }
        }
    }

    suspend fun pullFromRemote() {
        val crops = api.getCrops().data
        val gardens = api.getGardens().data
        val remoteSettings = api.getUserSettings().data

        val gardenIds = gardens.map { it.id }.toSet()
        val zones = gardens.flatMap { garden ->
            api.getZones(garden.id).data
        }.filter { it.gardenId in gardenIds }
        val schedules = gardens.flatMap { garden ->
            api.getSchedules(garden.id).data
        }
        val expenses = gardens.flatMap { garden ->
            api.getExpenses(garden.id).data
        }

        val zoneByTreeId = buildMap {
            zones.forEach { zone ->
                zone.treeIds.forEach { treeId ->
                    put(treeId, zone.id)
                }
            }
        }

        val treeEntities = gardens.flatMap { garden ->
            garden.trees.map { tree ->
                tree.toEntity(gardenId = garden.id, zoneId = zoneByTreeId[tree.id])
            }
        }

        dao.replaceRemoteSnapshot(
            crops = crops.map { it.toEntity() },
            gardens = gardens.map { it.toEntity() },
            zones = zones.map { it.toEntity() },
            trees = treeEntities,
            schedules = schedules.map { it.toEntity() },
            expenses = expenses.map { it.toEntity() },
            settings = UserSettingsEntity(
                currentGardenId = remoteSettings.currentGardenId.ifBlank { gardens.firstOrNull()?.id.orEmpty() },
                printerDevice = remoteSettings.printer.device
            )
        )
    }
}
