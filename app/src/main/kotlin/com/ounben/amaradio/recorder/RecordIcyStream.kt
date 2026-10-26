package com.ounben.amaradio.recorder

import java.io.InputStream

/**
 * Dedicated, standalone ICY Stream Parser for Radio Recording.
 * Uses the exact single-stream state machine from IcyDataSource to guarantee 100% pure audio bytes.
 */
class RecordIcyStream(
    private val inputStream: InputStream,
    private val metaInt: Int,
    private val onAudioData: (buffer: ByteArray, length: Int) -> Unit,
    private val onMetadata: (title: String) -> Unit
) {
    @Volatile
    private var isRunning = true

    fun parseStream() {
        if (metaInt <= 0) {
            val buffer = ByteArray(8192)
            while (isRunning) {
                val read = inputStream.read(buffer, 0, buffer.size)
                if (read == -1) break
                if (read > 0) {
                    onAudioData(buffer, read)
                }
            }
            return
        }

        val buffer = ByteArray(8192)
        var bytesUntilMetadata = metaInt
        var metadataBytesToRead = 0
        var metadataBuffer = ByteArray(2048)
        var metadataBufferPos = 0

        while (isRunning) {
            // Case 1: Reading ICY metadata bytes
            if (metadataBytesToRead > 0) {
                val toRead = minOf(metadataBytesToRead - metadataBufferPos, metadataBuffer.size - metadataBufferPos)
                val read = inputStream.read(metadataBuffer, metadataBufferPos, toRead)
                if (read == -1 || !isRunning) break

                metadataBufferPos += read
                if (metadataBufferPos == metadataBytesToRead) {
                    val metaString = String(metadataBuffer, 0, metadataBytesToRead, Charsets.UTF_8).trim()
                    parseAndEmitMetadata(metaString)
                    metadataBytesToRead = 0
                    bytesUntilMetadata = metaInt
                }
                continue
            }

            // Case 2: Encountered metadata length byte
            if (bytesUntilMetadata == 0) {
                val lengthByte = inputStream.read()
                if (lengthByte == -1 || !isRunning) break

                val length = lengthByte * 16
                if (length > 0) {
                    metadataBytesToRead = length
                    metadataBufferPos = 0
                    if (metadataBytesToRead > metadataBuffer.size) {
                        metadataBuffer = ByteArray(metadataBytesToRead)
                    }
                } else {
                    bytesUntilMetadata = metaInt
                }
                continue
            }

            // Case 3: Regular audio data read
            val toRead = minOf(buffer.size, bytesUntilMetadata)
            val bytesRead = inputStream.read(buffer, 0, toRead)
            if (bytesRead == -1 || !isRunning) break

            if (bytesRead > 0) {
                bytesUntilMetadata -= bytesRead
                onAudioData(buffer, bytesRead)
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
