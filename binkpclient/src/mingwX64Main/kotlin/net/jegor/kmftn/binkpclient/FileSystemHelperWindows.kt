package net.jegor.kmftn.binkpclient

import kotlinx.io.files.Path
import kotlinx.cinterop.*
import platform.posix.*

/**
 * Windows (MinGW) implementation for getting last modified time using POSIX stat
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun actualLastModifiedTime(path: Path): Long {
    return memScoped {
        val statBuf = alloc<stat>()
        val result = stat(path.toString(), statBuf.ptr)

        if (result == 0) {
            // On Windows with MinGW, st_mtime is directly a time_t value
            statBuf.st_mtime
        } else {
            // stat failed (file doesn't exist or other error)
            0L
        }
    }
}
