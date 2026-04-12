package net.jegor.kmftn.binkpclient

import kotlinx.io.files.Path
import kotlinx.cinterop.*
import platform.posix.*

/**
 * iOS implementation for getting last modified time using POSIX stat
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun actualLastModifiedTime(path: Path): Long {
    return memScoped {
        val statBuf = alloc<stat>()
        val result = stat(path.toString(), statBuf.ptr)

        if (result == 0) {
            statBuf.st_mtimespec.tv_sec
        } else {
            0L
        }
    }
}
