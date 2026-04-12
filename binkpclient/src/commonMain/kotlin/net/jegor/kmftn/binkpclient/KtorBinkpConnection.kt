package net.jegor.kmftn.binkpclient

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Ktor-based implementation of BinkpConnection
 */
internal class KtorBinkpConnection(
    private val socket: Socket,
    override val input: ByteReadChannel,
    override val output: ByteWriteChannel
) : BinkpConnection {
    override fun close() {
        socket.close()
    }
}

/**
 * Connect to a Binkp server
 */
internal suspend fun connectToBinkpServer(
    host: String,
    port: Int,
    timeout: Int = 60000
): KtorBinkpConnection {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val socket = aSocket(selectorManager).tcp().connect(host, port) {
        socketTimeout = timeout.toLong()
    }

    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = false)

    return KtorBinkpConnection(socket, readChannel, writeChannel)
}
