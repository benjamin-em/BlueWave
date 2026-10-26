package com.ounben.amaradio.recorder

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ounben.amaradio.utils.StationPlaceholderUtils
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Creates ID3v2.3 APIC Cover Art tags from local cached station artwork (or generated placeholder).
 * Strictly uses local files (station_icons/$stationUuid.jpg) without performing any network downloads.
 */
object TagEmbedder {

    fun createId3v2CoverArtTag(context: Context, stationUuid: String, stationName: String): ByteArray? {
        val jpegBytes = getStationJpegBytes(context, stationUuid, stationName) ?: return null
        return try {
            createId3v2Tag(jpegBytes)
        } catch (e: Exception) {
            Log.e("TagEmbedder", "Error creating ID3 tag", e)
            null
        }
    }

    private fun getStationJpegBytes(context: Context, stationUuid: String, stationName: String): ByteArray? {
        val iconDir = File(context.filesDir, "station_icons")
        val iconFile = File(iconDir, "$stationUuid.jpg")

        if (iconFile.exists()) {
            return try {
                iconFile.readBytes()
            } catch (_: Exception) { null }
        }

        return try {
            val bitmap = StationPlaceholderUtils.createPlaceholderBitmap(stationName, stationUuid)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            baos.toByteArray()
        } catch (_: Exception) { null }
    }

    private fun createId3v2Tag(jpegBytes: ByteArray): ByteArray {
        val mimeBytes = "image/jpeg".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0)
        val descriptionBytes = byteArrayOf(0)
        val pictureType = 0x03.toByte()

        val apicPayloadSize = 1 + mimeBytes.size + 1 + descriptionBytes.size + jpegBytes.size
        val apicHeader = ByteArray(10)
        apicHeader[0] = 'A'.code.toByte()
        apicHeader[1] = 'P'.code.toByte()
        apicHeader[2] = 'I'.code.toByte()
        apicHeader[3] = 'C'.code.toByte()

        apicHeader[4] = (apicPayloadSize shr 24).toByte()
        apicHeader[5] = (apicPayloadSize shr 16).toByte()
        apicHeader[6] = (apicPayloadSize shr 8).toByte()
        apicHeader[7] = (apicPayloadSize and 0xFF).toByte()

        val apicFrame = ByteArray(10 + apicPayloadSize)
        System.arraycopy(apicHeader, 0, apicFrame, 0, 10)
        var pos = 10
        apicFrame[pos++] = 0x00
        System.arraycopy(mimeBytes, 0, apicFrame, pos, mimeBytes.size)
        pos += mimeBytes.size
        apicFrame[pos++] = pictureType
        System.arraycopy(descriptionBytes, 0, apicFrame, pos, descriptionBytes.size)
        pos += descriptionBytes.size
        System.arraycopy(jpegBytes, 0, apicFrame, pos, jpegBytes.size)

        val tagSize = apicFrame.size
        val id3Header = ByteArray(10)
        id3Header[0] = 'I'.code.toByte()
        id3Header[1] = 'D'.code.toByte()
        id3Header[2] = '3'.code.toByte()
        id3Header[3] = 0x03
        id3Header[4] = 0x00
        id3Header[5] = 0x00

        id3Header[6] = ((tagSize shr 21) and 0x7F).toByte()
        id3Header[7] = ((tagSize shr 14) and 0x7F).toByte()
        id3Header[8] = ((tagSize shr 7) and 0x7F).toByte()
        id3Header[9] = (tagSize and 0x7F).toByte()

        val totalTag = ByteArray(10 + apicFrame.size)
        System.arraycopy(id3Header, 0, totalTag, 0, 10)
        System.arraycopy(apicFrame, 0, totalTag, 10, apicFrame.size)
        return totalTag
    }
}
