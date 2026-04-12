package net.jegor.kmftn.binkpclient

import kotlinx.io.files.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

/**
 * JVM implementation for getting last modified time
 */
internal actual fun actualLastModifiedTime(path: Path): Long {
    return try {
        val javaPath = Paths.get(path.toString())
        val fileTime: FileTime = Files.getLastModifiedTime(javaPath)
        fileTime.toMillis() / 1000  // Convert milliseconds to seconds
    } catch (e: Exception) {
        0L  // Return 0 if file doesn't exist or other error
    }
}
