package com.smartorchard.mobile.data.local

import androidx.room.*
import com.smartorchard.mobile.data.TreeStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OrchardDao {

    // ----- Crops -----
    @Query("SELECT * FROM crops")
    fun observeCrops(): Flow<List<CropEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrop(crop: CropEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrops(crops: List<CropEntity>)

    @Query("DELETE FROM crops")
    suspend fun deleteAllCrops()

    // ----- Gardens -----
    @Query("SELECT * FROM gardens")
    fun observeGardens(): Flow<List<GardenEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGarden(garden: GardenEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGardens(gardens: List<GardenEntity>)

    @Query("UPDATE gardens SET revenue_cents = :revenueCents, annual_revenue_cents = :annualRevenueCents, annual_costs_cents = :annualCostsCents WHERE id = :gardenId")
    suspend fun updateFinance(gardenId: String, revenueCents: Long, annualRevenueCents: Long, annualCostsCents: Long)

    @Query("SELECT * FROM gardens WHERE id = :gardenId LIMIT 1")
    suspend fun getGarden(gardenId: String): GardenEntity?

    @Update
    suspend fun updateGarden(garden: GardenEntity)

    @Query("DELETE FROM gardens")
    suspend fun deleteAllGardens()

    // ----- Trees -----
    @Query("SELECT * FROM trees ORDER BY row_index ASC, place ASC")
    fun observeTrees(): Flow<List<TreeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrees(trees: List<TreeEntity>)

    @Query("UPDATE trees SET status = :status WHERE id = :treeId")
    suspend fun updateTreeStatus(treeId: String, status: TreeStatus)

    @Query("UPDATE trees SET zone_id = NULL WHERE zone_id = :zoneId")
    suspend fun clearTreesFromZone(zoneId: String)

    @Query("UPDATE trees SET zone_id = :zoneId WHERE id IN (:treeIds)")
    suspend fun assignTreesToZone(zoneId: String, treeIds: List<String>)

    @Query("DELETE FROM trees")
    suspend fun deleteAllTrees()

    // ----- Zones -----
    @Query("SELECT * FROM zones")
    fun observeZones(): Flow<List<ZoneEntity>>

    @Upsert
    suspend fun insertZone(zone: ZoneEntity)

    @Upsert
    suspend fun insertZones(zones: List<ZoneEntity>)

    @Query("DELETE FROM zones")
    suspend fun deleteAllZones()

    @Transaction
    suspend fun upsertZoneWithTrees(zone: ZoneEntity, treeIds: List<String>) {
        insertZone(zone)
        clearTreesFromZone(zone.id)
        if (treeIds.isNotEmpty()) {
            treeIds.chunked(900).forEach { chunk ->
                assignTreesToZone(zone.id, chunk)
            }
        }
    }

    // ----- Schedules -----
    @Query("SELECT * FROM schedules")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteSchedule(id: String)

    @Query("DELETE FROM schedules")
    suspend fun deleteAllSchedules()

    // ----- Expenses -----
    @Query("SELECT * FROM expenses")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    // ----- Observations -----
    @Query("SELECT * FROM observations")
    fun observeObservations(): Flow<List<ObservationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: ObservationEntity)

    // ----- Notes -----
    @Query("SELECT * FROM notes")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    // ----- User Settings -----
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun observeSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: UserSettingsEntity)

    // ----- Sync Outbox -----
    @Insert
    suspend fun insertOutbox(entry: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox ORDER BY created_at ASC, id ASC")
    suspend fun getPendingOutbox(): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingSyncCount(): Flow<Int>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteOutboxById(id: Long)

    @Query("UPDATE sync_outbox SET retry_count = retry_count + 1, last_error = :error WHERE id = :id")
    suspend fun markOutboxFailed(id: Long, error: String)

    // ----- Utility -----
    @Query("SELECT COUNT(*) FROM crops")
    suspend fun cropCount(): Int

    // ----- Composite Transactions -----
    @Transaction
    suspend fun seedInitialData(
        crops: List<CropEntity>,
        gardens: List<GardenEntity>,
        zones: List<ZoneEntity>,
        trees: List<TreeEntity>,
        schedules: List<ScheduleEntity>,
        expenses: List<ExpenseEntity>,
        settings: UserSettingsEntity
    ) {
        if (cropCount() > 0) return
        insertCrops(crops)
        insertGardens(gardens)
        insertZones(zones)
        insertTrees(trees)
        insertSchedules(schedules)
        insertExpenses(expenses)
        insertSettings(settings)
    }

    @Transaction
    suspend fun replaceRemoteSnapshot(
        crops: List<CropEntity>,
        gardens: List<GardenEntity>,
        zones: List<ZoneEntity>,
        trees: List<TreeEntity>,
        schedules: List<ScheduleEntity>,
        expenses: List<ExpenseEntity>,
        settings: UserSettingsEntity? = null
    ) {
        deleteAllSchedules()
        deleteAllExpenses()
        deleteAllTrees()
        deleteAllZones()
        deleteAllGardens()
        deleteAllCrops()

        insertCrops(crops)
        insertGardens(gardens)
        insertZones(zones)
        insertTrees(trees)
        insertSchedules(schedules)
        insertExpenses(expenses)
        if (settings != null) {
            insertSettings(settings)
        }
    }

    @Transaction
    suspend fun insertGardenWithTrees(
        garden: GardenEntity,
        trees: List<TreeEntity>,
        printerDevice: String?
    ) {
        insertGarden(garden)
        insertTrees(trees)
        insertSettings(UserSettingsEntity(currentGardenId = garden.id, printerDevice = printerDevice))
    }

    @Transaction
    suspend fun insertObservationAndUpdateTree(
        observation: ObservationEntity,
        treeId: String,
        newStatus: TreeStatus
    ) {
        insertObservation(observation)
        updateTreeStatus(treeId, newStatus)
    }

    // ----- Atomic Local Mutation + Outbox Enqueue Transactions -----
    @Transaction
    suspend fun upsertSettingsWithOutbox(
        settings: UserSettingsEntity,
        outbox: SyncOutboxEntity
    ) {
        insertSettings(settings)
        insertOutbox(outbox)
    }

    @Transaction
    suspend fun insertCropWithOutbox(
        crop: CropEntity,
        outbox: SyncOutboxEntity
    ) {
        insertCrop(crop)
        insertOutbox(outbox)
    }

    @Transaction
    suspend fun insertGardenWithTreesAndOutbox(
        garden: GardenEntity,
        trees: List<TreeEntity>,
        printerDevice: String?,
        outbox: SyncOutboxEntity
    ) {
        insertGardenWithTrees(garden, trees, printerDevice)
        insertOutbox(outbox)
    }

    @Transaction
    suspend fun upsertZoneWithTreesAndOutbox(
        zone: ZoneEntity,
        treeIds: List<String>,
        outbox: SyncOutboxEntity
    ) {
        upsertZoneWithTrees(zone, treeIds)
        insertOutbox(outbox)
    }

    @Transaction
    suspend fun insertScheduleWithOutbox(
        schedule: ScheduleEntity,
        outbox: SyncOutboxEntity
    ) {
        insertSchedule(schedule)
        insertOutbox(outbox)
    }

    @Transaction
    suspend fun deleteScheduleWithOutbox(
        scheduleId: String,
        outbox: SyncOutboxEntity
    ) {
        deleteSchedule(scheduleId)
        insertOutbox(outbox)
    }

    @Transaction
    suspend fun insertExpenseWithOutbox(
        expense: ExpenseEntity,
        outbox: SyncOutboxEntity
    ) {
        insertExpense(expense)
        insertOutbox(outbox)
    }

    @Transaction
    suspend fun updateFinanceWithOutbox(
        gardenId: String,
        revenueCents: Long,
        annualRevenueCents: Long,
        annualCostsCents: Long,
        outbox: SyncOutboxEntity
    ) {
        updateFinance(gardenId, revenueCents, annualRevenueCents, annualCostsCents)
        insertOutbox(outbox)
    }

    @Transaction
    suspend fun insertNoteWithOutbox(
        note: NoteEntity,
        outbox: SyncOutboxEntity
    ) {
        insertNote(note)
        insertOutbox(outbox)
    }

    @Transaction
    suspend fun insertObservationAndUpdateTreeWithOutbox(
        observation: ObservationEntity,
        treeId: String,
        newStatus: TreeStatus,
        outbox: SyncOutboxEntity
    ) {
        insertObservationAndUpdateTree(observation, treeId, newStatus)
        insertOutbox(outbox)
    }
}
