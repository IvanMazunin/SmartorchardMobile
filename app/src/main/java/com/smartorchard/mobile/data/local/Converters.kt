package com.smartorchard.mobile.data.local

import androidx.room.TypeConverter
import com.smartorchard.mobile.data.AiResult
import com.smartorchard.mobile.data.TreeStatus
import kotlinx.serialization.json.Json

/** Room TypeConverters для enum и вложенных объектов. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // TreeStatus enum
    @TypeConverter
    fun statusToString(value: TreeStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): TreeStatus = TreeStatus.valueOf(value)

    // AiResult? nullable
    @TypeConverter
    fun aiResultToJson(value: AiResult?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun jsonToAiResult(value: String?): AiResult? = value?.let { json.decodeFromString(it) }

    // SyncEntityType enum
    @TypeConverter
    fun syncEntityTypeToString(value: SyncEntityType): String = value.name

    @TypeConverter
    fun stringToSyncEntityType(value: String): SyncEntityType = SyncEntityType.valueOf(value)

    // SyncOperation enum
    @TypeConverter
    fun syncOperationToString(value: SyncOperation): String = value.name

    @TypeConverter
    fun stringToSyncOperation(value: String): SyncOperation = SyncOperation.valueOf(value)
}
