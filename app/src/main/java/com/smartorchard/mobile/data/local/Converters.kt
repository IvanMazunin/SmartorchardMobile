package com.smartorchard.mobile.data.local

import androidx.room.TypeConverter
import com.smartorchard.mobile.data.AiResult
import com.smartorchard.mobile.data.Tree
import com.smartorchard.mobile.data.TreeStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Room TypeConverters для сложных типов: списки, enum, вложенные объекты. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // List<Tree>
    @TypeConverter fun treesToJson(value: List<Tree>): String = json.encodeToString(value)
    @TypeConverter fun jsonToTrees(value: String): List<Tree> = json.decodeFromString(value)

    // List<String>
    @TypeConverter fun stringListToJson(value: List<String>): String = json.encodeToString(value)
    @TypeConverter fun jsonToStringList(value: String): List<String> = json.decodeFromString(value)

    // TreeStatus enum
    @TypeConverter fun statusToString(value: TreeStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): TreeStatus = TreeStatus.valueOf(value)

    // AiResult? nullable
    @TypeConverter fun aiResultToJson(value: AiResult?): String? = value?.let { json.encodeToString(it) }
    @TypeConverter fun jsonToAiResult(value: String?): AiResult? = value?.let { json.decodeFromString(it) }
}
