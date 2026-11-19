package com.smartorchard.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CropEntity::class,
        GardenEntity::class,
        TreeEntity::class,
        ZoneEntity::class,
        ScheduleEntity::class,
        ExpenseEntity::class,
        ObservationEntity::class,
        NoteEntity::class,
        UserSettingsEntity::class,
        SyncOutboxEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OrchardDatabase : RoomDatabase() {
    abstract fun dao(): OrchardDao
}
