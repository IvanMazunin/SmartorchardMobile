package com.smartorchard.mobile.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MockRepository(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "orchard-state.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(load())
    val state = mutable.asStateFlow()

    private fun load(): AppData = runCatching {
        json.decodeFromString<AppData>(file.openRead().bufferedReader().use { it.readText() }).also {
            require(it.schema == 1 && it.gardens.isNotEmpty() && it.crops.isNotEmpty())
            require(it.gardens.all { g -> g.trees.isNotEmpty() && it.crops.any { c -> c.id == g.cropId } })
        }
    }.getOrElse { SeedData.create() }

    suspend fun update(transform: (AppData) -> AppData) = mutex.withLock {
        val next = transform(mutable.value)
        withContext(Dispatchers.IO) {
            val stream = file.startWrite()
            try { stream.write(json.encodeToString(next).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
        }
        mutable.value = next
    }
}
