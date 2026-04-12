package net.jegor.kmftn.binkpclient

import io.ktor.utils.io.*

/**
 * Platform-agnostic connection abstraction for Binkp protocol
 */
internal interface BinkpConnection {
    /**
     * Input channel for reading data
     */
    val input: ByteReadChannel

    /**
     * Output channel for writing data
     */
    val output: ByteWriteChannel

    /**
     * Close the connection
     */
    fun close()
}
