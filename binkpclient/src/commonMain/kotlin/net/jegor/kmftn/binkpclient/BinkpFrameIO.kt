package net.jegor.kmftn.binkpclient

import io.ktor.utils.io.*

/**
 * Handles reading and writing of Binkp protocol frames using Ktor channels
 */
internal class BinkpFrameIO(
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel
) {
    /**
     * Read a frame from the input (blocking)
     */
    suspend fun readFrame(): BinkpFrame? {
        return try {
            // Read 2-byte header
            val header1 = input.readByte().toInt() and 0xFF
            val header2 = input.readByte().toInt() and 0xFF

            val isCommand = (header1 and 0x80) != 0
            val size = ((header1 and 0x7F) shl 8) or header2

            if (size == 0) {
                // Empty frame - drop silently
                return null
            }

            // Read data
            val data = ByteArray(size)
            input.readFully(data, 0, size)

            BinkpFrame(isCommand, data)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read a frame (non-blocking) - returns null if not enough data available
     */
    suspend fun readFrameNonBlocking(): BinkpFrame? {
        if (input.availableForRead < 2) {
            return null
        }

        // Peek at header to check if we have enough data
        val available = input.availableForRead
        if (available < 2) return null

        // We need to read to check size, so let's just try reading
        return readFrame()
    }

    /**
     * Send a command frame
     */
    suspend fun sendCommand(command: BinkpCommand, argument: String) {
        val argBytes = argument.encodeToByteArray()
        val data = ByteArray(1 + argBytes.size)
        data[0] = command.id.toByte()
        argBytes.copyInto(data, 1)
        sendFrame(true, data)
    }

    /**
     * Send a data frame
     */
    suspend fun sendDataFrame(data: ByteArray) {
        sendFrame(false, data)
    }

    /**
     * Send a frame
     */
    private suspend fun sendFrame(isCommand: Boolean, data: ByteArray) {
        val size = data.size
        require(size <= 32767) { "Frame data too large" }

        val header1 = ((if (isCommand) 0x80 else 0x00) or ((size shr 8) and 0x7F)).toByte()
        val header2 = (size and 0xFF).toByte()

        output.writeByte(header1)
        output.writeByte(header2)
        output.writeFully(data)
        output.flush()
    }

    /**
     * Check if data is available for reading
     */
    fun available(): Long {
        return input.availableForRead.toLong()
    }
}
