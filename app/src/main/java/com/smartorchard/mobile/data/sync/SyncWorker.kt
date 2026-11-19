package com.smartorchard.mobile.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val res = syncEngine.processOutbox()
        return if (res.failedCount == 0) {
            Result.success()
        } else if (runAttemptCount < 5) {
            Result.retry()
        } else {
            Result.failure()
        }
    }
}
