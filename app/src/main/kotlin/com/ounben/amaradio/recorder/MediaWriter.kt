package com.ounben.amaradio.recorder

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles loss-less file writing to Scoped Storage using Android MediaStore API.
 * Writes to Music/AMARadio/<StationName>/ as a .part file, then finalizes upon completion.
 */
class MediaWriter(
    private val context: Context,
    private val stationName: String,
    private val format: FormatParser.AudioFormat
) {
    private var fileUri: Uri? = null
    private var outputStream: OutputStream? = null
    private var partFileName: String = ""
    private var finalFileName: String = ""
    private var startTimeMs: Long = System.currentTimeMillis()
    private var totalBytesWritten: Long = 0L

    fun openStream(): Boolean {
        val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startTimeMs))
        val sanitizedStation = sanitizeFileName(stationName)
        
        finalFileName = "${sanitizedStation}_$timeStr.${format.extension}"
        partFileName = "$finalFileName.part"

        val relativePath = "${Environment.DIRECTORY_MUSIC}/AMARadio/$sanitizedStation"

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, partFileName)
            put(MediaStore.Audio.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Audio.Media.TITLE, sanitizedStation)
            put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        return try {
            fileUri = context.contentResolver.insert(collection, contentValues)
            if (fileUri != null) {
                outputStream = context.contentResolver.openOutputStream(fileUri!!)
                outputStream != null
            } else false
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error creating MediaStore entry", e)
            false
        }
    }

    fun write(data: ByteArray, length: Int) {
        try {
            outputStream?.write(data, 0, length)
            totalBytesWritten += length
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error writing audio bytes", e)
        }
    }

    fun finalizeFile(): RecordingResult? {
        val uri = fileUri ?: return null
        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error closing output stream", e)
        }

        val durationMs = System.currentTimeMillis() - startTimeMs

        // Finalize MediaStore entry: Rename .part to final name and clear IS_PENDING
        val updateValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, finalFileName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
        }

        try {
            context.contentResolver.update(uri, updateValues, null, null)
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error finalizing MediaStore entry", e)
        }

        return RecordingResult(
            uri = uri,
            fileName = finalFileName,
            durationMs = durationMs,
            fileSizeBytes = totalBytesWritten,
            format = format
        )
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Radio_Station" }
    }

    data class RecordingResult(
        val uri: Uri,
        val fileName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: FormatParser.AudioFormat
    )
}
