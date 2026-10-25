package com.ounben.amaradio.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DatabaseUpdate(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private val syncMutex = Mutex()
        const val KEY_MODE = "mode"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_FORMATTED = "formatted"
    }

    override suspend fun doWork(): Result {
        val mode = inputData.getString(KEY_MODE) ?: "full"

        if (syncMutex.isLocked) {
            Log.w("DatabaseUpdate", "A sync is already in progress. Skipping.")
            return Result.success()
        }

        return syncMutex.withLock {
            val sync = DatabaseSync(applicationContext)

            if (!sync.isNetworkAvailable()) {
                return@withLock Result.failure(workDataOf("error" to "No internet connection"))
            }

            try {
                val success = sync.syncDatabase(mode = mode) { current, total, formattedText ->
                    try {
                        // Update progress in WorkManager
                        setProgressAsync(
                            workDataOf(
                                KEY_CURRENT to current,
                                KEY_TOTAL to total,
                                KEY_FORMATTED to formattedText
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("DatabaseUpdate", "Failed to update progress", e)
                    }
                }

                if (success) {
                    Result.success()
                } else {
                    Result.failure()
                }
            } catch (e: Exception) {
                Log.e("DatabaseUpdate", "Error executing worker", e)
                Result.failure(workDataOf("error" to (e.localizedMessage ?: "Sync error")))
            }
        }
    }
}
