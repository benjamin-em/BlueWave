package com.ounben.amaradio.recorder

import android.content.Context
import android.util.Log
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.RecordEntity
import com.ounben.amaradio.database.user.RecordTrackEntity
import com.ounben.amaradio.players.exoplayer.IcyDataSource
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Singleton RecordManager. Registers as listener on IcyDataSource, receiving audio bytes
 * (onDataSourceBytesRead) and live stream metadata (onDataSourceStreamLiveInfo), writing them
 * to MediaWriter in real-time.
 */
class RecordManager private constructor(private val context: Context) : IcyDataSource.IcyDataSourceListener {

    data class RecordingState(
        val isRecording: Boolean = false,
        val stationName: String = "",
        val stationUuid: String = "",
        val currentTrackTitle: String = "",
        val durationMs: Long = 0L,
        val fileSizeBytes: Long = 0L,
        val formatExtension: String = "mp3",
        val error: String? = null
    )

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var currentStation: DataRadioStation? = null
    private var mediaWriter: MediaWriter? = null
    private var startTimeMs: Long = 0L
    private var initialFormatDetected = false
    private val sampleBuffer = ByteArrayOutputStream()
    private val trackChapters = mutableListOf<Pair<String, Long>>()

    private var fallbackJob: Job? = null
    private var autoStopJob: Job? = null

    fun startRecording(station: DataRadioStation, timerDurationMs: Long = 0L) {
        if (_recordingState.value.isRecording) return

        currentStation = station
        startTimeMs = System.currentTimeMillis()
        initialFormatDetected = false
        sampleBuffer.reset()
        trackChapters.clear()

        _recordingState.value = RecordingState(
            isRecording = true,
            stationName = station.Name,
            stationUuid = station.StationUuid
        )

        RecordService.startService(context, station.Name)

        autoStopJob?.cancel()
        if (timerDurationMs > 0) {
            autoStopJob = CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(timerDurationMs)
                if (_recordingState.value.isRecording) {
                    Log.d("RecordManager", "Auto-stop recording timer expired")
                    stopRecording()
                }
            }
        }

        fallbackJob?.cancel()
        fallbackJob = CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(1500)
            if (_recordingState.value.isRecording && !initialFormatDetected) {
                Log.d("RecordManager", "No bytes received from ExoPlayer, starting fallback stream")
                startFallbackStream(station)
            }
        }
    }

    private fun startFallbackStream(station: DataRadioStation) {
        val app = context.applicationContext as com.ounben.amaradio.AMARadioApp
        val httpClient = app.newHttpClientWithoutProxy()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val request = okhttp3.Request.Builder()
            .url(station.StreamUrl)
            .header("Icy-MetaData", "1")
            .header("User-Agent", "AMARadio/1.0")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use

                val metaInt = response.header("icy-metaint")?.toIntOrNull() ?: 0
                val stream = response.body?.byteStream() ?: return@use

                val icyFilter = IcyFilter(
                    inputStream = stream,
                    metaInt = metaInt,
                    onAudioData = { data, len ->
                        onDataSourceBytesRead(data, 0, len)
                    },
                    onMetadata = { trackTitle ->
                        onDataSourceStreamLiveInfo(StreamLiveInfo(mapOf("title" to trackTitle)))
                    }
                )
                icyFilter.parseStream()
            }
        } catch (e: Exception) {
            Log.e("RecordManager", "Fallback stream error", e)
        }
    }

    fun stopRecording() {
        autoStopJob?.cancel()
        autoStopJob = null
        fallbackJob?.cancel()
        fallbackJob = null
        if (!_recordingState.value.isRecording) return

        val station = currentStation
        _recordingState.value = RecordingState(isRecording = false)

        val result = if (station != null) mediaWriter?.finalizeFile(station.StationUuid) else mediaWriter?.finalizeFile("")
        mediaWriter = null

        if (result != null && station != null) {
            saveRecordingToDatabase(station, result, trackChapters.toList())
        }

        RecordService.stopService(context)
    }

    override fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int) {
        if (!_recordingState.value.isRecording || length <= 0) return

        if (!initialFormatDetected) {
            sampleBuffer.write(buffer, offset, length)
            if (sampleBuffer.size() >= 4096) {
                val sampleBytes = sampleBuffer.toByteArray()
                val detectedFormat = FormatParser.detectFormat(null, sampleBytes)
                
                mediaWriter = MediaWriter(context, currentStation?.Name ?: "Radio", detectedFormat)
                if (mediaWriter?.openStream() == true) {
                    mediaWriter?.write(sampleBytes, sampleBytes.size)
                    initialFormatDetected = true
                    _recordingState.value = _recordingState.value.copy(formatExtension = detectedFormat.extension)
                } else {
                    Log.e("RecordManager", "Failed to open MediaWriter stream")
                    stopRecording()
                    return
                }
            }
            return
        }

        mediaWriter?.write(buffer.copyOfRange(offset, offset + length), length)
        
        val elapsed = System.currentTimeMillis() - startTimeMs
        _recordingState.value = _recordingState.value.copy(
            durationMs = elapsed,
            fileSizeBytes = _recordingState.value.fileSizeBytes + length
        )
    }

    override fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo) {
        if (!_recordingState.value.isRecording) return

        val title = streamLiveInfo.title ?: return
        if (title.isBlank() || title == _recordingState.value.currentTrackTitle) return

        val offsetMs = System.currentTimeMillis() - startTimeMs
        trackChapters.add(title to offsetMs)

        _recordingState.value = _recordingState.value.copy(currentTrackTitle = title)
    }

    override fun onDataSourceConnected() {}
    override fun onDataSourceConnectionLost() {}
    override fun onDataSourceConnectionLostIrrecoverably() {}
    override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?) {}

    private fun saveRecordingToDatabase(
        station: DataRadioStation,
        result: MediaWriter.RecordingResult,
        chapters: List<Pair<String, Long>>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDb = AMARadioUserDatabase.getDatabase(context)
                val recordDao = userDb.recordDao()

                val recordId = recordDao.insertRecord(
                    RecordEntity(
                        stationUuid = station.StationUuid,
                        stationName = station.Name,
                        fileUri = result.uri.toString(),
                        timestamp = startTimeMs,
                        durationMs = result.durationMs,
                        fileSizeBytes = result.fileSizeBytes,
                        format = result.format.extension
                    )
                )

                if (recordId > 0) {
                    val trackEntities = chapters.map { (title, offset) ->
                        RecordTrackEntity(
                            recordId = recordId,
                            trackTitle = title,
                            offsetMs = offset,
                            timestamp = startTimeMs + offset
                        )
                    }
                    if (trackEntities.isNotEmpty()) {
                        recordDao.insertTracks(trackEntities)
                    }
                }
            } catch (e: Exception) {
                Log.e("RecordManager", "Error saving recording to DB", e)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: RecordManager? = null

        fun getInstance(context: Context): RecordManager {
            return instance ?: synchronized(this) {
                instance ?: RecordManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
