package com.ounben.amaradio.sync

import android.content.Context
import android.database.sqlite.SQLiteFullException
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.room.withTransaction
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toEntity
import com.ounben.amaradio.station.DataRadioStation
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class DatabaseSyncState {
    object Idle : DatabaseSyncState()
    data class Running(
        val current: Int,
        val total: Int,
        val formattedProgress: String,
        val mode: String
    ) : DatabaseSyncState()
    data class Success(val message: String, val lastSyncTime: String) : DatabaseSyncState()
    data class Error(val message: String) : DatabaseSyncState()
}

class DatabaseSync(private val context: Context) {

    private val app = context.applicationContext as AMARadioApp
    private val loader = DatabaseLoader(context)
    private val database = AMARadioDatabase.getDatabase(app)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(app)

    private val numberFormat = DecimalFormat("#,###")

    companion object {
        private val syncMutex = Mutex()
        private val _progressFlow = MutableStateFlow<DatabaseSyncState>(DatabaseSyncState.Idle)
        val progressFlow: StateFlow<DatabaseSyncState> = _progressFlow.asStateFlow()
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun syncDatabase(
        mode: String = "full",
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (syncMutex.isLocked) {
            Log.w("DatabaseSync", "A database sync is already in progress. Skipping duplicate execution.")
            return@withContext true
        }

        syncMutex.withLock {
            if (!isNetworkAvailable()) {
                val errorMsg = app.getString(com.ounben.amaradio.R.string.database_error_no_connection)
                _progressFlow.value = DatabaseSyncState.Error(errorMsg)
                return@withLock false
            }

            try {
                if (mode == "background") {
                    performBackgroundSync(onProgress)
                } else if (mode == "incremental") {
                    performIncrementalSync(onProgress)
                } else {
                    performFullSync(onProgress)
                }
            } catch (e: SQLiteFullException) {
                Log.e("DatabaseSync", "Disk full during sync", e)
                val errorMsg = app.getString(com.ounben.amaradio.R.string.database_error_no_space)
                _progressFlow.value = DatabaseSyncState.Error(errorMsg)
                false
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                Log.e("DatabaseSync", "Sync failed: ${e.message}", e)
                val errorMsg = e.localizedMessage ?: app.getString(com.ounben.amaradio.R.string.database_error_failed)
                _progressFlow.value = DatabaseSyncState.Error(errorMsg)
                false
            }
        }
    }

    private suspend fun performBackgroundSync(
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit
    ): Boolean = coroutineScope {
        // 1. Primärversuch im Hintergrund: Snapshot CDN GZIP
        try {
            val snapshotSuccess = performSnapshotSync(onProgress)
            if (snapshotSuccess) return@coroutineScope true
            else Log.w("DatabaseSync", "Background snapshot import returned false. Fallback to last 1000 changes...")
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            Log.w("DatabaseSync", "Background snapshot import failed: ${t.message}. Fallback to last 1000 changes...", t)
        }

        // 2. Fallback bei Fehler im Hintergrund: Die letzten 1000 geänderten Sender laden
        Log.i("DatabaseSync", "Background snapshot failed -> Loading last 1,000 changed stations...")
        performIncrementalSync(onProgress)
    }

    private suspend fun performFullSync(
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit
    ): Boolean = coroutineScope {
        // 1. Primärer Versuchsablauf: Snapshot CDN GZIP
        try {
            val snapshotSuccess = performSnapshotSync(onProgress)
            if (snapshotSuccess) return@coroutineScope true
            else Log.w("DatabaseSync", "Snapshot import returned false. Fallback to full API pagination...")
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            Log.w("DatabaseSync", "Snapshot import failed: ${t.message}. Fallback to full API pagination...", t)
        }

        // 2. Fallback bei Fehler: Vollständige Paginierung aller ~48.000 Sender über die Radio-Browser API
        Log.i("DatabaseSync", "Snapshot failed -> Starting full API paginated sync...")
        performApiFullSync(onProgress)
    }

    private suspend fun performApiFullSync(
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit
    ): Boolean = coroutineScope {
        val totalCount = loader.getTotalStationCount() ?: 0
        var currentLoaded = 0
        val chunkSize = 1000 // 1.000 Sender pro Abfrage
        val parallelBatches = 2 // 2x 1.000 = 2.000 Sender pro Zyklus

        reportProgress(0, totalCount, mode = "full", onProgress = onProgress)

        while (totalCount == 0 || currentLoaded < totalCount) {
            coroutineContext.ensureActive()

            val tasks = (0 until parallelBatches).map { batchIdx ->
                val offset = currentLoaded + (batchIdx * chunkSize)
                if (totalCount == 0 || offset < totalCount) {
                    async(Dispatchers.IO) {
                        loader.getStationsChunk(offset = offset, limit = chunkSize)
                    }
                } else null
            }.filterNotNull()

            val results = tasks.awaitAll()
            var batchCount = 0

            for (chunk in results) {
                if (!chunk.isNullOrEmpty()) {
                    val entities = chunk.map { it.toEntity() }
                    database.stationDao().syncBatch(entities)
                    batchCount += chunk.size
                }
            }

            if (batchCount == 0) {
                // No more stations returned from API
                break
            }

            currentLoaded += batchCount
            reportProgress(currentLoaded, totalCount, mode = "full", onProgress = onProgress)
        }

        saveLastSyncTimestamp()
        val finalSyncTime = prefs.getString("last_db_sync_time", "Just now") ?: "Just now"
        _progressFlow.value = DatabaseSyncState.Success("Full sync completed", finalSyncTime)
        true
    }

    private suspend fun performSnapshotSync(
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val snapshotUrl = "https://radiobrowser-cdn.ounben.com/stations.json.gz"
        Log.i("DatabaseSync", "Starting snapshot download from $snapshotUrl...")

        reportProgress(0, 0, mode = "full", onProgress = onProgress)

        try {
            // 1. Request mit Accept-Encoding: gzip (ohne manuellen User-Agent Override)
            val request = Request.Builder()
                .url(snapshotUrl)
                .header("Accept-Encoding", "gzip")
                .build()

            val response = app.httpClient.newCall(request).execute()
            Log.i("DatabaseSync", "Snapshot HTTP response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.e("DatabaseSync", "Snapshot HTTP response failed with code ${response.code}")
                return@withContext false
            }

            val body = response.body
            val rawStream = body.byteStream()

            // 2. GZIP Dekomprimierung
            val isGzip = response.header("Content-Encoding")?.contains("gzip", ignoreCase = true) == true ||
                         snapshotUrl.endsWith(".gz")

            val decompressedStream: InputStream = if (isGzip) GZIPInputStream(rawStream) else rawStream

            Log.i("DatabaseSync", "Streaming and decoding stations JSON directly from GZIP stream in 5,000-item chunks...")
            
            var totalProcessed = 0
            val chunkSize = 5000

            // 3. Echte Sequence-Streaming & Chunked Room DB-Inserts mit 200ms Pause zwischen den Blöcken
            decompressedStream.use { stream ->
                val stationSequence = DataRadioStation.DecodeJsonSequence(stream)
                
                stationSequence.chunked(chunkSize).forEach { chunk ->
                    coroutineContext.ensureActive()

                    database.withTransaction {
                        val entities = chunk.map { it.toEntity() }
                        database.stationDao().insertAllInternal(entities)
                    }

                    totalProcessed += chunk.size
                    reportProgress(totalProcessed, 0, mode = "full", onProgress = onProgress)

                    // 200 ms Pause nach jedem 5.000er-Chunk: SQLite-Locks freigeben,
                    // eMMC-Schreibpuffer entlasten und CPU-/GC-Spitzen vermeiden.
                    delay(200)
                }
            }

            if (totalProcessed == 0) {
                Log.e("DatabaseSync", "Snapshot stream returned 0 stations")
                return@withContext false
            }

            reportProgress(totalProcessed, totalProcessed, mode = "full", onProgress = onProgress)
            saveLastSyncTimestamp()

            val finalSyncTime = prefs.getString("last_db_sync_time", "Just now") ?: "Just now"
            _progressFlow.value = DatabaseSyncState.Success("Snapshot import completed successfully", finalSyncTime)
            Log.i("DatabaseSync", "Snapshot import completed successfully with $totalProcessed stations.")
            true
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            Log.e("DatabaseSync", "Snapshot import failed: ${t.message}", t)
            false
        }
    }

    private suspend fun performIncrementalSync(
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit
    ): Boolean = coroutineScope {
        val maxIncrementalLimit = 1000 // Inkrementell: 1.000 neueste Änderungen in einem einzigen Aufruf laden

        reportProgress(0, maxIncrementalLimit, mode = "incremental", onProgress = onProgress)

        coroutineContext.ensureActive()

        val changed = loader.getStationsLastChange(offset = 0, limit = maxIncrementalLimit)
        if (!changed.isNullOrEmpty()) {
            val entities = changed.map { it.toEntity() }
            database.stationDao().syncBatch(entities)

            reportProgress(entities.size, maxIncrementalLimit, mode = "incremental", onProgress = onProgress)
        } else {
            reportProgress(0, maxIncrementalLimit, mode = "incremental", onProgress = onProgress)
        }

        saveLastSyncTimestamp()
        val finalSyncTime = prefs.getString("last_db_sync_time", "Just now") ?: "Just now"
        _progressFlow.value = DatabaseSyncState.Success("Incremental sync completed", finalSyncTime)
        true
    }

    private fun reportProgress(
        current: Int,
        total: Int,
        mode: String,
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit
    ) {
        val formattedCurrent = numberFormat.format(current)
        val text = if (total > 0 && current > 0) {
            val formattedTotal = numberFormat.format(total)
            app.getString(com.ounben.amaradio.R.string.database_progress_format, formattedCurrent, formattedTotal)
        } else if (current > 0) {
            app.getString(com.ounben.amaradio.R.string.database_stations_loaded, formattedCurrent)
        } else {
            app.getString(com.ounben.amaradio.R.string.database_downloading)
        }

        _progressFlow.value = DatabaseSyncState.Running(
            current = current,
            total = total,
            formattedProgress = text,
            mode = mode
        )
        onProgress(current, total, text)
    }

    private fun saveLastSyncTimestamp() {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        prefs.edit().putString("last_db_sync_time", nowStr).apply()
    }
}
