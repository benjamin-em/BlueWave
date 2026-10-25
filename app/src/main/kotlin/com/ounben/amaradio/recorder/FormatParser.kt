package com.ounben.amaradio.recorder

/**
 * Detects audio format (MP3, AAC, OGG, FLAC) from audio Magic Bytes or Content-Type headers.
 */
object FormatParser {

    enum class AudioFormat(val extension: String, val mimeType: String) {
        MP3("mp3", "audio/mpeg"),
        AAC("aac", "audio/aac"),
        OGG("ogg", "audio/ogg"),
        FLAC("flac", "audio/flac"),
        UNKNOWN("mp3", "audio/mpeg") // Safe default
    }

    fun detectFormat(contentType: String?, sampleBytes: ByteArray): AudioFormat {
        if (sampleBytes.size >= 4) {
            val b0 = sampleBytes[0].toInt() and 0xFF
            val b1 = sampleBytes[1].toInt() and 0xFF
            val b2 = sampleBytes[2].toInt() and 0xFF
            val b3 = sampleBytes[3].toInt() and 0xFF

            // OGG: "OggS" (0x4F 0x67 0x67 0x53)
            if (b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53) {
                return AudioFormat.OGG
            }

            // FLAC: "fLaC" (0x66 0x4C 0x61 0x43)
            if (b0 == 0x66 && b1 == 0x4C && b2 == 0x61 && b3 == 0x43) {
                return AudioFormat.FLAC
            }

            // ADTS AAC: 12 bits set to 1 (0xFF 0xF0 to 0xFF 0xF9)
            if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) {
                return AudioFormat.AAC
            }

            // MP3 Frame Sync: 11 bits set to 1 (0xFF 0xE0 to 0xFF 0xFD)
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                return AudioFormat.MP3
            }
        }

        return fromContentType(contentType)
    }

    private fun fromContentType(contentType: String?): AudioFormat {
        if (contentType.isNullOrBlank()) return AudioFormat.UNKNOWN
        val lower = contentType.lowercase()
        return when {
            lower.contains("aac") -> AudioFormat.AAC
            lower.contains("ogg") -> AudioFormat.OGG
            lower.contains("flac") -> AudioFormat.FLAC
            lower.contains("mpeg") || lower.contains("mp3") -> AudioFormat.MP3
            else -> AudioFormat.UNKNOWN
        }
    }
}
