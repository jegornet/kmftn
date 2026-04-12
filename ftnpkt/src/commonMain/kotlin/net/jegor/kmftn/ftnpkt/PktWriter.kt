package net.jegor.kmftn.ftnpkt

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Writes a [Pkt] to a file.
 */
public object PktWriter {

    public fun write(path: Path, pkt: Pkt) {
        val data = pkt.toByteArray()
        SystemFileSystem.sink(path).buffered().use { it.write(data) }
    }
}
