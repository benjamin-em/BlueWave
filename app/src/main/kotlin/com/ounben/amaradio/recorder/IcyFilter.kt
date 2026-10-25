package com.ounben.amaradio.recorder

import java.io.InputStream

/**
 * Parses and strips in-band ICY metadata (icy-metaint) from an HTTP live radio stream in real-time.
 * Ensures the audio stream written to disk remains 100% pure and uncorrupted.
 */
class IcyFilter(
    private val inputStream: InputStream,
    private val metaInt: Int,
    private val onAudioData: (byteArray: ByteArray, length: Int) -> Unit,
    private val onMetadata: (title: String) -> Unit
) {
    @Volatile
    private var isRunning = true

    fun parseStream() {
        if (metaInt <= 0) {
            // Stream does not contain ICY in-band metadata - pass through audio bytes directly
            val buffer = ByteArray(8192)
            var bytesRead = 0
            while (isRunning && inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (bytesRead > 0) {
                    onAudioData(buffer, bytesRead)
                }
            }
            return
        }

        val audioBuffer = ByteArray(8192)
        var audioBytesLeftInChunk = metaInt

        while (isRunning) {
            val toRead = minOf(audioBuffer.size, audioBytesLeftInChunk)
            val bytesRead = inputStream.read(audioBuffer, 0, toRead)
            if (bytesRead == -1) break

            if (bytesRead > 0) {
                onAudioData(audioBuffer, bytesRead)
                audioBytesLeftInChunk -= bytesRead
            }

            if (audioBytesLeftInChunk == 0) {
                val metaLenByte = inputStream.read()
                if (metaLenByte == -1) break

                val metaLen = metaLenByte * 16
                if (metaLen > 0) {
                    val metaBuffer = ByteArray(metaLen)
                    var metaRead = 0
                    while (metaRead < metaLen) {
                        val r = inputStream.read(metaBuffer, metaRead, metaLen - metaRead)
                        if (r == -1) break
                        metaRead += r
                    }

                    if (metaRead == metaLen) {
                        val metaString = String(metaBuffer, Charsets.UTF_8).trimEnd(0.toChar())
                        parseAndEmitMetadata(metaString)
                    }
                }

                // Reset counter for the next audio chunk
                audioBytesLeftInChunk = metaInt
            }
        }
    }

    private fun parseAndEmitMetadata(metaString: String) {
        val titleMatch = STREAM_TITLE_REGEX.find(metaString)
        if (titleMatch != null) {
            val title = titleMatch.groupValues[1].trim()
            if (title.isNotEmpty()) {
                onMetadata(title)
            }
        }
    }

    fun stop() {
        isRunning = false
    }

    companion object {
        private val STREAM_TITLE_REGEX = Regex("""StreamTitle='(.*?)';""", RegexOption.IGNORE_CASE)
    }
}
