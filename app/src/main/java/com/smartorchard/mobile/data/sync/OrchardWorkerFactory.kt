package com.smartorchard.mobile.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrchardWorkerFactory @Inject constructor(
    private val syncEngine: SyncEngine
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = if (workerClassName == SyncWorker::class.java.name) {
        SyncWorker(appContext, workerParameters, syncEngine)
    } else {
        null
    }
}
