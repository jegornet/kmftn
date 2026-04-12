package net.jegor.kmftn.binkpclient

import kotlinx.io.files.Path
import kotlinx.cinterop.*
import platform.posix.*

/**
 * Linux implementation for getting last modified time using POSIX stat
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun actualLastModifiedTime(path: Path): Long {
    return memScoped {
        val statBuf = alloc<stat>()
        val result = stat(path.toString(), statBuf.ptr)

        if (result == 0) {
            // On Linux, use st_mtim which is a timespec structure
            statBuf.st_mtim.tv_sec
        } else {
            // stat failed (file doesn't exist or other error)
            0L
        }
    }
}
