package com.smartorchard.mobile.data.local

import androidx.room.*
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

    // ----- Zones -----
    @Query("SELECT * FROM zones")
    fun observeZones(): Flow<List<ZoneEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: ZoneEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<ZoneEntity>)

    // ----- Schedules -----
    @Query("SELECT * FROM schedules")
    fun observeSchedules(): Flow<List<ScheduleEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)
    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteSchedule(id: String)

    // ----- Expenses -----
    @Query("SELECT * FROM expenses")
    fun observeExpenses(): Flow<List<ExpenseEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

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

    // ----- Utility -----
    @Query("SELECT COUNT(*) FROM crops")
    suspend fun cropCount(): Int
}
